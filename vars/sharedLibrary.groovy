#!groovy
import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*
import shared.library.devops.ChangeLog
import shared.library.devops.GitTagLog

/**
 * @author 潘维吉
 * @description 通用核心共享Pipeline脚本库
 * 针对大前端Web和服务端Java、Go、Python、C++等多语言项目
 */
def call(String type = 'web-java', Map map) {
    echo "Pipeline共享库脚本类型: ${type}, jenkins分布式节点名: 前端${map.jenkins_node_front_end} , 后端${map.jenkins_node} "
    // 应用共享方法定义
    changeLog = new ChangeLog()
    gitTagLog = new GitTagLog()

    // 初始化参数
    getInitParams(map)

    remote = [:]
    try {
        remote.host = "${REMOTE_IP}" // 部署应用程序服务器IP 动态参数 可配置在独立的job中
    } catch (exception) {
        // println exception.getMessage()
        remote.host = "${map.remote_ip}" // 部署应用程序服务器IP  不传参数 使用默认值
    }
    remote.user = "${map.remote_user_name}"
    remote_worker_ips = readJSON text: "${map.remote_worker_ips}"  // 分布式部署工作服务器地址 同时支持N个服务器
    // 代理机或跳板机外网ip用于透传部署到目标机器
    proxy_jump_ip = "${map.proxy_jump_ip}"

    if (type == "web-java") { // 针对标准项目
        pipeline {
            // 指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
            agent { label "${PROJECT_TYPE.toInteger() == GlobalVars.frontEnd ? "${map.jenkins_node_front_end}" : "${map.jenkins_node}"}" }
            //agent { label "${map.jenkins_node}" }

            parameters {
                choice(name: 'DEPLOY_MODE', choices: [GlobalVars.release, GlobalVars.rollback],
                        description: '选择部署方式  1. ' + GlobalVars.release + '发布 2. ' + GlobalVars.rollback +
                                '回滚(基于jenkins归档方式回滚选择' + GlobalVars.rollback + ', 基于Git Tag方式回滚请选择' + GlobalVars.release + ')')
                choice(name: 'MONOREPO_PROJECT_NAME', choices: "${MONOREPO_PROJECT_NAMES}",
                        description: "选择MonoRepo单体式统一仓库项目名称, ${GlobalVars.defaultValue}选项是MultiRepo多体式独立仓库或未配置, 大统一单体式仓库流水线可减少构建时间和磁盘空间")
                gitParameter(name: 'GIT_BRANCH', type: 'PT_BRANCH', defaultValue: "${BRANCH_NAME}", selectedValue: "DEFAULT",
                        useRepository: "${REPO_URL}", sortMode: 'ASCENDING', branchFilter: 'origin/(.*)',
                        description: "选择要构建的Git分支 默认: " + "${BRANCH_NAME} (可自定义配置具体任务的默认常用分支, 实现一键或全自动构建)")
                gitParameter(name: 'GIT_TAG', type: 'PT_TAG', defaultValue: GlobalVars.noGit, selectedValue: GlobalVars.noGit,
                        useRepository: "${REPO_URL}", sortMode: 'DESCENDING_SMART', tagFilter: '*',
                        description: "DEPLOY_MODE基于" + GlobalVars.release + "部署方式, 可选择指定Git Tag版本标签构建, 默认不选择是获取指定分支下的最新代码, 选择后按tag代码而非分支代码构建⚠️, 同时可作为一键回滚版本使用 🔙 ")
                string(name: 'ROLLBACK_BUILD_ID', defaultValue: '0', description: "DEPLOY_MODE基于" + GlobalVars.rollback + "部署方式, 输入对应保留的回滚构建记录ID, " +
                        "默认0是回滚到上一次连续构建, 当前归档模式的回滚仅适用于在master节点构建的任务")
                booleanParam(name: 'IS_HEALTH_CHECK', defaultValue: "${map.is_health_check}",
                        description: '是否执行服务启动健康检测 否: 可大幅减少构建时间 分布式部署不建议取消')
                booleanParam(name: 'IS_GIT_TAG', defaultValue: "${map.is_git_tag}",
                        description: '是否生产环境自动给Git仓库设置Tag版本和生成CHANGELOG.md变更记录')
                booleanParam(name: 'IS_DING_NOTICE', defaultValue: "${map.is_ding_notice}", description: "是否开启钉钉群通知 📢 ")
                choice(name: 'NOTIFIER_PHONES', choices: "${contactPeoples}", description: '选择要通知的人 (钉钉群内@提醒发布结果) 📢 ')
                //booleanParam(name: 'IS_DEPLOY_MULTI_ENV', defaultValue: false, description: '是否同时部署当前job项目多环境 如dev test等')
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
                        token: "jenkins", // 唯一标识 env.JOB_NAME
                        causeString: ' Triggered on $ref',
                        printContributedVariables: true,
                        printPostContent: true,
                        silentResponse: false,
                        regexpFilterText: '$project_git_http_url_$ref_$git_message',
                        // WebHooks触发后 正则匹配规则: 先匹配Job配置Git仓库确定项目, 根据jenkins job配置的分支匹配, 再匹配最新一次Git提交记录是否含有release发布关键字
                        // 如果是多模块项目再去匹配部署的模块 对于开发者只需要关心触发自动发布Git提交规范即可 如单模块: release 多模块: release(app)
                        // 针对monorepo单仓多包仓库 可根据changed_files变量中变更文件所在的项目匹配自动触发构建具体的分支
                        regexpFilterExpression: '^(' + "${REPO_URL}" + ')' +
                                '_(refs/heads/' + "${BRANCH_NAME}" + ')' +
                                '_(release)' + "${PROJECT_TYPE.toInteger() == GlobalVars.backEnd ? '\\(' + "${SHELL_PROJECT_TYPE}" + '\\)' : ''}" + '.*$'
                )
                // 每分钟判断一次代码是否存在变化 有变化就执行
                // pollSCM('H/1 * * * *')
            }

            environment {
                // 系统环境变量
                NODE_OPTIONS = "--max_old_space_size=4096" // NODE内存调整 防止打包内存溢出
                // jenkins节点java路径 适配不同版本jdk情况 /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
                //JAVA_HOME = "/var/jenkins_home/tools/hudson.model.JDK/${JDK_VERSION}${JDK_VERSION == '11' ? '/jdk-11' : ''}"
                // 动态设置环境变量  配置相关自定义工具
                //PATH = "${JAVA_HOME}/bin:$PATH"

                NODE_VERSION = "${map.nodejs}" // nodejs版本
                JDK_VERSION = "${map.jdk}" // JDK版本
                CI_GIT_CREDENTIALS_ID = "${map.ci_git_credentials_id}" // CI仓库信任ID
                GIT_CREDENTIALS_ID = "${map.git_credentials_id}" // Git信任ID
                DING_TALK_CREDENTIALS_ID = "${map.ding_talk_credentials_id}" // 钉钉授信ID 系统设置里面配置 自动生成
                DEPLOY_FOLDER = "${map.deploy_folder}" // 服务器上部署所在的文件夹名称
                NPM_PACKAGE_FOLDER = "${map.npm_package_folder}" // Web项目NPM打包代码所在的文件夹名称
                WEB_STRIP_COMPONENTS = "${map.web_strip_components}" // Web项目解压到指定目录层级
                MAVEN_ONE_LEVEL = "${map.maven_one_level}"// 如果Maven模块化存在二级模块目录 设置一级模块目录名称
                DOCKER_JAVA_OPTS = "${map.docker_java_opts}" // JVM内存设置
                DOCKER_MEMORY = "${map.docker_memory}" // docker内存限制
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
                IS_ARCHIVE = false // 是否归档
                IS_CODE_QUALITY_ANALYSIS = false // 是否进行代码质量分析的总开关
                IS_INTEGRATION_TESTING = false // 是否进集成测试
                IS_NOTICE_CHANGE_LOG = "${map.is_notice_change_log}" // 是否通知变更记录
            }

            options {
                //失败重试次数
                retry(0)
                //超时时间 job会自动被终止
                timeout(time: 45, unit: 'MINUTES')
                //保持构建的最大个数
                buildDiscarder(logRotator(numToKeepStr: "${map.build_num_keep}", artifactNumToKeepStr: "${map.build_num_keep}"))
                //控制台输出增加时间戳
                timestamps()
                //不允许同一个job同时执行流水线,可被用来防止同时访问共享资源等
                disableConcurrentBuilds()
                //如果某个stage为unstable状态，则忽略后面的任务，直接退出
                skipStagesAfterUnstable()
                //安静的时期 设置管道的静默时间段（以秒为单位），以覆盖全局默认值
                quietPeriod(3)
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

                /*   stage('扫码代码') {
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

                stage('Docker For JavaScript构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == true && "${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) }
                    }
                    agent {
                        // label "linux"
                        docker {
                            // Node环境  构建完成自动删除容器
                            image "node:${NODE_VERSION.replace('Node', '')}"
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            echo "Docker环境内构建Node方式"
                            nodeBuildProject()
                        }
                    }
                }

                stage('JavaScript构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == false && "${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) }
                    }
                    tools {
                        // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中
                        nodejs "${NODE_VERSION}"
                    }
                    steps {
                        script {
                            nodeBuildProject()
                        }
                    }
                }

                stage('Docker For Java构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == true && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) }
                    }
                    agent {
                        docker {
                            // JDK MAVEN 环境  构建完成自动删除容器
                            image "maven:${map.maven.replace('Maven', '')}-openjdk-${JDK_VERSION}"
                            args " -v /var/cache/maven/.m2:/root/.m2 "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            mavenBuildProject()
                        }
                    }
                }
                stage('Java构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == false && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) }
                    }
                    tools {
                        // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中
                        maven "${map.maven}"
                        jdk "${JDK_VERSION}"
                    }
                    steps {
                        script {
                            mavenBuildProject()
                        }
                    }
                }

                stage('Go构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Go) }
                    }
                    steps {
                        script {
                            goBuildProject()
                        }
                    }
                }

                stage('Docker For Python构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == true && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) }
                    }
                    agent {
                        docker {
                            // Python打包环境  构建完成自动删除容器
                            image "cdrx/pyinstaller-linux:python3" // cdrx/pyinstaller-windows
                            args " -v $PWD:/src/ "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            pythonBuildProject()
                        }
                    }
                }
                stage('Python构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == false && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) }
                    }
                    steps {
                        script {
                            pythonBuildProject()
                        }
                    }
                }

                stage('C++构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Cpp) }
                    }
                    steps {
                        script {
                            cppBuildProject()
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

                stage('上传云端') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                    }
                    steps {
                        script {
                            uploadRemote(Utils.getShEchoResult(this, "pwd"))
                        }
                    }
                }

                stage('人工审批') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return false
                        }
                    }
                    steps {
                        script {
                            manualApproval()
                        }
                    }
                }

                stage('单机部署') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_BLUE_GREEN_DEPLOY == false)  // 非蓝绿部署 蓝绿部署有单独步骤
                        }
                    }
                    steps {
                        script {
                            runProject()
                        }
                    }
                }

                stage('健康检测') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (params.IS_HEALTH_CHECK == true && IS_BLUE_GREEN_DEPLOY == false)
                        }
                    }
                    steps {
                        script {
                            healthCheck()
                        }
                    }
                }

                stage('集成测试') {
                    when {
                        beforeAgent true
                        // 生产环境不进行集成测试 缩减构建时间
                        not {
                            anyOf {
                                branch 'master'
                                branch 'prod'
                            }
                        }
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            // 是否进行集成测试  是否存在postman_collection.json文件才进行API集成测试  fileExists("_test/postman/postman_collection.json") == true
                            return ("${IS_INTEGRATION_TESTING}" == 'true' && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd
                                    && "${AUTO_TEST_PARAM}" != "" && IS_BLUE_GREEN_DEPLOY == false)
                        }
                    }
/*                    agent {
                        docker {
                            // Node环境  构建完成自动删除容器
                            image "node:${NODE_VERSION}"
                            reuseNode true // 使用根节点
                        }
                    }*/
                    steps {
                        script {
                            integrationTesting()
                        }
                    }
                }

                stage('蓝绿部署') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_BLUE_GREEN_DEPLOY == true)  // 是否进行蓝绿部署
                        }
                    }
                    steps {
                        script {
                            // 蓝绿部署是实现零停机部署最经济的方式 只有单个服务长期占用资源
                            blueGreenDeploy()
                        }
                    }
                }

                stage('滚动部署') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_ROLL_DEPLOY == true) // 是否进行滚动部署
                        }
                    }
                    tools {
                        // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中
                        maven "${map.maven}"
                    }
                    steps {
                        script {
                            // 滚动部署实现多台服务按顺序更新 分布式零停机
                            scrollToDeploy()
                        }
                    }
                }

                stage('灰度发布') {
                    when {
                        // branch 'master'
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_GRAYSCALE_DEPLOY == true) // 是否进行灰度发布
                        }
                    }
                    steps {
                        script {
                            // 基于Nginx Ingress 灰度发布  实现多版本共存 非强制更新提升用户体验
                            grayscaleDeploy()
                        }
                    }
                }

                stage('Kubernetes云原生') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_K8S_DEPLOY == true)  // 是否进行云原生K8S集群部署
                        }
                    }
                    steps {
                        script {
                            // 云原生K8s部署大规模集群
                            k8sDeploy()
                        }
                    }
                }

                stage('Serverless工作流') {
                    when {
                        // branch 'master'
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_SERVERLESS_DEPLOY == true) // 是否进行Serverless发布
                        }
                    }
                    steps {
                        script {
                            // Serverless发布方式免运维
                            serverlessDeploy()
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
                            dingNotice(3)
                        }
                    }
                }

                stage('制品仓库') {
                    when {
                        // branch 'master'
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return false  // 是否进行制品仓库
                        }
                    }
                    steps {
                        script {
                            productsWarehouse(map)
                        }
                    }
                }

                stage('Prometheus运维') {
                    when {
                        // branch 'master'
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return false  // 是否进行部署监控
                        }
                    }
                    steps {
                        script {
                            echo "随着应用服务部署 新一代Prometheus监控 全面检测应用健康情况"
                        }
                    }
                }

                stage('归档') {
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
                            rollbackVersion()
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
                        //deployMultiEnv()
                    }
                }
                failure {
                    script {
                        echo '当前失败时才运行'
                        dingNotice(0, "CI/CD流水线失败 ❌")
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

    } else if (type == "web-java-2") {  // 注意！！！ 差异性较大的Pipeline建议区分groovy文件维护

    }
}


