#!groovy
import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*
import shared.library.devops.ChangeLog
import shared.library.devops.GitTagLog

/**
 * @author 潘维吉
 * @description 通用核心共享Pipeline脚本库  针对所有Web项目
 * 技术项目类型 1. Npm生态与静态Web项目 2. Flutter For Web 3. React Native For Web 4. Unity For Web  5. WebAssembly
 */
def call(String type = 'web', Map map) {
    echo "Pipeline共享库脚本类型: ${type}, Jenkins分布式节点名: ${map.jenkins_node} "
    // 应用共享方法定义
    changeLog = new ChangeLog()
    gitTagLog = new GitTagLog()

    remote = [:]
    try {
        remote.host = "${REMOTE_IP}" // 部署应用程序服务器IP 动态字符参数 可配置在独立的job中
    } catch (exception) {
        // println exception.getMessage()
        remote.host = "${map.remote_ip}" // 部署应用程序服务器IP  不传字符参数 使用默认值
    }
    remote.user = "${map.remote_user_name}"
    remote_worker_ips = readJSON text: "${map.remote_worker_ips}"  // 分布式部署工作服务器地址 同时支持N个服务器
    // 代理机或跳板机外网ip用于透传部署到内网目标机器
    proxy_jump_ip = "${map.proxy_jump_ip}"
    // 自定义跳板机ssh和scp访问用户名 可精细控制权限 默认root
    proxy_jump_user_name = "${map.proxy_jump_user_name}"
    // 自定义跳板机ssh和scp访问端口 默认22
    proxy_jump_port = "${map.proxy_jump_port}"

    // 初始化参数
    getInitParams(map)

    if (type == "web") { // 针对标准项目
        pipeline {
            // 指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
            agent { label "${PROJECT_TYPE.toInteger() == GlobalVars.frontEnd ? "${map.jenkins_node_frontend}" : "${map.jenkins_node}"}" }

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
                string(name: 'VERSION_NUM', defaultValue: "", description: '选填 自定义语义化版本号x.y.z 如1.0.0 (默认不填写  自动生成的版本号并且语义化自增 生产环境设置有效) 🖊 ')
                booleanParam(name: 'IS_CANARY_DEPLOY', defaultValue: false, description: "是否执行K8s/Docker集群灰度发布、金丝雀发布、A/B测试实现多版本共存机制 🐦")
                booleanParam(name: 'IS_HEALTH_CHECK', defaultValue: "${map.is_health_check}",
                        description: '是否执行服务启动健康探测  K8S使用默认的健康探测')
                booleanParam(name: 'IS_GIT_TAG', defaultValue: "${map.is_git_tag}",
                        description: '是否在生产环境中自动给Git仓库设置Tag版本和生成CHANGELOG.md变更记录')
                booleanParam(name: 'IS_DING_NOTICE', defaultValue: "${map.is_ding_notice}", description: "是否开启钉钉群通知 📢 ")
                choice(name: 'NOTIFIER_PHONES', choices: "${contactPeoples}", description: '选择要通知的人 (钉钉群内@提醒发布结果) 📢 ')
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
                                [key: 'changed_files', value: '$.commits[*].[\'modified\',\'added\',\'removed\'][*]'],
                        ],
                        token: "jenkins-web", // 唯一标识 env.JOB_NAME
                        causeString: ' Triggered on $ref',
                        printContributedVariables: true,
                        printPostContent: true,
                        silentResponse: false,
                        regexpFilterText: '$_$ref_$git_message',
                        // WebHooks触发后 正则匹配规则: 先匹配Job配置Git仓库确定项目, 根据jenkins job配置的分支匹配, 再匹配最新一次Git提交记录是否含有release发布关键字
                        // 针对monorepo单仓多包仓库 可根据changed_files变量中变更文件所在的项目匹配自动触发构建具体的分支
                        regexpFilterExpression: '^' +
                                '_(refs/heads/' + "${BRANCH_NAME}" + ')' +
                                '_(release).*$'
                )
                // 每分钟判断一次代码是否存在变化 有变化就执行
                // pollSCM('H/1 * * * *')
            }

            environment {
                // 系统环境变量
                NODE_OPTIONS = "--max-old-space-size=4096" // NODE内存调整 防止打包内存溢出
                // 动态设置环境变量  配置相关自定义工具
                //PATH = "${JAVA_HOME}/bin:$PATH"
                SYSTEM_HOME = "$HOME" // 系统主目录

                CI_GIT_CREDENTIALS_ID = "${map.ci_git_credentials_id}" // CI仓库信任ID 账号和token组合
                GIT_CREDENTIALS_ID = "${map.git_credentials_id}" // Git信任ID
                DING_TALK_CREDENTIALS_ID = "${map.ding_talk_credentials_id}" // 钉钉授信ID 系统管理根目录里面配置 自动生成
                DEPLOY_FOLDER = "${map.deploy_folder}" // 服务器上部署所在的文件夹名称
                NPM_PACKAGE_FOLDER = "${map.npm_package_folder}" // Web项目NPM打包代码所在的文件夹名称
                WEB_STRIP_COMPONENTS = "${map.web_strip_components}" // Web项目解压到指定目录层级
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
                IS_NEED_SASS = "${map.is_need_sass}" // 是否需要css预处理器sass
                IS_AUTO_TRIGGER = false // 是否是自动触发构建
                IS_GEN_QR_CODE = false // 生成二维码 方便手机端扫描
                IS_ARCHIVE = false // 是否归档
                IS_INTEGRATION_TESTING = false // 是否进集成测试
                IS_ONLY_NOTICE_CHANGE_LOG = "${map.is_only_notice_change_log}" // 是否只通知发布变更记录
            }

            options {
                //失败重试次数
                retry(0)
                //超时时间 job会自动被终止
                timeout(time: 60, unit: 'MINUTES')
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
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                    }
                    /*   tools {
                               git "Default"
                         } */
                    steps {
                        script {
                            pullProjectCode()
                            pullCIRepo()
                            /*  parallel( // 步骤内并发执行
                                     'CI/CD代码': {
                                         pullCIRepo()
                                     },
                                     '项目代码': {
                                         pullProjectCode()
                                     })*/
                        }
                    }
                }

                /*   stage('扫描代码') {
                       //failFast true  // 其他阶段失败 中止parallel块同级正在进行的并行阶段
                       parallel { */// 阶段并发执行
                stage('代码质量') {
                    when {
                        beforeAgent true
                        // 生产环境不进行代码分析 缩减构建时间
                        not {
                            anyOf {
                                branch 'master'
                                branch 'prod'
                            }
                        }
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            // 是否进行代码质量分析  && fileExists("sonar-project.properties") == true 代码根目录配置sonar-project.properties文件才进行代码质量分析
                            // return ("${IS_CODE_QUALITY_ANALYSIS}" == 'true' )
                            return false
                        }
                    }
                    agent {
                        label "linux"
                        /*   docker {
                               // sonarqube环境  构建完成自动删除容器
                               image "sonarqube:community"
                               reuseNode true // 使用根节点
                           }*/
                    }
                    steps {
                        // 只显示当前阶段stage失败  而整个流水线构建显示成功
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            script {
                                codeQualityAnalysis()
                            }
                        }
                    }
                }
