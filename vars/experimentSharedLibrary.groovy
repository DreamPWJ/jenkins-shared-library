#!groovy
import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*
import shared.library.devops.ChangeLog
import shared.library.devops.GitTagLog

/**
 * @author 潘维吉
 * @description 通用核心共享Pipeline脚本库
 * 实验性CI/CD流水线 用于高效研发测试流水线新功能等
 */
def call(String type = 'experiment', Map map) {
    echo "Pipeline共享库脚本类型: ${type}, Jenkins分布式节点名: ${params.SELECT_BUILD_NODE}"
    // 应用共享方法定义
    env.changeLog = new ChangeLog()
    env.gitTagLog = new GitTagLog()

    remote = [:]
    try {
        remote.host = "${REMOTE_IP}" // 部署应用程序服务器IP 动态字符参数 可配置在独立的job中
    } catch (exception) {
        // println exception.getMessage()
        remote.host = "${map.remote_ip}" // 部署应用程序服务器IP  不传字符参数 使用默认值
    }
    remote.user = "${map.remote_user_name}"
    env.remote_worker_ips = readJSON text: "${map.remote_worker_ips}"  // 分布式部署工作服务器地址 同时支持N个服务器
    // 代理机或跳板机外网ip用于透传部署到内网目标机器
    env.proxy_jump_ip = "${map.proxy_jump_ip}"
    // 自定义跳板机ssh和scp访问用户名 可精细控制权限 默认root
    env.proxy_jump_user_name = "${map.proxy_jump_user_name}"
    // 自定义跳板机ssh和scp访问端口 默认22
    env.proxy_jump_port = "${map.proxy_jump_port}"

    // 初始化参数
    getInitParams(map)

    if (type == "experiment") { // 针对标准项目
        pipeline {
            // 指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
            agent { label "${params.SELECT_BUILD_NODE} || any" }

            parameters {
                choice(name: 'DEPLOY_MODE', choices: [GlobalVars.release, GlobalVars.rollback, GlobalVars.start, GlobalVars.stop, GlobalVars.destroy, GlobalVars.restart],
                        description: '选择部署方式  1. ' + GlobalVars.release + '发布 2. ' + GlobalVars.rollback +
                                '回滚(基于K8s/Docker方式快速回滚上一个版本选择' + GlobalVars.rollback + ', 基于Git Tag方式回滚更早历史版本用默认的' + GlobalVars.release + ') ' +
                                ' 3. ' + GlobalVars.start + '启动服务 4. ' + GlobalVars.stop + '停止服务 5. ' + GlobalVars.destroy + '销毁删除服务 6. ' + GlobalVars.restart + '滚动重启服务')
                choice(name: 'MONOREPO_PROJECT_NAME', choices: "${MONOREPO_PROJECT_NAMES}",
                        description: "选择MonoRepo单体式统一仓库项目名称, ${GlobalVars.defaultValue}选项是MultiRepo多体式独立仓库或未配置, 大统一单体式仓库流水线可减少构建时间和磁盘空间")
                gitParameter(name: 'GIT_BRANCH', type: 'PT_BRANCH', defaultValue: "${BRANCH_NAME}", selectedValue: "DEFAULT",
                        useRepository: "${REPO_URL}", sortMode: 'ASCENDING', branchFilter: 'origin/(.*)', quickFilterEnabled: false,
                        description: "选择要构建的Git分支 默认: " + "${BRANCH_NAME} (可自定义配置具体任务的默认常用分支, 实现一键或全自动构建)")
                gitParameter(name: 'GIT_TAG', type: 'PT_TAG', defaultValue: GlobalVars.noGit, selectedValue: GlobalVars.noGit,
                        useRepository: "${REPO_URL}", sortMode: 'DESCENDING_SMART', tagFilter: '*', quickFilterEnabled: false,
                        description: "DEPLOY_MODE基于" + GlobalVars.release + "部署方式, 可选择指定Git Tag版本标签构建, 默认不选择是获取指定分支下的最新代码, 选择后按tag代码而非分支代码构建⚠️, 同时可作为一键回滚版本使用 🔙 ")
                choice(name: 'SELECT_BUILD_NODE', choices: ALL_ONLINE_NODES, description: "选择在线的分布式构建node节点  自动调度动态化构建在不同机器上 实现大规模流水线高效协作运行 💻 ")
                string(name: 'VERSION_NUM', defaultValue: "", description: "选填 自定义语义化版本号x.y.z 如1.0.0 (默认不填写  自动生成的版本号并且语义化自增 生产环境设置有效) 🖊 ")
                text(name: 'VERSION_DESCRIPTION', defaultValue: "${Constants.DEFAULT_VERSION_COPYWRITING}",
                        description: "请填写版本变更日志 (不填写用默认文案在钉钉、Git Tag、CHANGELOG.md则使用Git提交记录作为发布日志) 🖊 ")
                booleanParam(name: 'IS_CANARY_DEPLOY', defaultValue: false, description: "是否执行K8s/Docker集群灰度发布、金丝雀发布、A/B测试实现多版本共存机制 🐦")
                booleanParam(name: 'IS_CODE_QUALITY_ANALYSIS', defaultValue: false, description: "是否执行静态代码质量分析扫描检测并生成质量报告， 交付可读、易维护和安全的高质量代码 🔦 ")
                booleanParam(name: 'IS_WORKSPACE_CLEAN', defaultValue: false, description: "是否全部清空CI/CD工作空间 删除代码构建产物与缓存等 全新构建流水线工作环境 🛀 ")
                booleanParam(name: 'IS_HEALTH_CHECK', defaultValue: "${map.is_health_check}",
                        description: '是否执行服务启动健康探测  K8S使用默认的健康探测 🌡️')
                booleanParam(name: 'IS_GIT_TAG', defaultValue: "${map.is_git_tag}",
                        description: '是否在生产环境中自动给Git仓库设置Tag版本和生成CHANGELOG.md变更记录 📄')
                booleanParam(name: 'IS_DING_NOTICE', defaultValue: "${map.is_ding_notice}", description: "是否开启钉钉群通知 将构建成功失败等状态信息同步到群内所有人 📢 ")
                choice(name: 'NOTIFIER_PHONES', choices: "${contactPeoples}", description: '选择要通知的人 (钉钉群内@提醒发布结果) 📢 ')
                stashedFile(name: 'DEPLOY_PACKAGE', description: "请选择上传部署包文件、配置文件等 可不依赖源码情况下支持直接上传成品包部署方式和动态配置替换等 (如 *.jar、*.yaml、*.tar.gz 等格式) 🚀 ")
            }

            triggers {
                // 根据提交代码自动触发CI/CD流水线 在代码库设置WebHooks连接后生效: http://jenkins.domain.com/generic-webhook-trigger/invoke?token=jenkins
                GenericTrigger(
                        genericVariables: [
                                [key: 'project_git_http_url', value: '$.project.git_http_url'],
                                [key: 'ref', value: '$.ref'],
                                [key: 'git_message', value: '$.commits[0].message'],
                                [key: 'git_user_name', value: '$.user_name'],
                                [key: 'git_user_email', value: '$.user_email'],
                                [key: 'git_event_name', value: '$.event_name'],
                                [key: 'commits', value: '$.commits'],
                                [key: 'changed_files', value: '$.commits[*].[\'modified\',\'added\',\'removed\'][*]', expressionType: 'JSONPath'],
                        ],
                        token: env.JOB_NAME, // 唯一标识 env.JOB_NAME
                        causeString: ' Triggered on $ref',
                        printContributedVariables: true,
                        printPostContent: true,
                        silentResponse: false,
                        regexpFilterText: '_$ref_$git_message', //_$changed_files
                        // WebHooks触发后 正则匹配规则: 先匹配Job配置Git仓库确定项目, 根据jenkins job配置的分支匹配, 再匹配最新一次Git提交记录是否含有release发布关键字
                        // 针对monorepo单仓多包仓库 可根据release(项目模块名称)或者changed_files变量中变更文件所在的项目匹配自动触发构建具体的分支
                        regexpFilterExpression: '^' +
                                '_(refs/heads/' + "${BRANCH_NAME}" + ')' +
                                '_(release)' + "${((PROJECT_TYPE.toInteger() == GlobalVars.frontEnd && IS_MONO_REPO == true) || (PROJECT_TYPE.toInteger() == GlobalVars.backEnd && IS_MAVEN_SINGLE_MODULE == false)) ? '\\(' + "${PROJECT_NAME}" + '\\)' : ''}" + '.*' +
                                '$'
                )
                // 每分钟判断一次代码是否存在变化 有变化就执行
                // pollSCM('H/1 * * * *')
            }

            options {
                //失败重试次数
                retry(0)
                //超时时间 job会自动被终止
                timeout(time: 120, unit: 'MINUTES')
                //保持构建的最大个数
                buildDiscarder(logRotator(numToKeepStr: "${map.build_num_keep}", artifactNumToKeepStr: "${map.build_num_keep}"))
                //控制台输出增加时间戳
                timestamps()
                //不允许同一个job同时执行流水线,可被用来防止同时访问共享资源等
                disableConcurrentBuilds()
                //如果某个stage为unstable状态，则忽略后面的任务，直接退出
                skipStagesAfterUnstable()
                //安静的时期 设置管道的静默时间段（以秒为单位），以覆盖全局默认值
                quietPeriod(1)
                //删除隐式checkout scm语句
                skipDefaultCheckout()
                //日志颜色
                ansiColor('xterm')
                //当agent为Docker或Dockerfile时, 指定在同一个jenkins节点上,每个stage都分别运行在一个新容器中,而不是同一个容器
                //newContainerPerStage()
            }

            environment {
                // 系统环境变量
                JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8" // 在全局系统设置或构建环境中设置 为了确保正确解析编码和颜色
                NODE_OPTIONS = "--max-old-space-size=4096" // NODE内存调整 防止打包内存溢出
                // jenkins节点java路径 适配不同版本jdk情况 /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
                //JAVA_HOME = "/var/jenkins_home/tools/hudson.model.JDK/${JDK_VERSION}${JDK_VERSION == '11' ? '/jdk-11' : ''}"
                // 动态设置环境变量  配置相关自定义工具
                //PATH = "${JAVA_HOME}/bin:$PATH"

                CI_GIT_CREDENTIALS_ID = "${map.ci_git_credentials_id}" // CI仓库信任ID 账号和token组合
                GIT_CREDENTIALS_ID = "${map.git_credentials_id}" // Git信任ID
                DING_TALK_CREDENTIALS_ID = "${map.ding_talk_credentials_id}" // 钉钉授信ID 系统管理根目录里面配置 自动生成
                DEPLOY_FOLDER = "${map.deploy_folder}" // 服务器上部署所在的文件夹名称
                NPM_PACKAGE_FOLDER = "${map.npm_package_folder}" // Web项目NPM打包代码所在的文件夹名称
                WEB_STRIP_COMPONENTS = "${map.web_strip_components}" // Web项目解压到指定目录层级
                DOCKER_JAVA_OPTS = "${map.docker_java_opts}" // JVM内存设置
                DOCKER_MEMORY = "${map.docker_memory}" // 容器最大内存限制 不支持小数点形式设置
                DOCKER_LOG_OPTS = "${map.docker_log_opts}" // docker日志限制
                IS_PUSH_DOCKER_REPO = "${map.is_push_docker_repo}" // 是否上传镜像到docker容器仓库
                DOCKER_REPO_CREDENTIALS_ID = "${map.docker_repo_credentials_id}" // docker容器镜像仓库账号信任id
                DOCKER_REPO_REGISTRY = "${map.docker_repo_registry}" // docker镜像仓库注册地址
                DOCKER_REPO_NAMESPACE = "${map.docker_repo_namespace}" // docker仓库命名空间名称
                DOCKER_MULTISTAGE_BUILD_IMAGES = "${map.docker_multistage_build_images}" // Dockerfile多阶段构建 镜像名称
                PROJECT_TAG = "${map.project_tag}" // 项目标签或项目简称
                MACHINE_TAG = "1号机" // 部署机器标签
                IS_PROD = "${map.is_prod}" // 是否是生产环境
                IS_SAME_SERVER = "${map.is_same_server}" // 是否在同一台服务器分布式部署
                IS_BEFORE_DEPLOY_NOTICE = "${map.is_before_deploy_notice}" // 是否进行部署前通知
                IS_GRACE_SHUTDOWN = "${map.is_grace_shutdown}" // 是否进行优雅停机
                IS_NEED_SASS = "${map.is_need_sass}" // 是否需要css预处理器sass
                IS_AUTO_TRIGGER = false // 是否是自动触发构建
                IS_GEN_QR_CODE = false // 生成二维码 方便手机端扫描
                IS_ARCHIVE = false // 是否归档  多个job会占用磁盘空间
                IS_ONLY_NOTICE_CHANGE_LOG = "${map.is_only_notice_change_log}" // 是否只通知发布变更记录
            }

            stages {
                stage('初始化') {
                    steps {
                        script {
                            echo '初始化'
                            initInfo()
                            getShellParams(map)
                            getUserInfo()
                        }
                    }
                }

                stage('获取代码') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return true
                        }
                    }
                    failFast true         // true表示其中只要有一个分支构建执行失败，就直接推出不等待其他分支构建
                    parallel {  // 并发构建步骤
                        stage('CI/CD代码') {
                            steps {
                                retry(3) {
                                    pullCIRepo()
                                }
                            }
                        }
                        stage('项目代码') {
                            steps {
                                retry(3) {
                                    pullProjectCode()
                                }
                            }
                        }
                    }
                }

                stage('未来实验室') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return true
                        }
                    }