/**
 *  获取初始化参数方法
 */
def getInitParams(map) {
    def jsonParams = readJSON text: "${JSON_PARAMS}"
    // println "${jsonParams}"
    REPO_URL = jsonParams.REPO_URL ? jsonParams.REPO_URL.trim() : "" // Git源码地址
    BRANCH_NAME = jsonParams.BRANCH_NAME ? jsonParams.BRANCH_NAME.trim() : GlobalVars.defaultBranch  // Git默认分支
    PROJECT_TYPE = jsonParams.PROJECT_TYPE ? jsonParams.PROJECT_TYPE.trim() : ""  // 项目类型 1 前端项目 2 后端项目
    // 计算机语言类型 1. Java  2. Go  3. Python  5. C++
    COMPUTER_LANGUAGE = jsonParams.COMPUTER_LANGUAGE ? jsonParams.COMPUTER_LANGUAGE.trim() : "1"
    // 项目名 获取部署资源位置和指定构建模块名等
    PROJECT_NAME = jsonParams.PROJECT_NAME ? jsonParams.PROJECT_NAME.trim() : ""
    SHELL_PARAMS = jsonParams.SHELL_PARAMS ? jsonParams.SHELL_PARAMS.trim() : "" // shell传入前端或后端参数

    // npm包管理工具类型 如:  npm、yarn、pnpm
    NPM_PACKAGE_TYPE = jsonParams.NPM_PACKAGE_TYPE ? jsonParams.NPM_PACKAGE_TYPE.trim() : "npm"
    NPM_RUN_PARAMS = jsonParams.NPM_RUN_PARAMS ? jsonParams.NPM_RUN_PARAMS.trim() : "" // npm run [test]的前端项目参数

    // 是否使用Docker容器环境方式构建打包 false使用宿主机环境
    IS_DOCKER_BUILD = jsonParams.IS_DOCKER_BUILD ? jsonParams.IS_DOCKER_BUILD : true
    IS_BLUE_GREEN_DEPLOY = jsonParams.IS_BLUE_GREEN_DEPLOY ? jsonParams.IS_BLUE_GREEN_DEPLOY : false // 是否蓝绿部署
    IS_ROLL_DEPLOY = jsonParams.IS_ROLL_DEPLOY ? jsonParams.IS_ROLL_DEPLOY : false // 是否滚动部署
    IS_GRAYSCALE_DEPLOY = jsonParams.IS_GRAYSCALE_DEPLOY ? jsonParams.IS_GRAYSCALE_DEPLOY : false // 是否灰度发布
    IS_K8S_DEPLOY = jsonParams.IS_K8S_DEPLOY ? jsonParams.IS_K8S_DEPLOY : false // 是否K8s集群部署
    IS_SERVERLESS_DEPLOY = jsonParams.IS_SERVERLESS_DEPLOY ? jsonParams.IS_SERVERLESS_DEPLOY : false // 是否Serverless发布
    IS_STATIC_RESOURCE = jsonParams.IS_STATIC_RESOURCE ? jsonParams.IS_STATIC_RESOURCE : false // 是否静态web资源
    IS_UPLOAD_OSS = jsonParams.IS_UPLOAD_OSS ? jsonParams.IS_UPLOAD_OSS : false // 是否构建产物上传到OSS
    IS_MONO_REPO = jsonParams.IS_MONO_REPO ? jsonParams.IS_MONO_REPO : false // 是否monorepo单体仓库
    // 是否Maven单模块代码
    IS_MAVEN_SINGLE_MODULE = jsonParams.IS_MAVEN_SINGLE_MODULE ? jsonParams.IS_MAVEN_SINGLE_MODULE : false

    // 设置monorepo单体仓库主包文件夹名
    MONO_REPO_MAIN_PACKAGE = jsonParams.MONO_REPO_MAIN_PACKAGE ? jsonParams.MONO_REPO_MAIN_PACKAGE.trim() : "projects"
    // Maven自定义指定settings.xml文件  如设置私有库或镜像源情况
    MAVEN_SETTING_XML = jsonParams.MAVEN_SETTING_XML ? jsonParams.MAVEN_SETTING_XML.trim() : "${map.maven_setting_xml}".trim()
    AUTO_TEST_PARAM = jsonParams.AUTO_TEST_PARAM ? jsonParams.AUTO_TEST_PARAM.trim() : ""  // 自动化集成测试参数
    // Java框架类型 1. Spring Boot  2. Spring MVC
    JAVA_FRAMEWORK_TYPE = jsonParams.JAVA_FRAMEWORK_TYPE ? jsonParams.JAVA_FRAMEWORK_TYPE.trim() : "1"
    // 自定义特殊化的Nginx配置文件在项目源码中的路径  用于替换CI仓库的config默认标准配置文件
    CUSTOM_NGINX_CONFIG = jsonParams.CUSTOM_NGINX_CONFIG ? jsonParams.CUSTOM_NGINX_CONFIG.trim() : ""
    // 自定义Docker挂载映射 docker run -v 参数  多个用逗号,分割
    DOCKER_VOLUME_MOUNT = jsonParams.DOCKER_VOLUME_MOUNT ? jsonParams.DOCKER_VOLUME_MOUNT.trim() : "${map.docker_volume_mount}".trim()
    // 不同部署节点动态批量替换多个环境配置文件 源文件目录 目标文件目录 逗号,分割
    SOURCE_TARGET_CONFIG_DIR = jsonParams.SOURCE_TARGET_CONFIG_DIR ? jsonParams.SOURCE_TARGET_CONFIG_DIR.trim() : ""

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
    SHELL_ENV_MODE = SHELL_PARAMS_ARRAY[4] // 环境模式 如 dev test prod等

    // 获取通讯录
    contactPeoples = ""
    try {
        def data = libraryResource('contacts.yaml')
        Map contacts = readYaml text: data
        contactPeoples = "${contacts.people}"
    } catch (e) {
        println("获取通讯录失败")
        println(e.getMessage())
    }

    // tag版本变量定义
    tagVersion = ""
    // 是否健康检测失败状态
    isHealthCheckFail = false
    // 扫描二维码地址
    qrCodeOssUrl = ""
    // Java构建包OSS地址Url
    javaOssUrl = ""
    // Web构建包大小
    webPackageSize = ""
    // Java打包类型 jar、war
    javaPackageType = ""
    // Java构建包大小
    javaPackageSize = ""
    // Maven打包后产物的位置
    mavenPackageLocation = ""
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
    try {
        echo "$git_event_name"
        IS_AUTO_TRIGGER = true
    } catch (e) {
    }
    // 初始化docker环境变量
    Docker.initEnv(this)

    // 不同语言使用不同的从服务部署脚本
    dockerReleaseWorkerShellName = ""
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
        dockerReleaseWorkerShellName = "docker-release-worker.sh"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Go) {
        dockerReleaseWorkerShellName = "go/docker-release-worker-go.sh"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
        dockerReleaseWorkerShellName = "python/docker-release-worker-python.sh"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Cpp) {
        dockerReleaseWorkerShellName = "cpp/docker-release-worker-cpp.sh"
    }

    // 是否跳板机穿透方式部署
    isProxyJumpType = false
    // 跳板机ssh ProxyJump访问新增的文本
    proxyJumpSSHText = "" // ssh跳板透传远程访问
    proxyJumpSCPText = "" // scp跳板透传远程复制
    if ("${proxy_jump_ip}".trim() != "") {
        isProxyJumpType = true
        // ssh -J root@外网跳板机IP:22 root@内网目标机器IP -p 22
        proxyJumpSSHText = " -J root@${proxy_jump_ip} "
        proxyJumpSCPText = " -o 'ProxyJump root@${proxy_jump_ip}' "
    }

}