/*         stage('JavaScript构建 In Docker') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == true && "${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) }
                    }
                    agent {
                        // label "linux"
                        docker {
                            // Node环境  构建完成自动删除容器
                            //image "node:${NODE_VERSION.replace('Node', '')}"
                            image "panweiji/node:${NODE_VERSION.replace('Node', '')}" // 为了更通用应使用通用镜像  自定义镜像针对定制化需求
                            // 使用自定义Dockerfile的node环境 加速monorepo依赖构建内置lerna等相关依赖
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            echo "Docker环境内构建Node方式"
                            nodeBuildProject()
                        }
                    }
                }*/
                stage('Flutter For Web') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return ("${WEB_PROJECT_TYPE}".toInteger() == GlobalVars.flutterWeb) }
                    }
                    agent {
                        docker {
                            // flutter sdk环境  构建完成自动删除容器
                            image "ghcr.io/cirruslabs/flutter:stable"
                            args " -v ${PWD}:/build "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            flutterBuildPackage(map)
                        }
                    }
                }
                stage('React Native For Web') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return ("${WEB_PROJECT_TYPE}".toInteger() == GlobalVars.reactNativeWeb) }
                    }
                    steps {
                        script {
                            reactNativeBuildPackage(map)
                        }
                    }
                }
                stage('Unity For WebGL') {
                    agent {
                        // label "linux"
                        docker {
                            // Unity环境  构建完成自动删除容器
                            // 容器仓库: https://hub.docker.com/r/unityci/editor/tags?page=1&ordering=last_updated
                            image "unityci/editor:ubuntu-${unityVersion}-webgl-0.13.0"
                            // Unity授权许可协议激活核心配置映射
                            args " -v ${env.WORKSPACE}/ci/_jenkins/unity/${unityActivationFile}:/root/.local/share/unity3d/Unity/Unity_lic.ulf "
                            reuseNode true // 使用根节点
                        }
                    }
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return ("${WEB_PROJECT_TYPE}".toInteger() == GlobalVars.unityWeb) }
                    }
                    steps {
                        script {
                            echo "Unity打包WebGL"
                            unityBuildPackage(map)
                        }
                    }
                }
                stage('Web构建打包') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                    }
                    agent {
                        docker {
                            // Node环境  构建完成自动删除容器
                            image "panweiji/node:${NODE_VERSION.replace('Node', '')}" // 为了更通用应使用通用镜像  自定义镜像针对定制化需求
                            reuseNode true // 使用根节点
                        }
                    }
                    /*                tools {
                                        // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中
                                        // nodejs "${NODE_VERSION}"
                                    }*/
                    steps {
                        script {
                            echo "Docker环境内构建Node方式"
                            nodeBuildProject()
                        }
                    }
                }

                stage('制作镜像') {
                    when {
                        beforeAgent true
                        expression { return ("${IS_PUSH_DOCKER_REPO}" == 'true') }
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                    }
                    //agent { label "slave-jdk11-prod" }
                    steps {
                        script {
                            buildImage()
                        }
                    }
                }

                stage('上传代码包') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_K8S_DEPLOY == false)  // k8s集群部署 镜像方式无需上传到服务器
                        }
                    }
                    steps {
                        script {
                            uploadRemote(Utils.getShEchoResult(this, "pwd"), map)
                        }
                    }
                }

                stage('单机部署') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_BLUE_GREEN_DEPLOY == false && IS_K8S_DEPLOY == false)  // 非蓝绿和k8s集群部署 都有单独步骤
                        }
                    }
                    steps {
                        script {
                            runProject(map)
                        }
                    }
                }

                stage('健康探测') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (params.IS_HEALTH_CHECK == true && IS_BLUE_GREEN_DEPLOY == false)
                        }
                    }
                    steps {
                        script {
                            healthCheck(map)
                        }
                    }
                }

                stage('滚动部署') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_ROLL_DEPLOY == true) // 是否进行滚动部署
                        }
                    }
                    steps {
                        script {
                            // 滚动部署实现多台服务按顺序更新 分布式零停机
                            scrollToDeploy(map)
                        }
                    }
                }

                stage('Kubernetes云原生') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_K8S_DEPLOY == true)  // 是否进行云原生K8S集群部署
                        }
                    }
                    agent {
                        docker {
                            //  构建完成自动删除容器
                            image "panweiji/k8s:latest" // 为了更通用应使用通用镜像  自定义镜像针对定制化需求
                            // args " "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            // 云原生K8s部署大规模集群
                            k8sDeploy(map)
                        }
                    }
                }

                stage('消息通知') {
                    when {
                        expression { return true }
                    }
                    steps {
                        script {
                            if ("${params.IS_DING_NOTICE}" == 'true' && params.IS_HEALTH_CHECK == false) {
                                dingNotice(map, 1, "**成功 ✅**") // ✅
                            }
                        }
                    }
                }

                stage('发布日志') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                    }
                    steps {
                        script {
                            // 自动打tag和生成CHANGELOG.md文件
                            gitTagLog()
                            // 钉钉通知变更记录
                            dingNotice(map, 3)
                        }
                    }
                }

                stage('成品归档') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return ("${IS_ARCHIVE}" == 'true') // 是否归档
                        }
                    }
                    steps {
                        script {
                            // 归档
                            archive()
                        }
                    }
                }

                stage('回滚版本') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.rollback
                    }
                    steps {
                        script {
                            rollbackVersion(map)
                        }
                    }
                }
            }

            // post包含整个pipeline或者stage阶段完成情况
            post() {
                always {
                    script {
                        echo '总是运行，无论成功、失败还是其他状态'
                        alwaysPost()
                    }
                }
                success {
                    script {
                        echo '当前成功时运行'
                        deletePackagedOutput()
                    }
                }
                failure {
                    script {
                        echo '当前失败时才运行'
                        dingNotice(map, 0, "CI/CD流水线失败 ❌")
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

    } else if (type == "web-2") {  // 同类型流水线不同阶段判断执行  但差异性较大的Pipeline建议区分groovy文件维护

    }
}


/**
 *  获取初始化参数方法
 */
def getInitParams(map) {
    // JSON_PARAMS为单独项目的初始化参数  JSON_PARAMS为key值  value为json结构  请选择jenkins动态参数中的 "文本参数" 配置  具体参数定义如下
    jsonParams = readJSON text: "${JSON_PARAMS}"
    // println "${jsonParams}"
    REPO_URL = jsonParams.REPO_URL ? jsonParams.REPO_URL.trim() : "" // Git源码地址
    BRANCH_NAME = jsonParams.BRANCH_NAME ? jsonParams.BRANCH_NAME.trim() : GlobalVars.defaultBranch  // Git默认分支
    PROJECT_TYPE = jsonParams.PROJECT_TYPE ? jsonParams.PROJECT_TYPE.trim() : "1"  // 项目类型 1 前端项目 2 后端项目
    // WEB项目类型 1. Npm生态与静态Web项目 2. Flutter For Web 3. React Native For Web 4. Unity For Web  5. WebAssembly
    WEB_PROJECT_TYPE = jsonParams.WEB_PROJECT_TYPE ? jsonParams.WEB_PROJECT_TYPE.trim() : "1"
    // 计算机语言类型 1. Java  2. Go  3. Python  5. C++  6. JavaScript
    COMPUTER_LANGUAGE = jsonParams.COMPUTER_LANGUAGE ? jsonParams.COMPUTER_LANGUAGE.trim() : "1"
    // 项目名 代码位置或构建模块名等
    PROJECT_NAME = jsonParams.PROJECT_NAME ? jsonParams.PROJECT_NAME.trim() : ""
    // shell传入前端或后端组合参数 包括名称、类型、多端口、环境等
    SHELL_PARAMS = jsonParams.SHELL_PARAMS ? jsonParams.SHELL_PARAMS.trim() : ""

    // npm包管理工具类型 如:  npm、yarn、pnpm
    NODE_VERSION = jsonParams.NODE_VERSION ? jsonParams.NODE_VERSION.trim() : "${map.nodejs}" // nodejs版本
    NPM_PACKAGE_TYPE = jsonParams.NPM_PACKAGE_TYPE ? jsonParams.NPM_PACKAGE_TYPE.trim() : "pnpm"
    NPM_RUN_PARAMS = jsonParams.NPM_RUN_PARAMS ? jsonParams.NPM_RUN_PARAMS.trim() : "" // npm run [build]的前端项目参数
    // 如果Maven模块化存在二级模块目录 设置一级模块目录名称
    MAVEN_ONE_LEVEL = jsonParams.MAVEN_ONE_LEVEL ? jsonParams.MAVEN_ONE_LEVEL.trim() : "${map.maven_one_level}"

    // 是否使用Docker容器环境方式构建打包 false使用宿主机环境
    IS_DOCKER_BUILD = jsonParams.IS_DOCKER_BUILD == "false" ? false : true
    IS_BLUE_GREEN_DEPLOY = jsonParams.IS_BLUE_GREEN_DEPLOY ? jsonParams.IS_BLUE_GREEN_DEPLOY : false // 是否蓝绿部署
    IS_ROLL_DEPLOY = jsonParams.IS_ROLL_DEPLOY ? jsonParams.IS_ROLL_DEPLOY : false // 是否滚动部署
    // 是否灰度发布  金丝雀发布  A/B测试
    IS_CANARY_DEPLOY = jsonParams.IS_CANARY_DEPLOY ? jsonParams.IS_CANARY_DEPLOY : false
    IS_K8S_DEPLOY = jsonParams.IS_K8S_DEPLOY ? jsonParams.IS_K8S_DEPLOY : false // 是否K8S集群部署
    IS_SERVERLESS_DEPLOY = jsonParams.IS_SERVERLESS_DEPLOY ? jsonParams.IS_SERVERLESS_DEPLOY : false // 是否Serverless发布
    IS_STATIC_RESOURCE = jsonParams.IS_STATIC_RESOURCE ? jsonParams.IS_STATIC_RESOURCE : false // 是否静态web资源
    IS_MONO_REPO = jsonParams.IS_MONO_REPO ? jsonParams.IS_MONO_REPO : false // 是否MonoRepo单体式仓库  单仓多包
    // K8s集群业务应用是否使用Session 做亲和度关联
    IS_USE_SESSION = jsonParams.IS_USE_SESSION ? jsonParams.IS_USE_SESSION : false
    // 服务器部署时不同机器的代码配置是否不相同
    IS_DIFF_CONF_IN_DIFF_MACHINES = jsonParams.IS_DIFF_CONF_IN_DIFF_MACHINES ? jsonParams.IS_DIFF_CONF_IN_DIFF_MACHINES : false
    // 是否开启K8S自动水平弹性扩缩容
    IS_K8S_AUTO_SCALING = jsonParams.IS_K8S_AUTO_SCALING ? jsonParams.IS_K8S_AUTO_SCALING : false

    // 设置monorepo单体仓库主包文件夹名
    MONO_REPO_MAIN_PACKAGE = jsonParams.MONO_REPO_MAIN_PACKAGE ? jsonParams.MONO_REPO_MAIN_PACKAGE.trim() : "projects"
    AUTO_TEST_PARAM = jsonParams.AUTO_TEST_PARAM ? jsonParams.AUTO_TEST_PARAM.trim() : ""  // 自动化集成测试参数
    // 自定义Docker挂载映射 docker run -v 参数(格式 宿主机挂载路径:容器内目标路径)  多个用逗号,分割
    DOCKER_VOLUME_MOUNT = jsonParams.DOCKER_VOLUME_MOUNT ? jsonParams.DOCKER_VOLUME_MOUNT.trim() : "${map.docker_volume_mount}".trim()
    // 自定义特殊化的Nginx配置文件在项目源码中的路径  用于替换CI仓库的config默认标准配置文件
    CUSTOM_NGINX_CONFIG = jsonParams.CUSTOM_NGINX_CONFIG ? jsonParams.CUSTOM_NGINX_CONFIG.trim() : ""
    // 不同项目通过文件目录区分放在相同的仓库中 设置Git代码项目文件夹名称 用于找到相关应用源码
    GIT_PROJECT_FOLDER_NAME = jsonParams.GIT_PROJECT_FOLDER_NAME ? jsonParams.GIT_PROJECT_FOLDER_NAME.trim() : ""
    // 不同部署节点动态批量替换多个环境配置文件 源文件目录 目标文件目录 逗号,分割  如 resources/config,resources
    SOURCE_TARGET_CONFIG_DIR = jsonParams.SOURCE_TARGET_CONFIG_DIR ? jsonParams.SOURCE_TARGET_CONFIG_DIR.trim() : ""
    // K8S集群 Pod初始化副本数量  高并发建议分布式2n+1节点容灾性
    K8S_POD_REPLICAS = jsonParams.K8S_POD_REPLICAS ? jsonParams.K8S_POD_REPLICAS.trim() : 2
    // 应用服务访问完整域名或代理服务器IP 带https或http前缀 用于反馈显示等
    APPLICATION_DOMAIN = jsonParams.APPLICATION_DOMAIN ? jsonParams.APPLICATION_DOMAIN.trim() : ""
    // NFS网络文件服务地址
    NFS_SERVER = jsonParams.NFS_SERVER ? jsonParams.NFS_SERVER.trim() : ""
    // 挂载宿主机路径与NFS服务器文件路径映射关系 NFS宿主机文件路径 NFS服务器文件路径 映射关系:冒号分割 多个逗号,分割
    NFS_MOUNT_PATHS = jsonParams.NFS_MOUNT_PATHS ? jsonParams.NFS_MOUNT_PATHS.trim() : ""
    // 自定义健康探测HTTP路径Path  默认根目录 /
    CUSTOM_HEALTH_CHECK_PATH = jsonParams.CUSTOM_HEALTH_CHECK_PATH ? jsonParams.CUSTOM_HEALTH_CHECK_PATH.trim() : "/"
    // 自定义部署Dockerfile名称 如 Dockerfile.xxx
    CUSTOM_DOCKERFILE_NAME = jsonParams.CUSTOM_DOCKERFILE_NAME ? jsonParams.CUSTOM_DOCKERFILE_NAME.trim() : "Dockerfile"

    // 默认统一设置项目级别的分支 方便整体控制改变分支 将覆盖单独job内的设置
    if ("${map.default_git_branch}".trim() != "") {
        BRANCH_NAME = "${map.default_git_branch}"
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

    SHELL_PARAMS_ARRAY = SHELL_PARAMS.split("\\s+")  // 正则表达式\s表示匹配任何空白字符，+表示匹配一次或多次
    SHELL_PROJECT_NAME = SHELL_PARAMS_ARRAY[0] // 项目名称
    SHELL_PROJECT_TYPE = SHELL_PARAMS_ARRAY[1] // 项目类型
    SHELL_HOST_PORT = SHELL_PARAMS_ARRAY[2] // 宿主机对外访问接口
    SHELL_EXPOSE_PORT = SHELL_PARAMS_ARRAY[3] // 容器内暴露端口
    SHELL_ENV_MODE = SHELL_PARAMS_ARRAY[4] // 环境模式 如 dev sit test prod等

    // 项目全名 防止项目名称重复
    FULL_PROJECT_NAME = "${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}"
    // Docker镜像名称
    dockerImageName = "${SHELL_PROJECT_NAME}/${SHELL_PROJECT_TYPE}-${SHELL_ENV_MODE}"
    // Docker容器名称
    dockerContainerName = "${FULL_PROJECT_NAME}-${SHELL_ENV_MODE}"

    // 目标系统类型 1. Npm生态与静态web项目 2. Flutter For Web 3. ReactNative For Web 4. Unity For Web
    switch ("${WEB_PROJECT_TYPE}".toInteger()) {
        case GlobalVars.npmWeb:
            SYSTEM_TYPE_NAME = "Web"
            break
        case GlobalVars.flutterWeb:
            SYSTEM_TYPE_NAME = "Flutter"
            break
        case GlobalVars.reactNativeWeb:
            SYSTEM_TYPE_NAME = "React Native"
            break
        case GlobalVars.unityWeb:
            SYSTEM_TYPE_NAME = "Unity"
            unityVersion = "2020.3.13f1"  // unity编辑器版本
            unityActivationFile = "Unity_v2020.x.ulf" // unity激活许可文件名称
            break
        default:
            SYSTEM_TYPE_NAME = "未知"
    }
    println("目标系统类型: ${SYSTEM_TYPE_NAME}")

    // 获取通讯录
    contactPeoples = ""
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
    healthCheckUrl = ""
    healthCheckDomainUrl = ""
    // 使用域名或机器IP地址
    if ("${APPLICATION_DOMAIN}".trim() == "") {
        healthCheckUrl = "http://${remote.host}:${SHELL_HOST_PORT}"
    } else {
        healthCheckDomainUrl = "${APPLICATION_DOMAIN}"
    }

    // tag版本变量定义
    tagVersion = ""
    // 扫描二维码地址
    qrCodeOssUrl = ""
    // 是否健康探测失败状态
    isHealthCheckFail = false
    // 计算应用启动时间
    healthCheckTimeDiff = "未知"

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
}

/**
 * 组装初始化shell参数
 */
def getShellParams(map) {
    SHELL_WEB_PARAMS_GETOPTS = " -a ${SHELL_PROJECT_NAME} -b ${SHELL_PROJECT_TYPE} -c ${SHELL_HOST_PORT} " +
            "-d ${SHELL_EXPOSE_PORT} -e ${SHELL_ENV_MODE}  -f ${DEPLOY_FOLDER} -g ${NPM_PACKAGE_FOLDER} -h ${WEB_STRIP_COMPONENTS} " +
            "-i ${IS_PUSH_DOCKER_REPO}  -k ${DOCKER_REPO_REGISTRY}/${DOCKER_REPO_NAMESPACE} -l ${CUSTOM_DOCKERFILE_NAME} "
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
        // def git = git url: "${REPO_URL}", branch: "${BRANCH_NAME}", credentialsId: "${GIT_CREDENTIALS_ID}"
        // println "${git}"
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
    // 是否存在CI代码
    dir("${env.WORKSPACE}/ci") {
        existCiCode()
    }
}

/**
 * 代码质量分析
 */
def codeQualityAnalysis() {
    pullProjectCode()
    SonarQube.scan(this, "${FULL_PROJECT_NAME}")
    // SonarQube.getStatus(this, "${PROJECT_NAME}")
    // 可打通项目管理平台自动提交bug指派任务
}

/**
 * Flutter For Web编译打包
 */
def flutterBuildPackage(map) {
    // 初始化环境变量
    Flutter.initEnv(this)

    // 构建应用 前置条件
    Flutter.buildPrecondition(this)

    // 构建Flutter For Web   构建产物静态文件在 build/web/
    Flutter.buildWeb(this)

    // 删除之前构建文件
    sh "rm -rf ${NPM_PACKAGE_FOLDER} && rm -f ${NPM_PACKAGE_FOLDER}.tar.gz"

    // 打包资源重命名和位置移动 复用静态文件打包方法
    sh "cd build && rm -rf ${NPM_PACKAGE_FOLDER} && mkdir -p ${NPM_PACKAGE_FOLDER} " +
            " && mv web ${NPM_PACKAGE_FOLDER}/  && mv ${NPM_PACKAGE_FOLDER} ${env.WORKSPACE}/${NPM_PACKAGE_FOLDER} "
}

/**
 * React Native For Web编译打包
 */
def reactNativeBuildPackage(map) {

}

/**
 * Unity For WebGL编译打包
 */
def unityBuildPackage(map) {
    jenkinsConfigDir = "${env.WORKSPACE}/ci/_jenkins"
    // fastlane配置文件CI库位置前缀
    fastlaneConfigDir = "${jenkinsConfigDir}/fastlane"
    // 同步打包执行的构建文件
    Unity.syncBuildFile(this)

    unityWebGLPackagesOutputDir = "webgl"
    // 删除Unity构建产物
    sh "rm -rf ./${unityWebGLPackagesOutputDir} "
    // Unity构建打包
    Unity.build(this, "WebGL")

    // 删除之前构建文件
    sh "rm -rf ${NPM_PACKAGE_FOLDER} && rm -f ${NPM_PACKAGE_FOLDER}.tar.gz"

    // 打包资源重命名和位置移动 复用静态文件打包方法
    sh "rm -rf ${NPM_PACKAGE_FOLDER} && mkdir -p ${NPM_PACKAGE_FOLDER} " +
            " && mv ${unityWebGLPackagesOutputDir} ${NPM_PACKAGE_FOLDER}/  "

    println("Unity For WebGL打包成功 ✅")
}

/**
 * Node编译打包
 */
def nodeBuildProject() {
    monoRepoProjectDir = "" // monorepo项目所在目录 默认根目录
    if ("${IS_MONO_REPO}" == 'true') {  // 是否MonoRepo单体式仓库  单仓多包
        monoRepoProjectDir = "${MONO_REPO_MAIN_PACKAGE}/${PROJECT_NAME}"
    }

    if ("${IS_STATIC_RESOURCE}" == 'true') { // 静态资源项目
        // 静态文件打包
        if ("${WEB_PROJECT_TYPE}".toInteger() == GlobalVars.npmWeb) {
            if ("${IS_MONO_REPO}" == 'true') {  // 是否MonoRepo单体式仓库  单仓多包
                dir("${monoRepoProjectDir}") {
                    // MonoRepo静态文件打包
                    Web.staticResourceBuild(this)
                }
            } else {
                // 静态文件打包
                Web.staticResourceBuild(this)
            }
        }
    } else { // npm编译打包项目
        // 初始化Node环境变量
        Node.initEnv(this)
        // 动态切换Node版本
        // Node.change(this, "${NODE_VERSION}".replaceAll("Node", ""))
        // Node环境设置镜像
        Node.setMirror(this)
        // sh "rm -rf node_modules && npm cache clear --force"

        if ("${IS_MONO_REPO}" == 'true') {  // 是否MonoRepo单体式仓库  单仓多包
            // 基于Lerna管理的Monorepo仓库打包
            Web.monorepoBuild(this)
        } else {
            if ("${IS_NEED_SASS}" == 'true') { // 是否需要css预处理器sass  兼容老项目老代码
                // 是否需要css预处理器sass处理
                Web.needSass(this)
            }

            timeout(time: 30, unit: 'MINUTES') {
                try {
                    def retryCount = 0
                    retry(3) {
                        retryCount++
                        if (retryCount >= 2) {
                            sh "rm -rf node_modules && rm -f *lock*"
                            // 如果包404下载失败  可以更换官方镜像源重新下载
                            // Node.setOfficialMirror(this)
                        }
                        if (Git.isExistsChangeFile(this) || retryCount >= 2) { // 自动判断是否需要下载依赖  根据依赖配置文件在Git代码是否变化
                            println("安装依赖 📥")
                            // npm ci 与 npm install类似 进行CI/CD或生产发布时，最好使用npm ci 防止版本号错乱但依赖lock文件
                            sh " ${NPM_PACKAGE_TYPE} install || pnpm install || npm ci || yarn install "
                            // --prefer-offline &> /dev/null 加速安装速度 优先离线获取包不打印日志 但有兼容性问题
                        }

                        println("执行Node构建 🏗️  ")
                        sh " rm -rf ${NPM_PACKAGE_FOLDER} || true "
                        sh " npm run '${NPM_RUN_PARAMS}' "
                    }
                } catch (e) {
                    println(e.getMessage())
                    sh "rm -rf node_modules && rm -f *lock*"
                    error("Web打包失败, 终止当前Pipeline运行 ❌")
                }
            }
        }

        // NPM打包产出物位置
        npmPackageLocationDir = "${IS_MONO_REPO}" == 'true' ? "${monoRepoProjectDir}/${NPM_PACKAGE_FOLDER}" : "${NPM_PACKAGE_FOLDER}"
        npmPackageLocation = "${npmPackageLocationDir}" + ".tar.gz"
        println(npmPackageLocation)
        // 判断npm打包目录是否存在 打包名称规范不一致等
/*    if (!fileExists("${npmPackageLocationDir}/")) {
        // React框架默认打包目录是build , Angular框架默认打包目录是多层级的等  重命名到定义的目录名称
        sh "rm -rf ${NPM_PACKAGE_FOLDER} && mv build ${NPM_PACKAGE_FOLDER}"
    }*/
        buildPackageSize = Utils.getFolderSize(this, npmPackageLocationDir)
        Tools.printColor(this, "Web打包成功 ✅")
        // 压缩文件夹 易于加速传输
        if ("${IS_MONO_REPO}" == 'true') {
            sh "cd ${monoRepoProjectDir} && tar -zcvf ${NPM_PACKAGE_FOLDER}.tar.gz ${NPM_PACKAGE_FOLDER} >/dev/null 2>&1 "
        } else {
            sh "tar -zcvf ${NPM_PACKAGE_FOLDER}.tar.gz ${NPM_PACKAGE_FOLDER} >/dev/null 2>&1 "
        }

        // 替换自定义的nginx配置文件
        Deploy.replaceNginxConfig(this)

    }
}

/**
 * 制作镜像
 * 可通过ssh在不同机器上构建镜像
 */
def buildImage() {
    // Docker多阶段镜像构建处理
    Docker.multiStageBuild(this, "${DOCKER_MULTISTAGE_BUILD_IMAGES}")
    // 构建Docker镜像  只构建一次
    retry(2) { // 重试几次 可能网络等问题导致构建失败
        Docker.build(this, "${dockerImageName}")
    }
}

/**
 * 上传部署文件到远程云端
 */
def uploadRemote(filePath, map) {
    // ssh免密登录检测和设置
    autoSshLogin(map)
    timeout(time: 2, unit: 'MINUTES') {
        // 同步脚本和配置到部署服务器
        syncScript()
    }
    Tools.printColor(this, "上传部署文件到远程云端 🚀 ")
    def projectDeployFolder = "/${DEPLOY_FOLDER}/${FULL_PROJECT_NAME}/"
    if ("${IS_PUSH_DOCKER_REPO}" != 'true') { // 远程镜像库方式不需要再上传构建产物 直接远程仓库docker pull拉取镜像
        sh "cd ${filePath} && scp  ${npmPackageLocation} " +
                "${remote.user}@${remote.host}:${projectDeployFolder}"
    }
}

/**
 * 部署运行项目
 */
def runProject(map) {
    // 初始化docker
    initDocker()
    try {
        if ("${IS_PUSH_DOCKER_REPO}" == 'true') {
            // 拉取远程仓库Docker镜像
            Docker.pull(this, "${dockerImageName}")
        }
        sh " ssh  ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER}/web " +
                "&& ./docker-release-web.sh '${SHELL_WEB_PARAMS_GETOPTS}' ' "
    } catch (error) {
        println error.getMessage()
        currentBuild.result = 'FAILURE'
        error("部署运行步骤出现异常 ❌")
    }
}

/**
 * 健康探测
 */
def healthCheck(map, params = '') { // 可选参数
    if (params?.trim()) { // 为null或空判断
        // 单机滚动部署从服务
        healthCheckParams = params
    } else {
        healthCheckUrl = "http://${remote.host}:${SHELL_HOST_PORT}"
        healthCheckParams = " -a 1 -b ${healthCheckUrl}"
    }
    def healthCheckStart = new Date()
    timeout(time: 10, unit: 'MINUTES') {  // health-check.sh有检测超时时间 timeout为防止shell脚本超时失效兼容处理
        healthCheckMsg = sh(
                script: "ssh  ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER}/ && ./health-check.sh ${healthCheckParams} '",
                returnStdout: true).trim()
    }
    healthCheckTimeDiff = Utils.getTimeDiff(healthCheckStart, new Date()) // 计算应用启动时间

    if ("${healthCheckMsg}".contains("成功")) {
        Tools.printColor(this, "${healthCheckMsg} ✅")
        dingNotice(map, 1, "**成功 ✅**") // 钉钉成功通知
    } else if ("${healthCheckMsg}".contains("失败")) { // shell返回echo信息包含值
        isHealthCheckFail = true
        Tools.printColor(this, "${healthCheckMsg} ❌", "red")
        println("👉 健康探测失败原因分析: 查看应用服务启动日志是否失败")
        // 钉钉失败通知
        dingNotice(map, 1, "**失败或超时❌** [点击我验证](${healthCheckUrl}) 👈 ", "${BUILD_USER_MOBILE}")
        // 打印应用服务启动失败日志 方便快速排查错误
        Tools.printColor(this, "------------ 应用服务${healthCheckUrl} 启动异常日志开始 START 👇 ------------", "red")
        sh " ssh  ${remote.user}@${remote.host} 'docker logs ${dockerContainerName}' "
        Tools.printColor(this, "------------ 应用服务${healthCheckUrl} 启动异常日志结束 END 👆 ------------", "red")
        if ("${IS_ROLL_DEPLOY}" == 'true' || "${IS_BLUE_GREEN_DEPLOY}" == 'true') {
            println '分布式部署情况, 服务启动失败, 自动中止取消job, 防止继续部署导致其他应用服务挂掉 。'
            IS_ROLL_DEPLOY = false
        }
        IS_ARCHIVE = false // 不归档
        currentBuild.result = 'FAILURE' // 失败  不稳定UNSTABLE 取消ABORTED
        error("应用服务健康探测失败, 终止当前Pipeline运行 ❌")
        return
    }
}

/**
 * 滚动部署
 */
def scrollToDeploy(map) {
    // 主从架构与双主架构等  负载均衡和滚动更新worker应用服务
    if ("${IS_SAME_SERVER}" == 'false') {   // 不同服务器滚动部署
        def machineNum = 1
        if (remote_worker_ips.isEmpty()) {
            error("多机滚动部署, 请先在相关的Jenkinsfile.x文件配置其它服务器ip数组remote_worker_ips参数 ❌")
        }
        // 循环串行执行多机分布式部署
        remote_worker_ips.each { ip ->
            println ip
            remote.host = ip
            if (params.DEPLOY_MODE == GlobalVars.rollback) {
                uploadRemote("${archivePath}", map)
            } else {
                uploadRemote(Utils.getShEchoResult(this, "pwd"), map)
            }
            runProject()
            if (params.IS_HEALTH_CHECK == true) {
                machineNum++
                MACHINE_TAG = "${machineNum}号机" // 动态计算是几号机
                healthCheck(map)
            }
        }
    }
}

/**
 * 云原生K8S部署大规模集群 弹性扩缩容
 */
def k8sDeploy(map) {
    // 执行k8s集群部署
    Kubernetes.deploy(this, map)
    // 自动替换相同应用不同分布式部署节点的环境文件  打包构建上传不同的镜像
    if ("${IS_DIFF_CONF_IN_DIFF_MACHINES}" == 'true' && "${SOURCE_TARGET_CONFIG_DIR}".trim() != "" && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
        println("K8S集群部署相同应用不同环境的部署节点")
        Kubernetes.deploy(this, map, 2)
    }
}

/**
 * 自动设置免密连接 用于CI/CD服务器和应用部署服务器免密通信  避免手动批量设置繁琐重复劳动
 */
def autoSshLogin(map) {
    SecureShell.autoSshLogin(this, map)
}

/**
 * 同步脚本和配置到部署服务器
 */
def syncScript() {
    try {
        // 自动创建服务器部署目录
        // ssh登录概率性失败 连接数超报错: kex_exchange_identification
        // 解决vim /etc/ssh/sshd_config中 MaxSessions与MaxStartups改大2000 默认10 重启生效 systemctl restart sshd.service
        sh " ssh ${remote.user}@${remote.host} 'mkdir -p /${DEPLOY_FOLDER}/${FULL_PROJECT_NAME}' "
    } catch (error) {
        println "访问目标服务器失败, 首先检查jenkins服务器和应用服务器的ssh免密连接是否生效 ❌"
        println error.getMessage()
    }

    dir("${env.WORKSPACE}/ci") {
        try {
            // Docker多阶段镜像构建处理
            Docker.multiStageBuild(this, "${DOCKER_MULTISTAGE_BUILD_IMAGES}")
            // scp -r  递归复制整个目录 复制部署脚本和配置文件到服务器
            sh " chmod -R 777 .ci && scp -r .ci/*  ${remote.user}@${remote.host}:/${DEPLOY_FOLDER}/ "
        } catch (error) {
            println "复制部署脚本和配置文件到服务器失败 ❌"
            println error.getMessage()
        }

        // 给shell脚本执行权限
        sh " ssh  ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER} " +
                "&& chmod -R 777 web && chmod +x *.sh ' "
    }
}

/**
 * 是否存在CI代码
 */
def existCiCode() {
    if (!fileExists(".ci")) {
        // println "为保证先后顺序拉取代码 可能导致第一次构建时候无法找到CI仓库代码 重新拉取代码"
        pullCIRepo()
    }
}

/**
 * 初始化Docker引擎环境 自动化第一次部署环境
 */
def initDocker() {
    Docker.initDocker(this)
}

/**
 * 回滚版本
 */
def rollbackVersion(map) {
    if ("${ROLLBACK_BUILD_ID}" == '0') { // 默认回滚到上一个版本
        ROLLBACK_BUILD_ID = "${Integer.parseInt(env.BUILD_ID) - 2}"
    }
    //input message: "是否确认回滚到构建ID为${ROLLBACK_BUILD_ID}的版本", ok: "确认"
    //该/var/jenkins_home/**路径只适合在master节点执行的项目 不适合slave节点的项目
    archivePath = "/var/jenkins_home/jobs/${env.JOB_NAME}/builds/${ROLLBACK_BUILD_ID}/archive/"
    uploadRemote("${archivePath}", map)
    runProject(map)
    if (params.IS_HEALTH_CHECK == true) {
        healthCheck(map)
    }
    if ("${IS_ROLL_DEPLOY}" == 'true') {
        scrollToDeploy(map)
    }
}

/**
 * 归档文件
 */
def archive() {
    try {
        archiveArtifacts artifacts: "${npmPackageLocation}", onlyIfSuccessful: true
    } catch (error) {
        println "归档文件异常"
        println error.getMessage()
    }
}

/**
 * 删除打包产出物 减少磁盘占用
 */
def deletePackagedOutput() {
    try {
        sh " rm -f ${npmPackageLocation} "
    } catch (error) {
        println "删除打包产出物异常"
        println error.getMessage()
    }
}

/**
 * 生成二维码 方便手机端扫描
 */
def genQRCode(map) {
    if ("${IS_GEN_QR_CODE}" == 'true') { // 是否开启二维码生成功能
        try {
            imageSuffixName = "png"
            def imageName = "${PROJECT_NAME}"
            sh "rm -f *.${imageSuffixName}"
            QRCode.generate(this, "http://${remote.host}:${SHELL_HOST_PORT}", imageName)
            def sourceFile = "${env.WORKSPACE}/${imageName}.${imageSuffixName}" // 源文件
            def targetFile = "frontend/${env.JOB_NAME}/${env.BUILD_NUMBER}/${imageName}.${imageSuffixName}"
            // 目标文件
            qrCodeOssUrl = AliYunOSS.upload(this, map, sourceFile, targetFile)
            println "${qrCodeOssUrl}"
        } catch (error) {
            println " 生成二维码失败 ❌ "
            println error.getMessage()
        }
    }
}

/**
 * 总会执行统一处理方法
 */
def alwaysPost() {
    // sh 'pwd'
    // deleteDir()  // 清空工作空间
    try {
        def releaseEnvironment = "${NPM_RUN_PARAMS != "" ? NPM_RUN_PARAMS : SHELL_ENV_MODE}"
        def noticeHealthCheckUrl = "${APPLICATION_DOMAIN == "" ? healthCheckUrl : healthCheckDomainUrl}"
        currentBuild.description = "${IS_GEN_QR_CODE == 'true' ? "<img src=${qrCodeOssUrl} width=250 height=250 > <br/> " : ""}" +
                "<a href='${noticeHealthCheckUrl}'> 👉URL访问地址</a> " +
                "<br/> 项目: ${PROJECT_NAME}" +
                "${IS_PROD == 'true' ? "<br/> 版本: ${tagVersion}" : ""} " +
                "<br/> 大小: ${buildPackageSize} <br/> 分支: ${BRANCH_NAME} <br/> 环境: ${releaseEnvironment} <br/> 发布人: ${BUILD_USER}"
    } catch (error) {
        println error.getMessage()
    }
}

/**
 * 生成版本tag和变更日志
 */
def gitTagLog() {
    // 未获取到参数 兼容处理 因为参数配置从代码拉取 必须先执行一次jenkins任务才能生效
    if (!params.IS_GIT_TAG && params.IS_GIT_TAG != false) {
        params.IS_GIT_TAG = true
    }
    // 构建成功后生产环境并发布类型自动打tag和变更记录  指定tag方式不再重新打tag
    if (params.IS_GIT_TAG == true && "${IS_PROD}" == 'true' && params.GIT_TAG == GlobalVars.noGit) {
        // 获取变更记录
        def gitChangeLog = changeLog.genChangeLog(this, 100).replaceAll("\\;", "\n")
        def latestTag = ""
        try {
            if ("${params.VERSION_NUM}".trim() != "") { // 自定义版本号
                tagVersion = "${params.VERSION_NUM}".trim()
                println "手填的自定义版本号为: ${tagVersion} "
            } else {
                // sh ' git fetch --tags ' // 拉取远程分支上所有的tags 需要设置用户名密码
                // 获取本地当前分支最新tag名称 git describe --abbrev=0 --tags  获取远程仓库最新tag命令 git ls-remote   获取所有分支的最新tag名称命令 git describe --tags `git rev-list --tags --max-count=1`
                // 不同分支下的独立打的tag可能导致tag版本错乱的情况  过滤掉非语义化版本的tag版本号
                // latestTag = Utils.getShEchoResult(this, "git describe --abbrev=0 --tags")
                latestTag = Git.getGitTagMaxVersion(this)

                // 生成语义化版本号
                tagVersion = Utils.genSemverVersion(this, latestTag, gitChangeLog.contains(GlobalVars.gitCommitFeature) ?
                        GlobalVars.gitCommitFeature : GlobalVars.gitCommitFix)
            }
        } catch (error) {
            println "生成tag语义化版本号失败"
            println error.getMessage()
            // tagVersion = Utils.formatDate("yyyy-MM-dd") // 获取版本号失败 使用时间格式作为tag
            tagVersion = "1.0.${env.BUILD_NUMBER}" // 自动设置不重复tag版本 使用CI构建号作为tag
        }

        // 生成tag和变更日志
        gitTagLog.genTagAndLog(this, tagVersion, gitChangeLog, "${REPO_URL}", "${GIT_CREDENTIALS_ID}")
    }
    // 指定tag时候设置版本信息
    if (params.GIT_TAG != GlobalVars.noGit) {
        tagVersion = params.GIT_TAG
    }
}

/**
 * 钉钉通知
 * @type 0 失败 1 部署完成 2 部署之前 3 变更记录
 * @msg 自定义消息* @atMobiles 要@的手机号
 */
def dingNotice(map, int type, msg = '', atMobiles = '') {
    if ("${params.IS_DING_NOTICE}" == 'true') { // 是否钉钉通知
        println("钉钉通知: " + params.NOTIFIER_PHONES)
        // 格式化持续时间
        def durationTimeString = "${currentBuild.durationString.replace(' and counting', '').replace('min', 'm').replace('sec', 's')}".replace(' ', '')
        def notifierPhone = params.NOTIFIER_PHONES.split("-")[1].trim()
        if (notifierPhone == "oneself") { // 通知自己
            notifierPhone = "${BUILD_USER_MOBILE}"
        }
        if ("${IS_ROLL_DEPLOY}" == 'false' && "${IS_BLUE_GREEN_DEPLOY}" == 'false') {
            MACHINE_TAG = "" // 不是多节点部署不添加机器标识
        }
        def rollbackTag = ""
        if (params.DEPLOY_MODE == GlobalVars.rollback) {
            rollbackTag = "**回滚版本号: ${ROLLBACK_BUILD_ID}**" // 回滚版本添加标识
        }
        if (params.GIT_TAG != GlobalVars.noGit) {
            rollbackTag = "**Git Tag构建版本: ${params.GIT_TAG}**" // Git Tag版本添加标识
        }
        def monorepoProjectName = ""
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd && "${IS_MONO_REPO}" == 'true') {
            monorepoProjectName = "MonoRepo项目: ${PROJECT_NAME}"   // 单体仓库区分项目
        }
        def projectTypeName = "前端"
        def envTypeMark = "内测版"  // 环境类型标志
        if ("${IS_PROD}" == 'true') {
            envTypeMark = "正式版"
        }
        def releaseEnvironment = "${NPM_RUN_PARAMS != "" ? NPM_RUN_PARAMS : SHELL_ENV_MODE}"
        def noticeHealthCheckUrl = "${APPLICATION_DOMAIN == "" ? healthCheckUrl : healthCheckDomainUrl}"

        if (type == 0) { // 失败
            if (!isHealthCheckFail) {
                dingtalk(
                        robot: "${DING_TALK_CREDENTIALS_ID}",
                        type: 'MARKDOWN',
                        title: "CI/CD ${PROJECT_TAG}${envTypeMark}${projectTypeName}流水线失败通知",
                        text: [
                                "### [${env.JOB_NAME}#${env.BUILD_NUMBER}](${env.BUILD_URL}) ${PROJECT_TAG}${envTypeMark}${projectTypeName}项目${msg}",
                                "#### 请及时处理 🏃",
                                "###### ** 流水线失败原因: [运行日志](${env.BUILD_URL}console) 👈 **",
                                "###### Jenkins地址  [查看](${env.JENKINS_URL})   源码地址  [查看](${REPO_URL})",
                                "###### 发布环境: ${releaseEnvironment}  持续时间: ${durationTimeString}",
                                "###### 发布人: ${BUILD_USER}",
                                "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})"
                        ],
                        at: ["${BUILD_USER_MOBILE}"]
                )
            }
        } else if (type == 1 && "${IS_ONLY_NOTICE_CHANGE_LOG}" == 'false') { // 部署完成
            // 生成二维码 方便手机端扫描
            genQRCode(map)
            def screenshot = "![screenshot](${qrCodeOssUrl})"
            if ("${qrCodeOssUrl}" == "") {
                screenshot = ""
            }
            dingtalk(
                    robot: "${DING_TALK_CREDENTIALS_ID}",
                    type: 'ACTION_CARD',
                    title: "CI/CD ${PROJECT_TAG}${envTypeMark}${projectTypeName}部署结果通知",
                    text: [
                            "${screenshot}",
                            "### [${env.JOB_NAME}#${env.BUILD_NUMBER} ${PROJECT_TAG}${envTypeMark}${projectTypeName} ${MACHINE_TAG}](${env.JOB_URL})",
                            "##### 版本信息",
                            "- Nginx Web服务启动${msg}",
                            "- 构建分支: ${BRANCH_NAME}   环境: ${releaseEnvironment}",
                            "- Node版本: ${NODE_VERSION}   包大小: ${buildPackageSize}",
                            "${monorepoProjectName}",
                            "###### ${rollbackTag}",
                            "###### 启动用时: ${healthCheckTimeDiff}   持续时间: ${durationTimeString}",
                            "###### 访问URL: [${noticeHealthCheckUrl}](${noticeHealthCheckUrl})",
                            "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL})",
                            "###### 发布人: ${BUILD_USER}  构建机器: ${NODE_NAME}",
                            "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})"
                    ],
                    btns: [
                            [
                                    title    : "直接访问URL地址",
                                    actionUrl: "${noticeHealthCheckUrl}"
                            ]
                    ],
                    at: [isHealthCheckFail == true ? atMobiles : (notifierPhone == '110' ? '' : notifierPhone)]
            )
        } else if (type == 2) { // 部署之前
            dingtalk(
                    robot: "${DING_TALK_CREDENTIALS_ID}",
                    type: 'MARKDOWN',
                    title: "CI/CD ${PROJECT_TAG}${envTypeMark}${projectTypeName}部署前通知",
                    text: [
                            "### [${env.JOB_NAME}#${env.BUILD_NUMBER} ${envTypeMark}${projectTypeName}](${env.JOB_URL})",
                            "#### ${PROJECT_TAG}服务部署启动中 🚀  请稍等...  ☕",
                            "###### 发布人: ${BUILD_USER}",
                            "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})"
                    ],
                    at: []
            )
        } else if (type == 3) { // 变更记录
            def gitChangeLog = changeLog.genChangeLog(this, 20).replaceAll("\\;", "\n")
            if ("${gitChangeLog}" != GlobalVars.noChangeLog) {
                def titlePrefix = "${PROJECT_TAG} BUILD#${env.BUILD_NUMBER}"
                try {
                    if ("${tagVersion}") {
                        titlePrefix = "${PROJECT_TAG} ${tagVersion}"
                    }
                } catch (e) {
                }
                // 如果gitChangeLog为空 赋值提醒文案
                if ("${gitChangeLog}" == '') {
                    gitChangeLog = "无版本变更记录 🈳"
                }
                dingtalk(
                        robot: "${DING_TALK_CREDENTIALS_ID}",
                        type: 'MARKDOWN',
                        title: "${titlePrefix} ${envTypeMark}${projectTypeName}发布日志",
                        text: [
                                "### ${titlePrefix} ${envTypeMark}${projectTypeName}发布日志 🎉",
                                "#### 项目: ${PROJECT_NAME}",
                                "#### 环境: **${projectTypeName} ${IS_PROD == 'true' ? "生产环境" : "${releaseEnvironment}内测环境"}**",
                                "${gitChangeLog}",
                                ">  👉  前往 [变更日志](${REPO_URL.replace('.git', '')}/blob/${BRANCH_NAME}/CHANGELOG.md) 查看",
                                "###### 发布人: ${BUILD_USER}",
                                "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})"
                        ],
                        at: []
                )
            }
        }
    }
}