/*                    agent {
                        dockerfile {
                            filename 'Dockerfile.maven-jdk' // 在WORKSPACE工作区代码目录
                            label "panweiji/maven-jdk-${JDK_PUBLISHER}-${JDK_VERSION}:latest"
                            dir "${env.WORKSPACE}/ci"
                            additionalBuildArgs "--build-arg MVND_VERSION=1.0.3 --build-arg JDK_PUBLISHER=${JDK_PUBLISHER} --build-arg JDK_VERSION=${JDK_VERSION}"
                            args " -v /var/cache/maven/.m2:/root/.m2  "
                            reuseNode true  // 使用根节点 不设置会进入其它如@2代码工作目录
                        }
                    }*/
                    steps {
                        script {
                            futureLab(map)
                        }
                    }
                }
            }

            // post包含整个pipeline或者stage阶段完成情况
            post() {
                always {
                    script {
                        echo '总是运行，无论成功、失败还是其他状态'
                    }
                }
                success {
                    script {
                        echo '当前成功时运行'
                        // JenkinsCI.triggerUpstreamJob(this, "quality-pipeline")
                    }
                }
                failure {
                    script {
                        echo '当前失败时才运行'
                    }
                }
                unstable {
                    script {
                        echo '不稳定状态时运行'
                    }
                }
                aborted {
                    script {
                        echo '被终止时运行'
                    }
                }
                changed {
                    script {
                        echo '当前完成状态与上一次完成状态不同执行'
                    }
                }
                fixed {
                    script {
                        echo '上次完成状态为失败或不稳定,当前完成状态为成功时执行'
                    }
                }
                regression {
                    script {
                        echo '上次完成状态为成功,当前完成状态为失败、不稳定或中止时执行'
                    }
                }
            }
        }

    }
}