/**
 * 组装初始化shell参数
 */
def getShellParams(map) {
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
        SHELL_WEB_PARAMS_GETOPTS = " -a ${SHELL_PROJECT_NAME} -b ${SHELL_PROJECT_TYPE} -c ${SHELL_HOST_PORT} " +
                "-d ${SHELL_EXPOSE_PORT} -e ${SHELL_ENV_MODE}  -f ${DEPLOY_FOLDER} -g ${NPM_PACKAGE_FOLDER} -h ${WEB_STRIP_COMPONENTS} " +
                "-i ${IS_PUSH_DOCKER_REPO}  -k ${DOCKER_REPO_REGISTRY}/${DOCKER_REPO_NAMESPACE}  "
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
        // 使用getopts的方式进行shell参数传递
        SHELL_PARAMS_GETOPTS = " -a ${SHELL_PROJECT_NAME} -b ${SHELL_PROJECT_TYPE} -c ${SHELL_HOST_PORT} " +
                "-d ${SHELL_EXPOSE_PORT} -e ${SHELL_ENV_MODE}  -f ${IS_PROD} -g ${DOCKER_JAVA_OPTS} -h ${DOCKER_MEMORY} " +
                "-i ${DOCKER_LOG_OPTS}  -k ${DEPLOY_FOLDER} -l ${JDK_VERSION} -m ${IS_PUSH_DOCKER_REPO} " +
                "-n ${DOCKER_REPO_REGISTRY}/${DOCKER_REPO_NAMESPACE} -q ${JAVA_FRAMEWORK_TYPE} "
        if ("${DOCKER_VOLUME_MOUNT}") {
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -o ${DOCKER_VOLUME_MOUNT} "
        }
        if ("${SHELL_PARAMS_ARRAY.length}" == '6') {
            SHELL_REMOTE_DEBUG_PORT = SHELL_PARAMS_ARRAY[5] // 远程调试端口
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -y ${SHELL_REMOTE_DEBUG_PORT}"
        }

        if ("${SHELL_PARAMS_ARRAY.length}" == '7') {
            SHELL_EXTEND_PORT = SHELL_PARAMS_ARRAY[6]  // 扩展端口
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
    if ("${IS_AUTO_TRIGGER}" == 'true') { // 自动触发构建
        BUILD_USER = "$git_user_name"
        BUILD_USER_EMAIL = "$git_user_email"
    } else {
        wrap([$class: 'BuildUser']) {
            try {
                BUILD_USER = env.BUILD_USER
                BUILD_USER_EMAIL = env.BUILD_USER_EMAIL
                // 获取钉钉插件手机号 注意需要系统设置里in-process script approval允许权限
                def user = hudson.model.User.getById(env.BUILD_USER_ID, false).getProperty(io.jenkins.plugins.DingTalkUserProperty.class)
                BUILD_USER_MOBILE = user.mobile
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
    // 自定义选择指定分支 不使用配置好的分支情况
    if (params.IS_GIT_TAG && "${BRANCH_NAME}" != "${params.GIT_BRANCH}") {
        BRANCH_NAME = "${params.GIT_BRANCH}"  // Git分支
    }

    // 获取应用打包代码
    if (params.GIT_TAG == GlobalVars.noGit) {
        println "Git构建分支是: ${BRANCH_NAME} 📇"
        // def git = git url: "${REPO_URL}", branch: "${BRANCH_NAME}", credentialsId: "${GIT_CREDENTIALS_ID}"
        // println "${git}"
        // 对于大体积仓库或网络不好情况 自定义代码下载超时时间 默认10分钟
        checkout([$class           : 'GitSCM',
                  branches         : [[name: "*/${BRANCH_NAME}"]],
                  extensions       : [[$class: 'CloneOption', timeout: 30]],
                  gitTool          : 'Default',
                  userRemoteConfigs: [[credentialsId: "${GIT_CREDENTIALS_ID}", url: "${REPO_URL}"]]
        ])
    } else {
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
    SonarQube.scan(this, "${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}")
    // SonarQube.getStatus(this, "${PROJECT_NAME}")
/*    def scannerHome = tool 'SonarQube' // 工具名称
    withSonarQubeEnv('SonarQubeServer') { // 服务地址链接名称
        // 如果配置了多个全局服务器连接，则可以指定其名称
        sh "${scannerHome}/bin/sonar-scanner"
        // sh "/usr/local/bin/sonar-scanner --version"
    }*/
    // 可打通项目管理平台自动提交bug指派任务
}

/**
 * Node编译构建
 */
def nodeBuildProject() {
    monoRepoProjectDir = "" // monorepo项目所在目录 默认根目录
    if ("${IS_MONO_REPO}" == 'true') {  // 是否monorepo单体仓库
        monoRepoProjectDir = "${MONO_REPO_MAIN_PACKAGE}/${PROJECT_NAME}"
    }

    if ("${IS_STATIC_RESOURCE}" == 'true') { // 静态资源项目
        if ("${IS_MONO_REPO}" == 'true') {  // 是否monorepo单体仓库
            dir("${monoRepoProjectDir}") {
                // MonoRepo静态文件打包
                Web.staticResourceBuild(this)
            }
        } else {
            // 静态文件打包
            Web.staticResourceBuild(this)
        }
    } else { // npm编译打包项目
        if (IS_DOCKER_BUILD == false) { // 宿主机环境情况
            // 初始化Node环境变量
            Node.initEnv(this)
            // 动态切换Node版本
            // Node.change(this, "${NODE_VERSION}".replaceAll("Node", ""))
        }
        // Node环境设置镜像
        Node.setMirror(this)
        // sh "rm -rf node_modules && npm cache clear --force"

        if ("${IS_MONO_REPO}" == 'true') {  // 是否monorepo单体仓库
            // 基于Lerna管理的Monorepo仓库打包
            Web.monorepoBuild(this)
        } else {
            if ("${IS_NEED_SASS}" == 'true') { // 是否需要css预处理器sass  兼容老项目老代码
                // 是否需要css预处理器sass处理
                Web.needSass(this)
            }

            if (Git.isExistsChangeFile(this)) { // 自动判断是否需要下载依赖 可新增动态参数用于强制下载依赖情况
                retry(2) {
                    println("安装依赖 📥")
                    // npm ci 与 npm install类似 进行CI/CD或生产发布时，最好使用npm ci 防止版本号错乱
                    sh "npm ci" // --prefer-offline &> /dev/null 加速安装速度 优先离线获取包不打印日志 但有兼容性问题
                }
            }

            timeout(time: 10, unit: 'MINUTES') {
                try {
                    // >/dev/null为Shell脚本运行程序不输出日志到终端 2>&1是把出错输出也定向到标准输出
                    println("执行npm构建 🏗️  ")
                    sh "npm run '${NPM_RUN_PARAMS}' " // >/dev/null 2>&1
                } catch (e) {
                    println(e.getMessage())
                    sh "rm -rf node_modules"
                    error("Web打包失败, 中止当前pipeline运行 ❌")
                }
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
    webPackageSize = Utils.getFolderSize(this, npmPackageLocationDir)
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

/**
 * Maven编译构建
 */
def mavenBuildProject() {
    if (IS_DOCKER_BUILD == false) { // 宿主机环境情况
        // 动态切换Maven内的对应的JDK版本
        Java.switchJDKByJenv(this, "${JDK_VERSION}")
    }
    sh "mvn --version"
    // 自动替换不同分布式部署节点的环境文件
    Deploy.replaceEnvFile(this)
    // maven如果存在多级目录 一级目录设置
    MAVEN_ONE_LEVEL = "${MAVEN_ONE_LEVEL}".trim() != "" ? "${MAVEN_ONE_LEVEL}/" : "${MAVEN_ONE_LEVEL}".trim()
    println("执行Maven构建 🏗️  ")
    if ("${MAVEN_SETTING_XML}" == "") {
        // 更快的构建工具mvnd 多个的守护进程来服务构建请求来达到并行构建的效果  源码: https://github.com/apache/maven-mvnd
        if ("${IS_MAVEN_SINGLE_MODULE}" == 'true') { // 如果是整体单模块项目 不区分多模块也不需要指定项目模块名称
            MAVEN_ONE_LEVEL = ""
            // 在pom.xml文件目录下执行 规范是pom.xml在代码根目录
            // def pomPath = Utils.getShEchoResult(this, " find . -name \"pom.xml\" ").replace("pom.xml", "")
            sh "mvn clean install -Dmaven.test.skip=true"
        } else {  // 多模块情况
            // 单独指定模块构建 -pl指定项目名 -am 同时构建依赖项目模块 跳过测试代码
            sh "mvn clean install -pl ${MAVEN_ONE_LEVEL}${PROJECT_NAME} -am -Dmaven.test.skip=true"
        }
    } else {
        // 基于自定义setting.xml文件方式打包 如私有包等
        Maven.packageBySettingFile(this)
    }
    // 获取pom文件信息
    //Maven.getPomInfo(this)

    if ("${JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringBoot) {
        javaPackageType = "jar"
    } else if ("${JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringMVC) {
        javaPackageType = "war"
    }
    // Maven打包产出物位置
    if ("${IS_MAVEN_SINGLE_MODULE}" == 'true') {
        mavenPackageLocationDir = "target"
    } else {
        mavenPackageLocationDir = ("${MAVEN_ONE_LEVEL}" == "" ? "${PROJECT_NAME}" : "${MAVEN_ONE_LEVEL}${PROJECT_NAME}") + "/target"
    }
    mavenPackageLocation = "${mavenPackageLocationDir}" + "/*.${javaPackageType}"
    println(mavenPackageLocation)
    javaPackageSize = Utils.getFileSize(this, mavenPackageLocation)
    Tools.printColor(this, "Maven打包成功 ✅")
    // 上传部署文件到OSS
    uploadOss()
}

/**
 * Go编译构建
 */
def goBuildProject() {
    Go.build(this)
    Tools.printColor(this, "Go语言构建成功 ✅")
}

/**
 * Python编译构建
 */
def pythonBuildProject() {
    Python.build(this)
    Tools.printColor(this, "Python语言构建成功 ✅")
}

/**
 * C++编译构建
 */
def cppBuildProject() {
    Cpp.build(this)
    Tools.printColor(this, "C++语言构建成功 ✅")
}

/**
 * 制作镜像
 * 可通过ssh在不同机器上构建镜像
 */
def buildImage() {
    // 定义镜像唯一构建名称
    dockerBuildImageName = "${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}-${SHELL_ENV_MODE}"
    // Docker多阶段镜像构建处理
    Docker.multiStageBuild(this, "${DOCKER_MULTISTAGE_BUILD_IMAGES}")
    // 构建Docker镜像  只构建一次
    Docker.build(this, "${dockerBuildImageName}")
}

/**
 * 上传部署文件到OSS
 * 方便下载构建部署包
 */
def uploadOss() {
    if ("${IS_UPLOAD_OSS}" == 'true') {
        try {
            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                // 源文件地址
                def sourceFile = "${env.WORKSPACE}/${mavenPackageLocation}"
                // 目标文件
                def targetFile = "java/${env.JOB_NAME}/${PROJECT_NAME}-${SHELL_ENV_MODE}-${env.BUILD_NUMBER}.${javaPackageType}"
                javaOssUrl = AliYunOss.upload(this, sourceFile, targetFile)
                println "${javaOssUrl}"
                Tools.printColor(this, "上传部署文件到OSS成功 ✅")
            }
        } catch (error) {
            println "上传部署文件到OSS异常"
            println error.getMessage()
        }
    }
}

/**
 * 上传部署文件到远程云端
 */
def uploadRemote(filePath) {
    // ssh免密登录检测和设置
    autoSshLogin()
    timeout(time: 1, unit: 'MINUTES') {
        // 同步脚本和配置到部署服务器
        syncScript()
    }
    Tools.printColor(this, "上传部署文件到远程云端 🚀 ")
    def projectDeployFolder = "/${DEPLOY_FOLDER}/${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}/"
    if ("${IS_PUSH_DOCKER_REPO}" != 'true') { // 远程镜像库方式不需要再上传构建产物 直接远程仓库docker pull拉取镜像
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            sh "cd ${filePath} && scp ${proxyJumpSCPText} ${npmPackageLocation} " +
                    "${remote.user}@${remote.host}:${projectDeployFolder}"
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
            // 上传前删除部署目录的jar包 防止名称修改等导致多个部署目标jar包存在  jar包需要唯一性
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd ${projectDeployFolder} && rm -f *.${javaPackageType}' "
            // 上传构建包到远程服务器
            sh "cd ${filePath} && scp ${proxyJumpSCPText} ${mavenPackageLocation} " +
                    "${remote.user}@${remote.host}:${projectDeployFolder} "
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Go) {
            // Go语言打包产物 上传包到远程服务器
            sh "cd ${filePath} && scp ${proxyJumpSCPText} main.go ${remote.user}@${remote.host}:${projectDeployFolder} "
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
            // Python语言打包产物 上传包到远程服务器
            sh "cd ${filePath}/dist && scp ${proxyJumpSCPText} app ${remote.user}@${remote.host}:${projectDeployFolder} "
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Cpp) {
            // C++语言打包产物 上传包到远程服务器
            sh "cd ${filePath} && scp ${proxyJumpSCPText} app ${remote.user}@${remote.host}:${projectDeployFolder} "
        }
    }
}

/**
 * 人工卡点审批
 * 每一个人都有点击执行流水线权限  但是不一定有发布上线的权限 为了保证项目稳定安全等需要人工审批
 */
def manualApproval() {
    // 针对生产环境部署前做人工发布审批
    if ("${IS_PROD}" == 'true') {
        // 选择具有审核权限的人员 可以配置一个或多个
        def approvalPersons = ["潘维吉"] // 多审批人数组 参数化配置 也可指定审批人
        def approvalPersonMobiles = ["18863302302"] // 审核人的手机数组 用于钉钉通知等

        // 两种审批 1. 或签(一名审批人员同意或拒绝即可) 2. 会签(须所有审批人同意)
        if ("${approvalPersons}".contains("${BUILD_USER}")) {
            // 如果是有审核权限人员发布的跳过本次审核
        } else {
            // 同时钉钉通知到审核人 点击链接自动进入要审核流水线  如果Jenkins提供Open API审核可直接在钉钉内完成点击审批
            DingTalk.notice(this, "${DING_TALK_CREDENTIALS_ID}", "发布流水线申请人工审批 ✍🏻 ",
                    "#### ${BUILD_USER}申请发布${PROJECT_NAME}服务, [请您审批](${env.BUILD_URL}) 👈🏻  !" +
                            " \n ###### Jenkins  [运行日志](${env.BUILD_URL}console)  " +
                            " \n ###### 发布人: ${BUILD_USER}" +
                            " \n ###### 通知时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                    "${approvalPersonMobiles}".split(","))
            input {
                message "请【${approvalPersons.split(",")}】相关人员审批本次部署, 是否同意继续发布 ?"
                ok "同意"
            }
            def currentUser = env.BUILD_USER
            println(currentUser)
            if (!"${approvalPersons}".contains(currentUser)) {
                error("人工审批失败, 您没有审批的权限, 请重新运行流水线发起审批 ❌")
            } else {
                // 审核人同意后通知发布人 消息自动及时高效传递
                DingTalk.notice(this, "${DING_TALK_CREDENTIALS_ID}", "您发布流水线已被${currentUser}审批同意 ✅",
                        "#### 前往流水线 [查看](${env.BUILD_URL})  !" +
                                " \n ###### 审批时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                        "${BUILD_USER_MOBILE}")
            }
        }
    }
}

/**
 * 部署启动运行项目
 */
def runProject() {
    // 初始化docker
    initDocker()
    try {
        if ("${IS_PUSH_DOCKER_REPO}" == 'true') {
            // 拉取远程仓库Docker镜像
            Docker.pull(this, "${dockerBuildImageName}")
        }
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER}/web " +
                    "&& ./docker-release-web.sh '${SHELL_WEB_PARAMS_GETOPTS}' ' "
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
            // 部署之前的相关操作
            beforeRunProject()
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER} " +
                    "&& ./docker-release.sh '${SHELL_PARAMS_GETOPTS}' '  "
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Go) {
            // Go.deploy(this)
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER}/go " +
                    "&& ./docker-release-go.sh '${SHELL_PARAMS_GETOPTS}' '  "
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER}/python " +
                    "&& ./docker-release-python.sh '${SHELL_PARAMS_GETOPTS}' '  "
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Cpp) {
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER}/cpp " +
                    "&& ./docker-release-cpp.sh '${SHELL_PARAMS_GETOPTS}' '  "
        }
    } catch (error) {
        println error.getMessage()
        currentBuild.result = 'FAILURE'
        error("部署运行步骤出现异常 ❌")
    }
}

/**
 * 健康检测
 */
def healthCheck(params = '') { // 可选参数
    Tools.printColor(this, "开始应用服务健康探测, 请耐心等待... 🚀 ")
    if (params?.trim()) { // 为null或空判断
        // 单机分布式部署从服务
        healthCheckParams = params
    } else {
        healthCheckUrl = "http://${remote.host}:${SHELL_HOST_PORT}"
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) { // 服务端
            healthCheckUrl = "${healthCheckUrl}/"
        }
        healthCheckParams = " -a ${PROJECT_TYPE} -b ${healthCheckUrl}"
    }
    def healthCheckStart = new Date()
    timeout(time: 10, unit: 'MINUTES') {  // health-check.sh有检测超时时间 timeout为防止shell脚本超时失效兼容处理
        healthCheckMsg = sh(
                script: "ssh  ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER}/ && ./health-check.sh ${healthCheckParams} '",
                returnStdout: true).trim()
    }
    healthCheckTimeDiff = Utils.getTimeDiff(healthCheckStart, new Date()) // 计算启动时间

    if ("${healthCheckMsg}".contains("成功")) {
        Tools.printColor(this, "${healthCheckMsg} ✅")
        dingNotice(1, "**成功 ✅**") // 钉钉成功通知
    } else if ("${healthCheckMsg}".contains("失败")) { // shell返回echo信息包含值
        isHealthCheckFail = true
        Tools.printColor(this, "${healthCheckMsg} ❌", "red")
        println("👉 健康检测失败原因分析: 首选排除CI服务器和应用服务器网络是否连通、应用服务器端口是否开放, 再查看应用服务启动日志是否失败")
        // 钉钉失败通知
        dingNotice(1, "**失败或超时❌** [点击我验证](${healthCheckUrl}) 👈 ", "${BUILD_USER_MOBILE}")
        // 打印应用服务启动失败日志 方便快速排查错误
        Tools.printColor(this, "------------ 应用服务${healthCheckUrl} 启动异常日志开始 START 👇 ------------", "red")
        sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'docker logs ${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}-${SHELL_ENV_MODE}' "
        Tools.printColor(this, "------------ 应用服务${healthCheckUrl} 启动异常日志结束 END 👆 ------------", "red")
        if ("${IS_ROLL_DEPLOY}" == 'true' || "${IS_BLUE_GREEN_DEPLOY}" == 'true') {
            println '分布式部署情况, 服务启动失败, 自动中止取消job, 防止继续部署导致其他应用服务挂掉 。'
            IS_ROLL_DEPLOY = false
        }
        IS_ARCHIVE = false // 不归档
        currentBuild.result = 'FAILURE' // 失败  不稳定UNSTABLE 取消ABORTED
        error("健康检测失败, 中止当前pipeline运行 ❌")
        return
    }
}

/**
 * 集成测试
 */
def integrationTesting() {
    // 可先动态传入数据库名称部署集成测试应用 启动测试完成销毁 再重新部署业务应用
    try {
        // 创建JMeter性能报告
        Tests.createJMeterReport(this)
        // 创建冒烟测试报告
        Tests.createSmokeReport(this)

        // 结合YApi接口管理做自动化API测试
        def yapiUrl = "http://yapi.panweijikeji.com"
        def testUrl = "${yapiUrl}/api/open/run_auto_test?${AUTO_TEST_PARAM}"
        // 执行接口测试
        def content = HttpRequest.get(this, "${testUrl}")
        def json = readJSON text: "${content}"
        def failedNum = "${json.message.failedNum}"
        def projectId = "${AUTO_TEST_PARAM}".trim().split("&")[2].split("=")[0].replaceAll("env_", "")
        def testCollectionId = "${AUTO_TEST_PARAM}".trim().split("&")[0].replaceAll("id=", "")
        DingTalk.notice(this, "${DING_TALK_CREDENTIALS_ID}", "自动化API集成测试报告 🙋",
                "#### ${json.message.msg} \n #### 测试报告: [查看结果](${testUrl.replace("mode=json", "mode=html")}) 🚨" +
                        "\n ##### 测试总耗时:  ${json.runTime} \n ##### 测试用例不完善也可导致不通过 👉[去完善](${yapiUrl}/project/${projectId}/interface/col/${testCollectionId})  ",
                "${failedNum}" == "0" ? "" : "${BUILD_USER_MOBILE}")
    } catch (e) {
        println "自动化集成测试失败 ❌"
        println e.getMessage()
    }
}

/**
 * 蓝绿部署
 */
def blueGreenDeploy() {
    // 蓝绿部署: 好处是只用一个主单点服务资源实现部署过程中不间断提供服务
    // 1、先启动部署一个临时服务将流量切到蓝服务器上  2、再部署真正提供服务的绿服务器  3、部署完绿服务器,销毁蓝服务器,将流量切回到绿服务器
    // 镜像容器名称
    dockerContainerName = "${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}-${SHELL_ENV_MODE}"
    // 先判断是否在一台服务器部署
    if ("${IS_SAME_SERVER}" == 'false') { // 不同服务器蓝绿部署
        def mainServerIp = remote.host // 主服务器IP
        def blueServerIp = ""  // 蓝服务器IP
        // 先部署一个零时服务将流量切到蓝服务器上
        if (remote_worker_ips.isEmpty()) {
            error("多机蓝绿部署, 请先在相关的Jenkinsfile配置从服务器ip数组remote_worker_ips参数 ❌")
        }
        // 循环串行执行多机分布式部署
        remote_worker_ips.each { ip ->
            println ip
            remote.host = ip
            blueServerIp = ip
            if (params.DEPLOY_MODE == GlobalVars.rollback) {
                uploadRemote("${archivePath}")
            } else {
                uploadRemote(Utils.getShEchoResult(this, "pwd"))
            }
            runProject()
            if (params.IS_HEALTH_CHECK == true) {
                MACHINE_TAG = "蓝机"
                healthCheck()
            }
        }
        // 再部署真正提供服务的绿服务器
        remote.host = mainServerIp
        runProject()
        if (params.IS_HEALTH_CHECK == true) {
            MACHINE_TAG = "绿机"
            healthCheck()
        }
        // 部署完绿服务器,销毁蓝服务器,将流量切回到绿服务器
        sh " ssh ${proxyJumpSSHText} ${remote.user}@${blueServerIp} ' docker stop ${dockerContainerName} --time=0 || true && docker rm ${dockerContainerName} || true ' "
        // 自动配置nginx负载均衡
        // Nginx.conf(this, "${mainServerIp}", "${SHELL_HOST_PORT}", "${blueServerIp}", "${SHELL_HOST_PORT}")
    } else if ("${IS_SAME_SERVER}" == 'true') {  // 单机蓝绿部署 适用于服务器资源有限 又要实现零停机随时部署发布 蓝绿部署只保持一份节点服务
        // 从服务宿主机Docker端口号  根据主服务器端口动态生成
        def workHostPort = Integer.parseInt(SHELL_HOST_PORT) - 1000
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) { // 同一台服务器主从部署情况 情况只针对后端项目
            def workShellParamsGetopts = "${SHELL_PARAMS_GETOPTS}".replace("-c ${SHELL_HOST_PORT}", "-c ${workHostPort}")
            // 先部署一个零时服务将流量切到蓝服务器上
            try {
                dokcerReleaseWorkerMsg = Utils.getShEchoResult(this, " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER} && ./${dockerReleaseWorkerShellName} '${workShellParamsGetopts}' ' ")
            } catch (error) {
                println error.getMessage()
                currentBuild.result = 'FAILURE'
                error("单机蓝绿部署从服务运行步骤出现异常 ❌")
            }
            if (params.IS_HEALTH_CHECK == true && !dokcerReleaseWorkerMsg.contains("跳过执行")) {
                try {
                    MACHINE_TAG = "蓝机"
                    healthCheckUrl = "http://${remote.host}:${workHostPort}/"
                    healthCheck(" -a ${PROJECT_TYPE} -b ${healthCheckUrl}")
                } catch (error) {
                    // 注意：这地方是使用的旧镜像部署，会导致一个问题，如果旧镜像本身就有问题，会导致部署失败，因为永远无法使用新镜像
                    println error.getMessage()
                    println("从服务器健康检测失败异常捕获, 因为可能是旧镜像导致, 继续部署主服务器 ❌")
                }
            }
            // 再部署真正提供服务的绿服务器
            runProject()
            if (params.IS_HEALTH_CHECK == true) {
                MACHINE_TAG = "绿机"
                healthCheck()
            }
            sleep(time: 2, unit: "SECONDS") // 暂停pipeline一段时间，单位为秒
            // 部署完绿服务器,销毁蓝服务器,将流量切回到绿服务器
            def workDockerContainerName = "${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}-worker-${SHELL_ENV_MODE}"
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} ' docker stop ${workDockerContainerName} --time=0 || true && docker rm ${workDockerContainerName} || true ' "
            // 自动配置nginx负载均衡
            // Nginx.conf(this, "${remote.host}", "${SHELL_HOST_PORT}", "${remote.host}", "${workHostPort}")
        }
    }
}