/**
 * 常量定义类型
 */
class Constants {
    // 默认版本描述文案
    static final String DEFAULT_VERSION_COPYWRITING = '1. 优化了一些细节体验\n2. 修复了一些已知问题'
}

/**
 *  获取初始化参数方法
 */
def getInitParams(map) {

    // JSON_PARAMS为单独项目的初始化参数  JSON_PARAMS为key值  value为json结构  请选择jenkins动态参数中的 "文本参数" 配置  具体参数定义如下
    jsonParams = readJSON text: "${JSON_PARAMS}"
    // println "${jsonParams}"
    env.REPO_URL = jsonParams.REPO_URL ? jsonParams.REPO_URL.trim() : "" // Git源码地址 需要包含.git后缀
    env.BRANCH_NAME = jsonParams.BRANCH_NAME ? jsonParams.BRANCH_NAME.trim() : GlobalVars.defaultBranch  // Git默认分支
    env.PROJECT_TYPE = jsonParams.PROJECT_TYPE ? jsonParams.PROJECT_TYPE.trim() : ""  // 项目类型 1 前端项目 2 后端项目
    // 计算机语言类型 1. Java  2. Go  3. Python  5. C++  6. JavaScript
    env.COMPUTER_LANGUAGE = jsonParams.COMPUTER_LANGUAGE ? jsonParams.COMPUTER_LANGUAGE.trim() : "1"
    // 项目名 代码位置或构建模块名等
    env.PROJECT_NAME = jsonParams.PROJECT_NAME ? jsonParams.PROJECT_NAME.trim() : ""
    // shell传入前端或后端组合参数 包括名称、类型、多端口、环境等
    env.SHELL_PARAMS = jsonParams.SHELL_PARAMS ? jsonParams.SHELL_PARAMS.trim() : ""
    // 分布式部署独立扩展服务器 基于通用配置的基础上 再扩展的服务器IP集合 逗号分割
    env.EXPAND_SERVER_IPS = jsonParams.EXPAND_SERVER_IPS ? jsonParams.EXPAND_SERVER_IPS.trim() : ""

    env.JDK_VERSION = jsonParams.JDK_VERSION ? jsonParams.JDK_VERSION.trim() : "${map.jdk}" // 自定义JDK版本
    env.JDK_PUBLISHER = jsonParams.JDK_PUBLISHER ? jsonParams.JDK_PUBLISHER.trim() : "${map.jdk_publisher}" // JDK版本发行商
    env.NODE_VERSION = jsonParams.NODE_VERSION ? jsonParams.NODE_VERSION.trim() : "${map.nodejs}" // 自定义Node版本
    env.TOMCAT_VERSION = jsonParams.TOMCAT_VERSION ? jsonParams.TOMCAT_VERSION.trim() : "7.0" // 自定义非内嵌的Tomcat老版本
    // npm包管理工具类型 如:  npm、yarn、pnpm
    env.NPM_PACKAGE_TYPE = jsonParams.NPM_PACKAGE_TYPE ? jsonParams.NPM_PACKAGE_TYPE.trim() : "pnpm"
    env.NPM_RUN_PARAMS = jsonParams.NPM_RUN_PARAMS ? jsonParams.NPM_RUN_PARAMS.trim() : "" // npm run [build]的前端项目参数
    // 如果Maven模块化存在二级模块目录 设置一级模块目录名称
    env.MAVEN_ONE_LEVEL = jsonParams.MAVEN_ONE_LEVEL ? jsonParams.MAVEN_ONE_LEVEL.trim() : "${map.maven_one_level}"

    env.IS_MONO_REPO = jsonParams.IS_MONO_REPO ? jsonParams.IS_MONO_REPO : false // 是否MonoRepo单体式仓库  单仓多包
    // 是否Maven单模块代码
    env.IS_MAVEN_SINGLE_MODULE = jsonParams.IS_MAVEN_SINGLE_MODULE ? jsonParams.IS_MAVEN_SINGLE_MODULE : false
    // 是否执行Maven单元测试
    env.IS_RUN_MAVEN_TEST = jsonParams.IS_RUN_MAVEN_TEST ? jsonParams.IS_RUN_MAVEN_TEST : false
    // 是否使用Docker容器环境方式构建打包 false使用宿主机环境
    env.IS_DOCKER_BUILD = jsonParams.IS_DOCKER_BUILD == "false" ? false : true
    // 是否开启Docker多架构CPU构建支持
    env.IS_DOCKER_BUILD_MULTI_PLATFORM = jsonParams.IS_DOCKER_BUILD_MULTI_PLATFORM ? jsonParams.IS_DOCKER_BUILD_MULTI_PLATFORM : false
    env.IS_BLUE_GREEN_DEPLOY = jsonParams.IS_BLUE_GREEN_DEPLOY ? jsonParams.IS_BLUE_GREEN_DEPLOY : false // 是否蓝绿部署
    env.IS_ROLL_DEPLOY = jsonParams.IS_ROLL_DEPLOY ? jsonParams.IS_ROLL_DEPLOY : false // 是否滚动部署
    // 是否灰度发布  金丝雀发布  A/B测试
    env.IS_CANARY_DEPLOY = jsonParams.IS_CANARY_DEPLOY ? jsonParams.IS_CANARY_DEPLOY : params.IS_CANARY_DEPLOY
    env.IS_K8S_DEPLOY = jsonParams.IS_K8S_DEPLOY ? jsonParams.IS_K8S_DEPLOY : false // 是否K8S集群部署
    env.IS_SERVERLESS_DEPLOY = jsonParams.IS_SERVERLESS_DEPLOY ? jsonParams.IS_SERVERLESS_DEPLOY : false
    // 是否Serverless发布
    env.IS_STATIC_RESOURCE = jsonParams.IS_STATIC_RESOURCE ? jsonParams.IS_STATIC_RESOURCE : false // 是否静态web资源
    env.IS_UPLOAD_OSS = jsonParams.IS_UPLOAD_OSS ? jsonParams.IS_UPLOAD_OSS : false // 是否构建产物上传到OSS
    // K8s集群业务应用是否使用Session 做亲和度关联
    env.IS_USE_SESSION = jsonParams.IS_USE_SESSION ? jsonParams.IS_USE_SESSION : false
    // 是否是NextJs服务端React框架
    env.IS_NEXT_JS = jsonParams.IS_NEXT_JS ? jsonParams.IS_NEXT_JS : false
    // 服务器部署时不同机器的代码配置是否不相同
    env.IS_DIFF_CONF_IN_DIFF_MACHINES = jsonParams.IS_DIFF_CONF_IN_DIFF_MACHINES ? jsonParams.IS_DIFF_CONF_IN_DIFF_MACHINES : false
    // 是否开启K8S自动水平弹性扩缩容
    env.IS_K8S_AUTO_SCALING = jsonParams.IS_K8S_AUTO_SCALING ? jsonParams.IS_K8S_AUTO_SCALING : false
    // 是否禁用K8S健康探测
    env.IS_DISABLE_K8S_HEALTH_CHECK = jsonParams.IS_DISABLE_K8S_HEALTH_CHECK ? jsonParams.IS_DISABLE_K8S_HEALTH_CHECK : false
    // 是否开启Spring Native原生镜像 显著提升性能同时降低资源使用
    env.IS_SPRING_NATIVE = jsonParams.IS_SPRING_NATIVE ? jsonParams.IS_SPRING_NATIVE : false
    // 是否进行代码质量分析的开关
    env.IS_CODE_QUALITY_ANALYSIS = jsonParams.IS_CODE_QUALITY_ANALYSIS ? jsonParams.IS_CODE_QUALITY_ANALYSIS : params.IS_CODE_QUALITY_ANALYSIS
    // 是否进集成测试
    env.IS_INTEGRATION_TESTING = jsonParams.IS_INTEGRATION_TESTING ? jsonParams.IS_INTEGRATION_TESTING : false

    // 设置monorepo单体仓库主包文件夹名
    env.MONO_REPO_MAIN_PACKAGE = jsonParams.MONO_REPO_MAIN_PACKAGE ? jsonParams.MONO_REPO_MAIN_PACKAGE.trim() : "projects"
    env.AUTO_TEST_PARAM = jsonParams.AUTO_TEST_PARAM ? jsonParams.AUTO_TEST_PARAM.trim() : ""  // 自动化集成测试参数
    // Java框架类型 1. Spring Boot  2. Spring MVC
    env.JAVA_FRAMEWORK_TYPE = jsonParams.JAVA_FRAMEWORK_TYPE ? jsonParams.JAVA_FRAMEWORK_TYPE.trim() : "1"
    // 自定义Docker挂载映射 docker run -v 参数(格式 宿主机挂载路径:容器内目标路径)  多个用逗号,分割
    env.DOCKER_VOLUME_MOUNT = jsonParams.DOCKER_VOLUME_MOUNT ? jsonParams.DOCKER_VOLUME_MOUNT.trim() : "${map.docker_volume_mount}".trim()
    // 自定义特殊化的Nginx配置文件在项目源码中的路径  用于替换CI仓库的config默认标准配置文件
    env.CUSTOM_NGINX_CONFIG = jsonParams.CUSTOM_NGINX_CONFIG ? jsonParams.CUSTOM_NGINX_CONFIG.trim() : ""
    // 不同部署节点动态批量替换多个环境配置文件 源文件目录 目标文件目录 逗号,分割  如 resources/config,resources
    env.SOURCE_TARGET_CONFIG_DIR = jsonParams.SOURCE_TARGET_CONFIG_DIR ? jsonParams.SOURCE_TARGET_CONFIG_DIR.trim() : ""
    // 不同项目通过文件目录区分放在相同的仓库中 设置Git代码项目文件夹名称 用于找到相关应用源码
    env.GIT_PROJECT_FOLDER_NAME = jsonParams.GIT_PROJECT_FOLDER_NAME ? jsonParams.GIT_PROJECT_FOLDER_NAME.trim() : ""
    // K8S集群 Pod初始化副本数量  高并发建议分布式2n+1节点容灾性
    env.K8S_POD_REPLICAS = jsonParams.K8S_POD_REPLICAS ? jsonParams.K8S_POD_REPLICAS.trim() : 2
    // 应用服务访问完整域名或代理服务器IP 带https或http前缀 用于反馈显示等
    env.APPLICATION_DOMAIN = jsonParams.APPLICATION_DOMAIN ? jsonParams.APPLICATION_DOMAIN.trim() : ""
    // NFS网络文件服务地址
    env.NFS_SERVER = jsonParams.NFS_SERVER ? jsonParams.NFS_SERVER.trim() : ""
    // 挂载宿主机路径与NFS服务器文件路径映射关系 NFS宿主机文件路径 NFS服务器文件路径 映射关系:冒号分割 多个逗号,分割
    env.NFS_MOUNT_PATHS = jsonParams.NFS_MOUNT_PATHS ? jsonParams.NFS_MOUNT_PATHS.trim() : ""
    // 自定义健康探测HTTP路径Path  默认根目录 /
    env.CUSTOM_HEALTH_CHECK_PATH = jsonParams.CUSTOM_HEALTH_CHECK_PATH ? jsonParams.CUSTOM_HEALTH_CHECK_PATH.trim() : "/"
    // 自定义部署Dockerfile名称 如 Dockerfile.xxx
    env.CUSTOM_DOCKERFILE_NAME = jsonParams.CUSTOM_DOCKERFILE_NAME ? jsonParams.CUSTOM_DOCKERFILE_NAME.trim() : "Dockerfile"
    // 自定义Python版本
    env.CUSTOM_PYTHON_VERSION = jsonParams.CUSTOM_PYTHON_VERSION ? jsonParams.CUSTOM_PYTHON_VERSION.trim() : "3.14.0"
    // 自定义Python启动文件名称 默认app.py文件
    env.CUSTOM_PYTHON_START_FILE = jsonParams.CUSTOM_PYTHON_START_FILE ? jsonParams.CUSTOM_PYTHON_START_FILE.trim() : "app.py"


    // 获取分布式构建节点 可动态构建在不同机器上
    def allNodes = JenkinsCI.getAllOnlineNodes(this, map)

    // 统一处理第一次CI/CD部署或更新pipeline代码导致jenkins构建参数不存在 初始化默认值
    if (IS_CANARY_DEPLOY == null) {  // 判断参数不存在 设置默认值
        IS_CANARY_DEPLOY = false
    }

    // 默认统一设置项目级别的分支 方便整体控制改变分支 将覆盖单独job内的设置
    if ("${map.default_git_branch}".trim() != "") {
        BRANCH_NAME = "${map.default_git_branch}"
    }
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd && "${map.default_frontend_git_branch}".trim() != "") {
        // 支持前端项目单独统一配置分支
        BRANCH_NAME = "${map.default_frontend_git_branch}"
    }

    // 启动时间长的服务是否进行部署前通知  具体job级别设置优先
    if (jsonParams.IS_BEFORE_DEPLOY_NOTICE ? jsonParams.IS_BEFORE_DEPLOY_NOTICE.toBoolean() : false) {
        IS_BEFORE_DEPLOY_NOTICE = true
    }

    // 统一前端monorepo仓库到一个job中, 减少构建依赖缓存大小和jenkins job维护成本
    MONOREPO_PROJECT_NAMES = ""
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd && "${IS_MONO_REPO}" == 'true') {
        MONOREPO_PROJECT_NAMES = PROJECT_NAME.trim().replace(",", "\n")
        def projectNameArray = "${PROJECT_NAME}".split(",") as ArrayList
        def projectNameIndex = projectNameArray.indexOf(params.MONOREPO_PROJECT_NAME)
        PROJECT_NAME = projectNameArray[projectNameIndex]
        SHELL_PARAMS = ("${SHELL_PARAMS}".split(",") as ArrayList)[projectNameIndex]
        NPM_RUN_PARAMS = ("${NPM_RUN_PARAMS}".split(",") as ArrayList)[projectNameIndex]
        if ("${MONO_REPO_MAIN_PACKAGE}".contains(",")) {
            MONO_REPO_MAIN_PACKAGE = ("${MONO_REPO_MAIN_PACKAGE}".split(",") as ArrayList)[projectNameIndex]
        }
        println("大统一前端monorepo仓库项目参数: ${PROJECT_NAME}:${NPM_RUN_PARAMS}:${SHELL_PARAMS}")
    } else {
        MONOREPO_PROJECT_NAMES = GlobalVars.defaultValue
    }

    // Maven Docker构建镜像名称
    mavenDockerName = "maven"
    if ("${IS_SPRING_NATIVE}" == "true") {
        mavenDockerName = "csanchez/maven"
    }

    // 未来可独立拆分成不同参数传入 更易于理解和维护
    SHELL_PARAMS_ARRAY = SHELL_PARAMS.split("\\s+")  // 正则表达式\s表示匹配任何空白字符，+表示匹配一次或多次
    env.SHELL_PROJECT_NAME = SHELL_PARAMS_ARRAY[0] // 项目名称
    env.SHELL_PROJECT_TYPE = SHELL_PARAMS_ARRAY[1] // 项目类型
    env.SHELL_HOST_PORT = SHELL_PARAMS_ARRAY[2] // 宿主机对外访问接口
    env.SHELL_EXPOSE_PORT = SHELL_PARAMS_ARRAY[3] // 容器内暴露端口
    env.SHELL_ENV_MODE = SHELL_PARAMS_ARRAY[4] // 环境模式 如 dev sit test prod等

    // 项目全名 防止项目名称重复
    env.FULL_PROJECT_NAME = "${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}"
    // Docker镜像名称
    env.dockerImageName = "${SHELL_PROJECT_NAME}/${SHELL_PROJECT_TYPE}-${SHELL_ENV_MODE}"
    // Docker容器名称
    env.dockerContainerName = "${FULL_PROJECT_NAME}-${SHELL_ENV_MODE}"

    // 获取通讯录
    env.contactPeoples = ""
    try {
        // 可使用configFileProvider动态配置
        def data = libraryResource('contacts.yaml')
        Map contacts = readYaml text: data
        contactPeoples = "${contacts.people}"
    } catch (e) {
        println("获取通讯录失败")
        println(e.getMessage())
    }

    // 健康探测url地址
    env.healthCheckUrl = ""
    env.healthCheckDomainUrl = ""
    // 使用域名或机器IP地址
    if ("${APPLICATION_DOMAIN}".trim() == "") {
        healthCheckUrl = "http://${remote.host}:${SHELL_HOST_PORT}"
    } else {
        healthCheckDomainUrl = "${APPLICATION_DOMAIN}"
    }

    // Git Tag版本变量定义
    env.tagVersion = ""
    // 扫描二维码地址
    env.qrCodeOssUrl = ""
    // Java构建包OSS地址Url
    env.javaOssUrl = ""
    // Java打包类型 jar、war
    env.javaPackageType = ""
    // 构建包大小
    env.buildPackageSize = ""
    // 构建打包后产物的位置
    env.buildPackageLocation = ""
    // 是否健康探测失败状态
    env.isHealthCheckFail = false
    // 计算应用启动时间
    env.healthCheckTimeDiff = "未知"
    // Qodana代码质量准备不同语言的镜像名称
    env.qodanaImagesName = ""

}

/**
 * 初始化信息
 */
def initInfo() {
    // 判断平台信息
    if (!isUnix()) {
        error("当前脚本针对Unix(如Linux或MacOS)系统 脚本执行失败 ❌")
    }
    //echo sh(returnStdout: true, script: 'env')
    //sh 'printenv'
    //println "${env.PATH}"
    //println currentBuild

    // 初始化docker环境变量
    Docker.initEnv(this)

    // 不同语言使用不同的从服务部署脚本
    dockerReleaseWorkerShellName = ""
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
        dockerReleaseWorkerShellName = "docker-release-worker.sh"
        qodanaImagesName = "qodana-jvm-community"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Go) {
        dockerReleaseWorkerShellName = "go/docker-release-worker-go.sh"
        qodanaImagesName = "qodana-go"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
        dockerReleaseWorkerShellName = "python/docker-release-worker-python.sh"
        qodanaImagesName = "qodana-python-community"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Cpp) {
        dockerReleaseWorkerShellName = "cpp/docker-release-worker-cpp.sh"
        qodanaImagesName = "qodana-clang"
    }
    // 前端项目
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
        qodanaImagesName = "qodana-js"
    }

    // 是否跳板机穿透方式部署
    isProxyJumpType = false
    // 跳板机ssh ProxyJump访问新增的文本 考虑多层跳板机穿透情况
    proxyJumpSSHText = "" // ssh跳板透传远程访问
    proxyJumpSCPText = "" // scp跳板透传远程复制传输
    if ("${proxy_jump_ip}".trim() != "") {
        isProxyJumpType = true
        // ssh -J root@外网跳板机IP:22 root@内网目标机器IP -p 22
        proxyJumpSSHText = " -J ${proxy_jump_user_name}@${proxy_jump_ip}:${proxy_jump_port} "
        proxyJumpSCPText = " -o 'ProxyJump ${proxy_jump_user_name}@${proxy_jump_ip}:${proxy_jump_port}' "
    }

    // 删除代码构建产物与缓存等 用于全新构建流水线工作环境
    try {
        if (params.IS_WORKSPACE_CLEAN == true) {
            println("删除代码构建产物与缓存等 用于全新构建流水线工作环境")
            def jobHome = env.WORKSPACE.split("@")[0] // 根据@符号分隔去前面的路径
            sh " rm -rf ${jobHome}*"
        }
    } catch (error) {
        println("清空工作空间失败: " + error)
    }

}