/**
 * 滚动部署
 */
def scrollToDeploy() {
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

            machineNum++
            MACHINE_TAG = "${machineNum}号机" // 动态计算是几号机
            if (params.DEPLOY_MODE == GlobalVars.rollback) {
                uploadRemote("${archivePath}")
            } else {
                // 如果配置多节点动态替换不同的配置文件重新执行maven构建打包或者直接替换部署服务器文件
                if ("${SOURCE_TARGET_CONFIG_DIR}".trim() != "" && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                    mavenBuildProject() // 需要mvn jdk构建环境
                }
                uploadRemote(Utils.getShEchoResult(this, "pwd"))
            }
            runProject()
            if (params.IS_HEALTH_CHECK == true) {
                healthCheck()
            }
        }
    } else if ("${IS_SAME_SERVER}" == 'true') {  // 单机滚动部署 适用于服务器资源有限 又要实现零停机随时部署发布
        // 从服务宿主机Docker端口号  根据主服务器端口动态生成
        def workHostPort = Integer.parseInt(SHELL_HOST_PORT) - 1000
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) { // 同一台服务器主从部署情况 情况只针对后端项目
            def workShellParamsGetopts = "${SHELL_PARAMS_GETOPTS}".replace("-c ${SHELL_HOST_PORT}", "-c ${workHostPort}")
            try {
                sleep(time: 2, unit: "SECONDS") // 暂停pipeline一段时间，单位为秒
                dokcerReleaseWorkerMsg = Utils.getShEchoResult(this, " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER} && ./${dockerReleaseWorkerShellName} '${workShellParamsGetopts}' ' ")
            } catch (error) {
                println error.getMessage()
                currentBuild.result = 'FAILURE'
                error("单机滚动部署运行步骤出现异常 ❌")
            }
            if (params.IS_HEALTH_CHECK == true && !dokcerReleaseWorkerMsg.contains("跳过执行")) {
                MACHINE_TAG = "2号机"
                healthCheckUrl = "http://${remote.host}:${workHostPort}/"
                healthCheck(" -a ${PROJECT_TYPE} -b ${healthCheckUrl}")
            }
        }
    }
}