/**
 * 组装初始化shell参数
 */
def getShellParams(map) {
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
        SHELL_WEB_PARAMS_GETOPTS = " -a ${SHELL_PROJECT_NAME} -b ${SHELL_PROJECT_TYPE} -c ${SHELL_HOST_PORT} " +
                "-d ${SHELL_EXPOSE_PORT} -e ${SHELL_ENV_MODE}  -f ${DEPLOY_FOLDER} -g ${NPM_PACKAGE_FOLDER} -h ${WEB_STRIP_COMPONENTS} " +
                "-i ${IS_PUSH_DOCKER_REPO}  -k ${DOCKER_REPO_REGISTRY}/${DOCKER_REPO_NAMESPACE} -l ${CUSTOM_DOCKERFILE_NAME} "
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
        // 使用getopts的方式进行shell参数传递
        SHELL_PARAMS_GETOPTS = " -a ${SHELL_PROJECT_NAME} -b ${SHELL_PROJECT_TYPE} -c ${SHELL_HOST_PORT} " +
                "-d ${SHELL_EXPOSE_PORT} -e ${SHELL_ENV_MODE}  -f ${IS_PROD} -g ${DOCKER_JAVA_OPTS} -h ${DOCKER_MEMORY} " +
                "-i ${DOCKER_LOG_OPTS}  -k ${DEPLOY_FOLDER} -l ${JDK_VERSION} -m ${IS_PUSH_DOCKER_REPO} " +
                "-n ${DOCKER_REPO_REGISTRY}/${DOCKER_REPO_NAMESPACE} "

        // 区分JAVA框架类型参数
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
            def jdkPublisher = "${JDK_PUBLISHER}"
            if ("${IS_SPRING_NATIVE}" == "true") {
                // jdkPublisher = "container-registry.oracle.com/graalvm/native-image"  // GraalVM JDK with Native Image
                // GraalVM JDK without Native Image
                jdkPublisher = "container-registry.oracle.com/graalvm/jdk"
            }
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -q ${JAVA_FRAMEWORK_TYPE} -r ${TOMCAT_VERSION} -s ${jdkPublisher} -t ${IS_SPRING_NATIVE}"
        }

        // Python项目参数
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
            SHELL_PARAMS_GETOPTS = " -a ${SHELL_PROJECT_NAME} -b ${SHELL_PROJECT_TYPE} -c ${SHELL_HOST_PORT} " +
                    "-d ${SHELL_EXPOSE_PORT} -e ${SHELL_ENV_MODE}  -f ${IS_PROD} -g ${CUSTOM_PYTHON_VERSION} -h ${DOCKER_MEMORY} " +
                    "-i ${DOCKER_LOG_OPTS}  -k ${DEPLOY_FOLDER} -l ${CUSTOM_PYTHON_START_FILE} -m ${IS_PUSH_DOCKER_REPO} " +
                    "-n ${DOCKER_REPO_REGISTRY}/${DOCKER_REPO_NAMESPACE} "
        }

        // 是否存在容器挂载
        if ("${DOCKER_VOLUME_MOUNT}") {
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -o ${DOCKER_VOLUME_MOUNT} "
        }
        // 可选远程调试端口
        if ("${SHELL_PARAMS_ARRAY.length}" == '6') {
            SHELL_REMOTE_DEBUG_PORT = SHELL_PARAMS_ARRAY[5] // 远程调试端口
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -y ${SHELL_REMOTE_DEBUG_PORT}"
        }
        // 可选扩展端口
        SHELL_EXTEND_PORT = ""
        if ("${SHELL_PARAMS_ARRAY.length}" == '7') {
            SHELL_EXTEND_PORT = SHELL_PARAMS_ARRAY[6]
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -z ${SHELL_EXTEND_PORT}"
        }
        // println "${SHELL_PARAMS_GETOPTS}"
    }
}

/**
 * 获取用户信息
 */
def getUserInfo() {
    // 用户相关信息
    def triggerCauses = JenkinsCI.ciAutoTriggerInfo(this)
    if (IS_AUTO_TRIGGER == true) { // 自动触发构建
        println("自动触发构建: " + triggerCauses)
    } else {
        wrap([$class: 'BuildUser']) {
            try {
                BUILD_USER = env.BUILD_USER
                // BUILD_USER_EMAIL = env.BUILD_USER_EMAIL
                // 获取钉钉插件手机号 注意需要系统设置里in-process script approval允许权限
                def user = hudson.model.User.getById(env.BUILD_USER_ID, false).getProperty(io.jenkins.plugins.DingTalkUserProperty.class)
                BUILD_USER_MOBILE = user.mobile
                if (user.mobile == null || "${user.mobile}".trim() == "") {
                    BUILD_USER_MOBILE = env.BUILD_USER // 未填写钉钉插件手机号则使用用户名代替显示
                }
            } catch (error) {
                println "获取账号部分信息失败"
                println error.getMessage()
            }
        }
    }
}

/**
 * 获取CI代码库
 */
def pullCIRepo() {
    // 同步部署脚本和配置文件等
    sh ' mkdir -p ci && chmod -R 777 ci'
    dir("${env.WORKSPACE}/ci") {
        def reg = ~/^\*\// // 正则匹配去掉*/字符
        // 根据jenkins配置的scm分支 获取相应分支下脚本和配置 支持多分支构建
        scmBranchName = scm.branches[0].name - reg
        println "Jenkinsfile文件和CI代码库分支: ${scmBranchName}"
        // 拉取Git上的部署文件 无需人工上传
        git url: "${GlobalVars.CI_REPO_URL}", branch: "${scmBranchName}", changelog: false, credentialsId: "${CI_GIT_CREDENTIALS_ID}"
    }
}