/**
 * 基于Nginx Ingress 灰度发布  实现多版本并存 非强制用户更新提升用户体验
 */
def grayscaleDeploy() {

}

/**
 * 云原生K8S部署大规模集群
 */
def k8sDeploy() {
    // 执行部署
    Kubernetes.deploy(this)
}

/**
 *  Serverless工作流发布  无服务器架构免运维 只需要按照云函数的定义要求进行少量的声明或者配置
 */
def serverlessDeploy() {
    // K8s中的Knative或者结合公有云方案 实现Serverless无服务
}

/**
 * 自动设置免密连接 用于CI/CD服务器和应用部署服务器免密通信  避免手动批量设置繁琐重复劳动
 */
def autoSshLogin() {
    try {
        if ("${remote.user}".trim() == "" || "${remote.host}".trim() == "") {
            currentBuild.result = 'FAILURE'
            error("请配置部署服务器登录用户名或IP地址 ❌")
        }
        // 检测ssh免密连接是否成功
        sh "ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} exit"
    } catch (error) {
        println error.getMessage()
        if (error.getMessage().contains("255")) { // 0连接成功 255无法连接
            println "免密登录失败  根据hosts.txt文件已有的账号信息自动设置"
            try {
                // 目的是清除当前机器里关于远程服务器的缓存和公钥信息 如远程服务器已重新初始化情况 导致本地还有缓存
                // ECDSA host key "ip" for  has changed and you have requested strict checking 报错
                sh "ssh-keygen -R ${remote.host}"
            } catch (e) {
                println "清除当前机器里关于远程服务器的缓存和公钥信息失败"
                println e.getMessage()
            }
            dir("${env.WORKSPACE}/ci") {
                try {
                    // 执行免密登录脚本
                    sh " cd _linux && chmod +x auto-ssh.sh && ./auto-ssh.sh "
                } catch (e) {
                    println e.getMessage()
                }
            }
        }
    }
}