/**
 * 获取项目代码
 */
def pullProjectCode() {

    // 未获取到参数 兼容处理 因为参数配置从代码拉取 必须先执行jenkins任务才能生效
    if (!params.GIT_TAG) {
        params.GIT_TAG = GlobalVars.noGit
    }

    // 获取应用打包代码
    if (params.GIT_TAG == GlobalVars.noGit) { // 基于分支最新代码构建
        // 自定义选择指定分支 不使用配置好的分支情况
        if ("${BRANCH_NAME}" != "${params.GIT_BRANCH}") {
            BRANCH_NAME = "${params.GIT_BRANCH}"  // Git分支
        }

        println "Git构建分支是: ${BRANCH_NAME} 📇"
        // 仓库地址是否包含.git后缀 没有添加
        if (!"${REPO_URL}".contains(".git")) {
            REPO_URL = "${REPO_URL}.git"
        }
        sh "git --version"  // 建议使用git 2.0以上的高级版本  否则可能有兼容性问题
        // sh "which git"
        // https仓库下载报错处理 The certificate issuer's certificate has expired.  Check your system date and time.
        sh "git config --global http.sslVerify false || true"
        // def git = git url: "${REPO_URL}", branch: "${BRANCH_NAME}", credentialsId: "${GIT_CREDENTIALS_ID}"
        // println "${git}"

        // 在node节点工具位置选项配置 which git的路径 才能拉取代码!!!
        // 对于大体积仓库或网络不好情况 自定义代码下载超时时间
        checkout([$class           : 'GitSCM',
                  branches         : [[name: "*/${BRANCH_NAME}"]],
                  extensions       : [[$class: 'CloneOption', timeout: 30]],
                  gitTool          : 'Default',
                  userRemoteConfigs: [[credentialsId: "${GIT_CREDENTIALS_ID}", url: "${REPO_URL}"]]
        ])
    } else {  // 基于Git标签代码构建
        println "Git构建标签是: ${params.GIT_TAG} 📇"
        checkout([$class                           : 'GitSCM',
                  branches                         : [[name: "${params.GIT_TAG}"]],
                  doGenerateSubmoduleConfigurations: false,
                  extensions                       : [[$class: 'CloneOption', timeout: 30]],
                  gitTool                          : 'Default',
                  submoduleCfg                     : [],
                  userRemoteConfigs                : [[credentialsId: "${GIT_CREDENTIALS_ID}", url: "${REPO_URL}"]]
        ])
    }

}

/**
 * 实验开发调试
 */