/**
 * 同步脚本和配置到部署服务器
 */
def syncScript() {
    try {
        // 自动创建服务器部署目录
        // ssh登录概率性失败 连接数超报错: kex_exchange_identification
        // 解决vim /etc/ssh/sshd_config中 MaxSessions与MaxStartups改大2000 默认10 重启生效 systemctl restart sshd.service
        sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'mkdir -p /${DEPLOY_FOLDER}/${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}' "
    } catch (error) {
        println "访问目标服务器失败, 首先检查jenkins服务器和应用服务器的ssh免密连接是否生效 ❌"
        println error.getMessage()
    }

    dir("${env.WORKSPACE}/ci") {
        try {
            // Docker多阶段镜像构建处理
            Docker.multiStageBuild(this, "${DOCKER_MULTISTAGE_BUILD_IMAGES}")
            // scp -r  递归复制整个目录 复制部署脚本和配置文件到服务器
            sh " chmod -R 777 .ci && scp ${proxyJumpSCPText} -r .ci/*  ${remote.user}@${remote.host}:/${DEPLOY_FOLDER}/ "
        } catch (error) {
            println "复制部署脚本和配置文件到服务器失败 ❌"
            println error.getMessage()
        }

        // 给shell脚本执行权限
        sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER} " +
                "&& chmod -R 777 web && chmod -R 777 go && chmod -R 777 python && chmod -R 777 cpp && chmod +x *.sh ' "
    }
}

/**
 * 是否存在CI代码
 */
def existCiCode() {
    if (!fileExists(".ci/Dockerfile")) {
        println "为保证先后顺序拉取代码 可能导致第一次构建时候无法找到CI仓库代码 重新拉取代码"
        pullCIRepo()
    }
}

/**
 * 部署运行之前操作
 */
def beforeRunProject() {
    // 多节点部署无感知不执行部署前通知
    if ("${IS_BEFORE_DEPLOY_NOTICE}" == 'true' && "${IS_ROLL_DEPLOY}" == 'false' && "${IS_BLUE_GREEN_DEPLOY}" == 'false') {
        // 部署之前通知
        dingNotice(2)
    }
    try {
        if ("${IS_GRACE_SHUTDOWN}" == 'true') {
            // Spring Boot优雅停机 curl几秒超时
            sh " curl --connect-timeout 3 --max-time 10  http://${remote.host}:${SHELL_HOST_PORT}/actuator/shutdown -X POST "
        }
    } catch (error) {
        println "服务已无法访问情况 优雅停机等出现异常捕获 继续部署流程"
        println error.getMessage()
    }
}

/**
 * 初始化Docker引擎环境 自动化第一次部署环境
 */
def initDocker() {
    try {
        // 判断服务器是是否安装docker环境
        sh "ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'docker version' "
    } catch (error) {
        println error.getMessage()
        dir("${env.WORKSPACE}/ci") {
            linuxType = Utils.getShEchoResult(this, "ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'lsb_release -a' ")
            // 判断linux主流发行版类型
            dockerFileName = ""
            if ("${linuxType}".contains("CentOS")) {
                println "CentOS系统"
                dockerFileName = "docker-install.sh"
            } else if ("${linuxType}".contains("Ubuntu")) {
                println "Ubuntu系统"
                dockerFileName = "docker-install-ubuntu.sh"
            } else {
                println "Linux系统: ${linuxType}"
                error("部署服务器非CentOS或Ubuntu系统类型 ❌")
            }
            // 上传docker初始化脚本
            sh " scp ${proxyJumpSCPText} -r ./_docker/${dockerFileName}  ${remote.user}@${remote.host}:/${DEPLOY_FOLDER}/ "
            // 给shell脚本执行权限
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'chmod +x /${DEPLOY_FOLDER}/${dockerFileName} ' "
            println "初始化Docker引擎环境  执行Docker初始化脚本"
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER} && ./${dockerFileName} ' "
        }
    }
}

/**
 * 回滚版本
 */
def rollbackVersion() {
    if ("${ROLLBACK_BUILD_ID}" == '0') { // 默认回滚到上一个版本
        ROLLBACK_BUILD_ID = "${Integer.parseInt(env.BUILD_ID) - 2}"
    }
    //input message: "是否确认回滚到构建ID为${ROLLBACK_BUILD_ID}的版本", ok: "确认"
    //该/var/jenkins_home/**路径只适合在master节点执行的项目 不适合slave节点的项目
    archivePath = "/var/jenkins_home/jobs/${env.JOB_NAME}/builds/${ROLLBACK_BUILD_ID}/archive/"
    uploadRemote("${archivePath}")
    runProject()
    if (params.IS_HEALTH_CHECK == true) {
        healthCheck()
    }
    if ("${IS_ROLL_DEPLOY}" == 'true') {
        scrollToDeploy()
    }
}

/**
 * 归档文件
 */
def archive() {
    try {
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            archiveArtifacts artifacts: "${npmPackageLocation}", onlyIfSuccessful: true
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
            archiveArtifacts artifacts: "${mavenPackageLocation}", onlyIfSuccessful: true
        }
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
        //if ("${IS_PROD}" != 'true') {
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            sh " rm -f ${npmPackageLocation} "
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
            sh " rm -f ${mavenPackageLocation} "
        }
        //}
    } catch (error) {
        println "删除打包产出物异常"
        println error.getMessage()
    }
}

/**
 * 生成二维码 方便手机端扫描
 */
def genQRCode() {
    if ("${IS_GEN_QR_CODE}" == 'true') { // 是否开启二维码生成功能
        try {
            imageSuffixName = "png"
            def imageName = "${PROJECT_NAME}"
            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                sh "rm -f *.${imageSuffixName}"
                QRCode.generate(this, "http://${remote.host}:${SHELL_HOST_PORT}", imageName)
                def sourceFile = "${env.WORKSPACE}/${imageName}.${imageSuffixName}" // 源文件
                def targetFile = "frontend/${env.JOB_NAME}/${env.BUILD_NUMBER}/${imageName}.${imageSuffixName}"
                // 目标文件
                qrCodeOssUrl = AliYunOss.upload(this, sourceFile, targetFile)
                println "${qrCodeOssUrl}"
            }
        } catch (error) {
            println " 生成二维码失败 ❌ "
            println error.getMessage()
        }
    }
}

/**
 * 制品仓库版本管理 如Maven、Npm、Docker等以及通用仓库版本上传 支持大型项目复杂依赖关系
 */
def productsWarehouse(map) {
    //  1. Maven与Gradle仓库  2. Npm仓库  3. Docker镜像仓库  4. 通用OSS仓库

    // Maven与Gradle制品仓库
    // Maven.uploadWarehouse(this)

    // Npm制品仓库
    // Node.uploadWarehouse(this)

    // Docker制品仓库
    // Docker.push(this)

    // 通用OSS制品仓库
    // AliYunOss.upload(this)

}

/**
 * 总会执行统一处理方法
 */