def futureLab(map) {

    println("构建机器名称: ${NODE_NAME}")
    println("仓库地址: ${REPO_URL}")

    println("docker参数：" + Docker.setDockerParameters(this))

/*
    def logs = Git.getGitLogByTime(this)
    println("Git日志: ${logs}")
*/

    // 当前job是否有代码变更记录并提醒
    JenkinsCI.getNoChangeLogAndTip(this)

    // input message: 'Deploy to production?', ok: 'Yes, deploy'

    // Tools.printColor(this, "Maven打包成功 ✅")

/*    def array = map.remote_worker_ips
    println("远程节点IP: ${array}")
    println("远程节点IP数量: ${array.size}")
    println("远程节点IP数量乘数: ${array.size * 3}")
*/


    // 生成跳转 URL
    def targetUrl = "${env.BUILD_URL}console"

/*
    timeout(time: 1, unit: 'MINUTES') {
        input message: '请上传部署包并确认',
                submitter: 'admin'
    }
*/

    try { // 是否存在声明
        // 原始文件名称是 定义变量名称+ _FILENAME后缀组合
        println("上传文件名: ${DEPLOY_PACKAGE_FILENAME}")
        unstash 'DEPLOY_PACKAGE' // 获取文件
        // sh 'cat DEPLOY_PACKAGE'
        // 部署文件恢复原始文件名称
        sh 'mv DEPLOY_PACKAGE $DEPLOY_PACKAGE_FILENAME'
    } catch (error) {
    }


// 构建开始后立即重定向
/*    def redirectUrl = "${env.BUILD_URL}"
    println(redirectUrl)
    System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval'; style-src 'unsafe-inline';")
    echo "<script>window.location.href='${redirectUrl}';</script>"*/

/*    def badge = addInfoBadge(icon: "", text: '流水线执行成功 ✅')
    sleep 3
    def badge2 = addInfoBadge(icon: "", text: '流水线执行失败 ❌')
    sleep 3
    removeBadges(id: badge.getId())
    removeBadges(id: badge2.getId())*/

    /*
     addBadge(id: "version-badge", text: "2.10.2", color: 'green', cssClass: 'badge-text--background')
     addBadge(id: "url-badge", icon: 'symbol-link plugin-ionicons-api', text: '访问地址', link: 'https://yuanbao.tencent.com/', target: '_blank')
       removeBadges(id: "launch-badge")

       // JenkinsCI.getCurrentBuildParent(this)

       /*
       def array = map.ding_talk_credentials_ids
       array.each { item ->
           println "keyword: ${item.keyword}"
           println "token: ${item.token}"
       }

       // 钉钉 HTTP 原生调用
        DingTalk.noticeMarkDown(this, map.ding_talk_credentials_ids, "面向未来重构CI/CD基建", "#### 面向未来重构CI/CD基建 功能 性能 易用性全面提升", "18863302302")
       */


// Groovy HTTP 原生调用
/*    HttpUtil.get(this, "https://saasadmin.pengbocloud.com")
    HttpUtil.post(this, "https://saasadmin.pengbocloud.com",  '{"name":"new_item"}')*/


/*
    def pythonVersion = "3.10"
    def installPackages = "" // 动态安装依赖包
    def dockerImageName = "panweiji/python"
    def dockerImageTag = pythonVersion + "" + (installPackages == "" ? "" : "-" + installPackages.replaceAll(" ", "-"))
    Docker.buildDockerImage(this, map, "${env.WORKSPACE}/ci/.ci/python/Dockerfile.python", dockerImageName, dockerImageTag, "--build-arg PYTHON_VERSION=${pythonVersion} --build-arg CUSTOM_INSTALL_PACKAGES=${installPackages}", true)
    docker.image("${dockerImageName}:${dockerImageTag}").inside("") {
        sh "python -V"
    }*/

/*  def dockerImageName = "panweiji/k8s-build"
    def dockerImageTag = "latest"
    Docker.buildDockerImage(this, map, "${env.WORKSPACE}/ci/Dockerfile.k8s-new", dockerImageName, dockerImageTag, "", true)
    docker.image("${dockerImageName}:${dockerImageTag}").inside("") {
        sh "python -V"
        sh "kubectl version --client"
        // sh "helm version"
    }*/

/*    def nodeVersion = "${"Nod24".replace('Node', '')}"
    def dockerImageName = "panweiji/node-build"
    def dockerImageTag = "${nodeVersion}"
    Docker.buildDockerImage(this, map, "${env.WORKSPACE}/ci/Dockerfile.node-build", dockerImageName, dockerImageTag,
            "--build-arg NODE_VERSION=${nodeVersion}", true)
    docker.image("${dockerImageName}:${dockerImageTag}").inside("") {
        sh "node -v"
        sh "npm -v"
        sh "yarn --version"
        sh "pnpm --version || true" // 与Node版本兼容
        sh "git --version || true" // Node依赖从Git仓库获取
        // sh "playwright --version || true"
    }*/

  def mvndVersion = "1.0.3"
    def jdkVersion = "25"
    def dockerImageName = "panweiji/maven-jdk"
    def dockerImageTag = "${mvndVersion}-${jdkVersion}"
    Docker.buildDockerImage(this, map, "${env.WORKSPACE}/ci/Dockerfile.maven-jdk", dockerImageName, dockerImageTag,
            "--build-arg MVND_VERSION=${mvndVersion} --build-arg JDK_VERSION=${jdkVersion}", false)

    docker.image("${dockerImageName}:${dockerImageTag}").inside("-v /var/cache/maven/.m2:/root/.m2") {
        println("执行容器服务")
        sh "mvnd --version"
        // sh "mvn --version"
        sh "java --version"

        //sh "mvnd clean install -T 4C -Dmvnd.threads=8 -pl pengbo-park/pengbo-park-app -am -Dmaven.compile.fork=true -Dmaven.test.skip=true"
        //sh "mvn clean install  -pl pengbo-park/pengbo-park-app -am -Dmaven.compile.fork=true -Dmaven.test.skip=true"
        //sh "mvnd  install"
        //sh "mvn  install"
    }


/*    def gradleVersion = "9" // Gradle版本 要动态配置
    def jdkVersion = "${JDK_VERSION}"
    def dockerImageName = "gradle"
    def dockerImageTag = "$gradleVersion-jdk$jdkVersion"
    docker.image("${dockerImageName}:${dockerImageTag}").inside(" -v $HOME/.gradle:/root/.gradle -v $HOME/.gradle:/home/gradle/.gradle ") {
        dir("${env.WORKSPACE}/${GIT_PROJECT_FOLDER_NAME}") { // 源码在特定目录下
            Gradle.build(this, "bootJar")  // 打包命令
            // Spring Boot构建jar包 在 build/libs 下面
            def buildLibPath = "build/libs"
            dir(buildLibPath) {
                sh "rm -f *-plain.jar && ls"
            }
        }
    }*/

/*
    def k8sPodReplicas = Integer.parseInt("3")
    println("等于 " + k8sPodReplicas * 3 - 1)*/
/*  println("服务启动失败回滚到上一个版本  保证服务高可用性")
    Docker.rollbackServer(this, map, "${dockerImageName}", "${dockerContainerName}")*/
/*  def maxVersion = Git.getGitTagMaxVersion(this)
    println("结果: ${maxVersion}")
    */

}