def alwaysPost() {
    // sh 'pwd'
    // cleanWs()  // 清空工作空间
    // Jenkins全局安全配置->标记格式器内设置Safe HTML支持html文本
    try {
        def releaseEnvironment = "${NPM_RUN_PARAMS != "" ? NPM_RUN_PARAMS : SHELL_ENV_MODE}"
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            currentBuild.description = "${IS_GEN_QR_CODE == 'true' ? "<img src=${qrCodeOssUrl} width=250 height=250 > <br/> " : ""}" +
                    "<a href='http://${remote.host}:${SHELL_HOST_PORT}'> 👉URL访问地址</a> " +
                    "<br/> 项目: ${PROJECT_NAME}" +
                    "${IS_PROD == 'true' ? "<br/> 版本: ${tagVersion}" : ""} " +
                    "<br/> 大小: ${webPackageSize} <br/> 分支: ${BRANCH_NAME} <br/> 环境: ${releaseEnvironment} <br/> 发布人: ${BUILD_USER}"
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
            currentBuild.description = "<a href='http://${remote.host}:${SHELL_HOST_PORT}'> 👉API访问地址</a> " +
                    "${javaOssUrl.trim() != '' ? "<br/><a href='${javaOssUrl}'> 👉直接下载构建${javaPackageType}包</a>" : ""}" +
                    "<br/> 项目: ${PROJECT_NAME}" +
                    "${IS_PROD == 'true' ? "<br/> 版本: ${tagVersion}" : ""} " +
                    "<br/> 大小: ${javaPackageSize} <br/> 分支: ${BRANCH_NAME} <br/> 环境: ${releaseEnvironment} <br/> 发布人: ${BUILD_USER}"
        }
    } catch (error) {
        println error.getMessage()
    }
}

/**
 * 生成tag和变更日志
 */
def gitTagLog() {
    // 未获取到参数 兼容处理 因为参数配置从代码拉取 必须先执行jenkins任务才能生效
    if (!params.IS_GIT_TAG && params.IS_GIT_TAG != false) {
        params.IS_GIT_TAG = true
    }
    // 构建成功后生产环境并发布类型自动打tag和变更记录  指定tag方式不再重新打tag
    if (params.IS_GIT_TAG == true && "${IS_PROD}" == 'true' && params.GIT_TAG == GlobalVars.noGit) {
        // 获取变更记录
        def gitChangeLog = changeLog.genChangeLog(this, 100)
        def latestTag = ""
        try {
            // 获取本地最新tag名称
            latestTag = Utils.getShEchoResult(this, "git describe --abbrev=0 --tags")
        } catch (error) {
            println "没有获取到最新的git tag标签"
            println error.getMessage()
        }
        // 生成语义化版本号
        tagVersion = Utils.genSemverVersion(latestTag, gitChangeLog.contains(GlobalVars.gitCommitFeature) ?
                GlobalVars.gitCommitFeature : GlobalVars.gitCommitFix)
        // 生成tag和变更日志
        gitTagLog.genTagAndLog(this, tagVersion, gitChangeLog, "${REPO_URL}", "${GIT_CREDENTIALS_ID}")
    }
    // 指定tag时候设置版本信息
    if (params.GIT_TAG != GlobalVars.noGit) {
        tagVersion = params.GIT_TAG
    }
}

/**
 * 同时构建部署多环境
 */
def deployMultiEnv() {
    currentBuild.result = "SUCCESS"
    if (params.IS_DEPLOY_MULTI_ENV == true) {
        // 注意流水线开启并发构建 会影响下一个嵌套任务运行
        jobDevEnv = build job: "${PROJECT_NAME}-dev"
        println jobDevEnv.getResult()
        jobTestEnv = build job: "${PROJECT_NAME}-test"
        println jobTestEnv.getResult()
    }
}

/**
 * 钉钉通知
 * @type 0 失败 1 部署完成 2 部署之前 3 变更记录
 * @msg 自定义消息* @atMobiles 要@的手机号
 */
def dingNotice(int type, msg = '', atMobiles = '') {
    if ("${params.IS_DING_NOTICE}" == 'true') { // 是否钉钉通知
        println("钉钉通知: " + params.NOTIFIER_PHONES)
        // 格式化持续时间
        def durationTimeString = "${currentBuild.durationString.replace(' and counting', '').replace('sec', 's')}".replace(' ', '')
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
        def projectTypeName = ""
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            projectTypeName = "前端"
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
            projectTypeName = "后端"
        }
        def envTypeMark = "内测版"  // 环境类型标志
        if ("${IS_PROD}" == 'true') {
            envTypeMark = "正式版"
        }
        def releaseEnvironment = "${NPM_RUN_PARAMS != "" ? NPM_RUN_PARAMS : SHELL_ENV_MODE}"
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
        } else if (type == 1) { // 部署完成
            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                // 生成二维码 方便手机端扫描
                genQRCode()
                dingtalk(
                        robot: "${DING_TALK_CREDENTIALS_ID}",
                        type: 'ACTION_CARD',
                        title: "CI/CD ${PROJECT_TAG}${envTypeMark}${projectTypeName}部署结果通知",
                        text: [
                                "![screenshot](${qrCodeOssUrl})",
                                "### [${env.JOB_NAME}#${env.BUILD_NUMBER} ${PROJECT_TAG}${envTypeMark}${projectTypeName} ${MACHINE_TAG}](${env.JOB_URL})",
                                "##### 版本信息",
                                "- Nginx Web服务启动${msg}",
                                "- 构建分支: ${BRANCH_NAME}   环境: ${releaseEnvironment}",
                                "- 构建版本: ${NODE_VERSION}   包大小: ${webPackageSize}",
                                "${monorepoProjectName}",
                                "###### ${rollbackTag}",
                                "###### 启动用时: ${healthCheckTimeDiff}   持续时间: ${durationTimeString}",
                                "###### 访问URL: [${healthCheckUrl}](${healthCheckUrl})",
                                "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL})",
                                "###### 发布人: ${BUILD_USER}  构建机器: ${NODE_LABELS}",
                                "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})"
                        ],
                        btns: [
                                [
                                        title    : "直接访问URL地址",
                                        actionUrl: "${healthCheckUrl}"
                                ]
                        ],
                        at: [isHealthCheckFail == true ? atMobiles : (notifierPhone == '110' ? '' : notifierPhone)]
                )
            } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
                def javaInfo = ""
                if ("${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                    javaInfo = "构建版本: JDK${JDK_VERSION}   包大小: ${javaPackageSize}"
                    if ("${javaOssUrl}".trim() != '') {
                        javaInfo = javaInfo + "\n [直接下载构建${javaPackageType}包](${javaOssUrl})  👈"
                    }
                }
                dingtalk(
                        robot: "${DING_TALK_CREDENTIALS_ID}",
                        type: 'MARKDOWN',
                        title: "CI/CD ${PROJECT_TAG}${envTypeMark}${projectTypeName}部署结果通知",
                        text: [
                                "### [${env.JOB_NAME}#${env.BUILD_NUMBER} ${PROJECT_TAG}${envTypeMark}${projectTypeName} ${MACHINE_TAG}](${env.JOB_URL})",
                                "#### · CI构建Docker部署完成 👌",
                                "#### · 服务端项目启动运行${msg}",
                                "###### ${rollbackTag}",
                                "###### 启动用时: ${healthCheckTimeDiff}   持续时间: ${durationTimeString}",
                                "###### 构建分支: ${BRANCH_NAME}   环境: ${releaseEnvironment}",
                                "###### ${javaInfo}",
                                "###### API地址: [${healthCheckUrl}](${healthCheckUrl})",
                                "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL})", // Sonar地址  [查看](http://182.92.126.7:9000/)
                                "###### 发布人: ${BUILD_USER}  构建机器: ${NODE_LABELS}",
                                "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})"
                        ],
                        at: [isHealthCheckFail == true ? atMobiles : (notifierPhone == '110' ? '' : notifierPhone)]
                )
            }
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
            if ("${IS_NOTICE_CHANGE_LOG}" == 'true') {
                def gitChangeLog = changeLog.genChangeLog(this, 10)
                if ("${gitChangeLog}" != GlobalVars.noChangeLog) {
                    def titlePrefix = "${PROJECT_TAG} BUILD#${env.BUILD_NUMBER}"
                    try {
                        if ("${tagVersion}") {
                            titlePrefix = "${PROJECT_TAG} ${tagVersion}"
                        }
                    } catch (e) {
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
}

