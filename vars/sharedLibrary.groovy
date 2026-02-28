#!groovy
import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*
import shared.library.devops.ChangeLog
import shared.library.devops.GitTagLog

/**
 * @author 潘维吉
 * @description 通用核心共享Pipeline脚本库
 * 针对大前端Web和服务端Java、Python、C++、Go等多语言项目
 */
def call(String type = 'web-java', Map map) {
    echo "Pipeline共享库脚本类型: ${type}, Jenkins分布式节点名: ${params.SELECT_BUILD_NODE} "
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

    if (type == "web-java") { // 针对标准项目
        pipeline {
            // 指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
            agent { label "${params.SELECT_BUILD_NODE} || any" }
            //agent { label "${PROJECT_TYPE.toInteger() == GlobalVars.frontEnd ? "${map.jenkins_node_frontend}" : "${map.jenkins_node}"}" }
            //agent any

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
                booleanParam(name: 'IS_CODE_QUALITY_ANALYSIS', defaultValue: false, description: "是否执行静态代码质量分析扫描检测并生成质量报告, 交付可读、易维护和安全的高质量代码 🔦 ")
                booleanParam(name: 'IS_WORKSPACE_CLEAN', defaultValue: false, description: "是否全部清空CI/CD工作空间 删除代码构建产物与缓存等 全新构建流水线工作环境 🛀 ")
                booleanParam(name: 'IS_HEALTH_CHECK', defaultValue: "${map.is_health_check}",
                        description: '是否执行服务启动健康探测  K8S使用默认的健康探测 🌡️')
                booleanParam(name: 'IS_GIT_TAG', defaultValue: "${map.is_git_tag}",
                        description: '是否在生产环境中自动给Git仓库设置Tag版本和生成CHANGELOG.md变更记录 📄')
                booleanParam(name: 'IS_DING_NOTICE', defaultValue: "${map.is_ding_notice}", description: "是否开启钉钉群通知 将构建成功失败等状态信息同步到群内所有人 📢 ")
                choice(name: 'NOTIFIER_PHONES', choices: "${contactPeoples}", description: '选择要通知的人 (钉钉群内@提醒发布结果) 📢 ')
                stashedFile(name: 'DEPLOY_PACKAGE', description: "请选择上传部署包文件、配置文件等 可不依赖源码情况下支持直接上传成品包部署方式和动态配置替换等 (如 *.jar、*.yaml、*.tar.gz 等格式) 🚀 ")
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
                // pollSCM('H/1 * * * *') // 每分钟判断一次代码是否存在变化 有变化就执行
                // cron('H 2 * * *')      // 每天几点执行
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
                // KUBECONFIG = credentials('kubernetes-cluster') // k8s集群凭证
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

                stage('人工审批') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return false
                        }
                    }
                    steps {
                        script {
                            manualApproval(map)
                        }
                    }
                }

                stage('代码质量') {
                    when {
                        beforeAgent true
                        // 生产环境不进行代码分析 缩减构建时间
                        //anyOf {
                        // branch 'develop'
                        // branch 'feature*'
                        // changelog '.*^\\[test\\] .+$' } // 匹配提交的 changeLog 决定是否执行
                        //}
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            // 是否进行代码质量分析  && fileExists("sonar-project.properties") == true 代码根目录配置sonar-project.properties文件才进行代码质量分析
                            return "${IS_CODE_QUALITY_ANALYSIS}" == 'true'
                        }
                    }
                    agent {
                        // label "node-3"  // 执行节点 分布式执行 可在不同服务上执行不同任务
                        /*   docker {
                               // sonarqube环境  构建完成自动删除容器
                               image "sonarqube:community"
                               reuseNode true // 使用根节点
                           }*/
                        docker {
                            // js、jvm、php、jvm-android、go、python、php。 jvm-community是免费版
                            image "jetbrains/${qodanaImagesName}:latest" // 设置镜像类型和版本号 latest
                            args " --entrypoint='' -v ${env.WORKSPACE}:/data/project/ -v ${env.WORKSPACE}/qodana-report/:/data/results/ -v $HOME/.m2/:/root/.m2/ "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        // 只显示当前阶段stage失败  而整个流水线构建显示成功
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            script {
                                // codeQualityAnalysis()
                                Qodana.analyse(this, map)
                            }
                        }
                    }
                }

                stage('JavaScript构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_SOURCE_CODE_DEPLOY == false && IS_DOCKER_BUILD == true && "${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) }
                    }
/*                    agent {
                        // label "linux"
                        *//* dockerfile {
                          filename 'Dockerfile.node-build' // 在WORKSPACE工作区代码目录
                          dir "${env.WORKSPACE}/ci"
                          // additionalBuildArgs  '--build-arg version=1.0.0'
                          // args " -v /${env.WORKSPACE}:/tmp "
                          reuseNode true  // 使用根节点 不设置会进入其它如@2代码工作目录
                      }*//*
                        docker {
                            // Node环境  构建完成自动删除容器
                            //image "node:${NODE_VERSION.replace('Node', '')}"
                            // 使用自定义Dockerfile的node环境 加速monorepo依赖构建内置lerna等相关依赖
                            image "panweiji/node:${NODE_VERSION.replace('Node', '')}" // 为了更通用应使用通用镜像  自定义镜像针对定制化需求
                            // args " -v ${"${env.WORKSPACE}/${GIT_PROJECT_FOLDER_NAME}"}/node_modules:/node_modules "
                            reuseNode true // 使用根节点
                        }
                    }*/
                    steps {
                        script {
                            // echo "Docker环境内Node构建方式"
                            /*   if ("${IS_PROD}" == 'true') {
                                   docker.image("panweiji/node:${NODE_VERSION.replace('Node', '')}").inside("") {
                                       nodeBuildProject(map)
                                   }
                               } else {*/ // 验证新特性
                            def nodeVersion = "${NODE_VERSION.replace('Node', '')}"
                            def dockerImageName = "panweiji/node-build"
                            def dockerImageTag = "${nodeVersion}"
                            def dockerParams = Docker.setDockerParameters(this);
                            Docker.buildDockerImage(this, map, "${env.WORKSPACE}/ci/Dockerfile.node-build",
                                    dockerImageName, dockerImageTag, "--build-arg NODE_VERSION=${nodeVersion}", false)
                            docker.image("${dockerImageName}:${dockerImageTag}").withRun(dockerParams) { c ->
                                docker.image("${dockerImageName}:${dockerImageTag}").inside("") {
                                    nodeBuildProject(map)
                                }
                            }
                            // }
                        }
                    }
                }
/*                stage('JavaScript构建') {
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
                            nodeBuildProject(map)
                        }
                    }
                }*/

                stage('Java构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_SOURCE_CODE_DEPLOY == false && IS_PACKAGE_DEPLOY == false
                                    && IS_DOCKER_BUILD == true && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd
                                    && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java)
                        }
                    }
                    /*      agent {
                              dockerfile {
                                  filename 'Dockerfile.maven-jdk' // 在WORKSPACE工作区代码目录
                                  label "panweiji/maven-jdk-${JDK_PUBLISHER}-${JDK_VERSION}:latest"
                                  dir "${env.WORKSPACE}/ci"
                                  additionalBuildArgs "--build-arg MVND_VERSION=${MVND_VERSION} --build-arg JDK_PUBLISHER=${JDK_PUBLISHER} --build-arg JDK_VERSION=${JDK_VERSION}"
                                  args " -v /var/cache/maven/.m2:/root/.m2  "
                                  reuseNode true  // 使用根节点 不设置会进入其它如@2代码工作目录
                              }
                              docker {
                                  // JDK MAVEN 环境  构建完成自动删除容器  graalvm使用csanchez/maven镜像  容器仓库：https://hub.docker.com/_/maven/
                                  image "${mavenDockerName}:${map.maven.replace('Maven', '')}-${JDK_PUBLISHER}-${JDK_VERSION}"
                                  args " -v /var/cache/maven/.m2:/root/.m2 "
                                  reuseNode true // 使用根节点
                              }
                          }*/
                    steps {
                        script {
                            def dockerParams = Docker.setDockerParameters(this);
                            // Gradle构建方式
                            if (IS_GRADLE_BUILD == true) {
                                def gradleVersion = "${GRADLE_VERSION}" // Gradle版本 要动态配置
                                def jdkVersion = "${JDK_VERSION}"
                                def dockerImageName = "gradle"
                                def dockerImageTag = "$gradleVersion-jdk$jdkVersion"
                                docker.image("${dockerImageName}:${dockerImageTag}").withRun(dockerParams) { c ->
                                    docker.image("${dockerImageName}:${dockerImageTag}").inside("-v $HOME/.gradle:/root/.gradle -v $HOME/.gradle:/home/gradle/.gradle") {
                                        gradleBuildProject(map)
                                    }
                                }
                            } else {
                                if (("${JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringBoot || "${JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.Quarkus)
                                        && "${JDK_VERSION}".toInteger() >= 11 && "${IS_SPRING_NATIVE}" == "false") {
                                    // mvnd支持条件
                                    def mvndVersion = "${MVND_VERSION}"  // Mvnd版本 要动态配置
                                    def jdkVersion = "${JDK_VERSION}"
                                    def dockerImageName = "panweiji/maven-jdk"
                                    def dockerImageTag = "${mvndVersion}-${jdkVersion}"
                                    Docker.buildDockerImage(this, map, "${env.WORKSPACE}/ci/Dockerfile.maven-jdk",
                                            dockerImageName, dockerImageTag, "--build-arg MVND_VERSION=${mvndVersion} --build-arg JDK_VERSION=${jdkVersion}", false)
                                    docker.image("${dockerImageName}:${dockerImageTag}").withRun(dockerParams) { c ->
                                        docker.image("${dockerImageName}:${dockerImageTag}").inside("-v /var/cache/maven/.m2:/root/.m2") {
                                            mavenBuildProject(map, 0, "mvnd")
                                        }
                                    }
                                } else {
                                    def dockerImageNameAndTag = "${mavenDockerName}:${map.maven.replace('Maven', '')}-${JDK_PUBLISHER}-${JDK_VERSION}"
                                    docker.image("${dockerImageNameAndTag}").withRun(dockerParams) { c ->
                                        docker.image("${dockerImageNameAndTag}").inside("-v /var/cache/maven/.m2:/root/.m2") {
                                            mavenBuildProject(map)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
/*                stage('Java构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == false && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) }
                    }
                    tools {
                        // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中  如果有node节点 工具位置也要配置HOME路径
                        maven "${map.maven}"
                        jdk "${JDK_VERSION}"  // JDK高版本构建环境可以打包低版本代码项目
                    }
                    steps {
                        script {
                            mavenBuildProject(map)
                        }
                    }
                }*/

                stage('Python构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_SOURCE_CODE_DEPLOY == false && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) }
                    }
                    steps {
                        script {
                            /*   if (IS_DOCKER_BUILD == true) {
                                   // Python需要交叉编译 不同系统部署使用不同系统环境打包 如Windows使用 cdrx/pyinstaller-windows
                                   docker.image("cdrx/pyinstaller-linux:python3").inside {
                                       pythonBuildProject(map)
                                   }
                               } else {*/
                            pythonBuildProject(map)
                            //  }
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
                    steps {
                        script {
                            buildImage(map)
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
                            return (params.IS_HEALTH_CHECK == true && IS_BLUE_GREEN_DEPLOY == false && IS_K8S_DEPLOY == false)
                        }
                    }
                    steps {
                        script {
                            healthCheck(map)
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
                    steps {
                        script {
                            integrationTesting(map)
                        }
                    }
                }

                stage('蓝绿部署') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_BLUE_GREEN_DEPLOY == true)  // 是否进行蓝绿部署
                        }
                    }
                    steps {
                        script {
                            // 蓝绿部署是实现零停机部署最经济的方式 只有单个服务长期占用资源
                            blueGreenDeploy(map)
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

                stage('灰度发布') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_K8S_DEPLOY == true && IS_CANARY_DEPLOY == true) // 是否进行灰度发布
                        }
                    }
                    agent {
                        docker {
                            //   构建完成自动删除容器
                            image "panweiji/k8s:latest"
                            // args " "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            // 灰度发布  金丝雀发布  A/B测试  基于Nginx Ingress 灰度发布  实现多版本共存 非强制更新提升用户体验
                            grayscaleDeploy(map)
                        }
                    }
                }

                stage('Kubernetes云原生') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_K8S_DEPLOY == true && IS_CANARY_DEPLOY == false)  // 是否进行云原生K8S集群部署
                        }
                    }
                    agent { // agent语法文档： https://www.jenkins.io/doc/book/pipeline/syntax/#agent
                        /*   dockerfile {
                              filename 'Dockerfile.k8s' // 在WORKSPACE工作区代码目录
                              dir "${env.WORKSPACE}/ci"
                              // additionalBuildArgs  '--build-arg version=1.0.0'
                              // args " -v /${env.WORKSPACE}:/tmp "
                              reuseNode true  // 使用根节点 不设置会进入其它如@2代码工作目录
                          } */
                        docker {
                            //   构建完成自动删除容器
                            image "panweiji/k8s:latest"
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

                stage('Serverless工作流') {
                    when {
                        beforeAgent true
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

                stage('消息通知') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return true }
                    }
                    steps {
                        script {
                            if ("${params.IS_DING_NOTICE}" == 'true' && params.IS_HEALTH_CHECK == false) {
                                dingNotice(map, 1, "**成功 ✅**")
                            }
                        }
                    }
                }

                stage('发布日志') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                    }
                    steps {
                        script {
                            // 自动打tag和生成CHANGELOG.md文件
                            // docker.image("bitnami/git:latest").inside(" --entrypoint='' ") { // 因使用了Git高级特性 所以需确保最新版本
                            gitTagLog()
                            // }
                            // 钉钉通知变更记录
                            dingNotice(map, 3)
                        }
                    }
                }

                stage('K8s/Docker回滚 启动 停止 重启等') {
                    when {
                        beforeAgent true
                        expression {
                            return ("${GlobalVars.rollback}" == "${params.DEPLOY_MODE}" || "${GlobalVars.start}" == "${params.DEPLOY_MODE}" || "${GlobalVars.stop}" == "${params.DEPLOY_MODE}"
                                    || "${GlobalVars.destroy}" == "${params.DEPLOY_MODE}" || "${GlobalVars.restart}" == "${params.DEPLOY_MODE}")
                        }
                    }
                    steps {
                        script {
                            controlService(map)
                        }
                    }
                }

                stage('制品仓库') {
                    when {
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

                stage('智能运维') {
                    when {
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
                        currentBuild.result = 'SUCCESS'  // 显式设置构建结果
                        deletePackagedOutput()
                    }
                }
                failure {
                    script {
                        echo '当前失败时才运行'
                        dingNotice(map, 0, "CI/CD流水线失败 ❌")
                        // AI人工智能分析错误日志帮助人类解释与理解 需要大模型api key支持 插件: Explain Error Plugin
                        // explainError()
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

    } else if (type == "web-java-2") {  // 同类型流水线不同阶段判断执行  但差异性较大的Pipeline建议区分groovy文件维护

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
    REPO_URL = jsonParams.REPO_URL ? jsonParams.REPO_URL.trim() : "" // Git源码地址 需要包含.git后缀
    BRANCH_NAME = jsonParams.BRANCH_NAME ? jsonParams.BRANCH_NAME.trim() : GlobalVars.defaultBranch  // Git默认分支
    PROJECT_TYPE = jsonParams.PROJECT_TYPE ? jsonParams.PROJECT_TYPE.trim() : ""  // 项目类型 1 前端项目 2 后端项目
    // 计算机语言类型 1. Java  2. Go  3. Python  5. C++  6. JavaScript
    COMPUTER_LANGUAGE = jsonParams.COMPUTER_LANGUAGE ? jsonParams.COMPUTER_LANGUAGE.trim() : "1"
    // 项目名 代码位置或构建模块名等
    PROJECT_NAME = jsonParams.PROJECT_NAME ? jsonParams.PROJECT_NAME.trim() : ""
    // shell传入前端或后端组合参数 包括名称、类型、多端口、环境等
    SHELL_PARAMS = jsonParams.SHELL_PARAMS ? jsonParams.SHELL_PARAMS.trim() : ""
    // 分布式部署独立扩展服务器 基于通用配置的基础上 再扩展的服务器IP集合 逗号分割
    EXPAND_SERVER_IPS = jsonParams.EXPAND_SERVER_IPS ? jsonParams.EXPAND_SERVER_IPS.trim() : ""

    JDK_VERSION = jsonParams.JDK_VERSION ? jsonParams.JDK_VERSION.trim() : "${map.jdk}" // 自定义JDK版本
    JDK_PUBLISHER = jsonParams.JDK_PUBLISHER ? jsonParams.JDK_PUBLISHER.trim() : "${map.jdk_publisher}" // JDK版本发行商
    NODE_VERSION = jsonParams.NODE_VERSION ? jsonParams.NODE_VERSION.trim() : "${map.nodejs}" // 自定义Node版本
    TOMCAT_VERSION = jsonParams.TOMCAT_VERSION ? jsonParams.TOMCAT_VERSION.trim() : "7.0" // 自定义非内嵌的Tomcat老版本
    MVND_VERSION = jsonParams.MVND_VERSION ? jsonParams.MVND_VERSION.trim() : "1.0.3" // 自定义mvnd版本
    GRADLE_VERSION = jsonParams.GRADLE_VERSION ? jsonParams.GRADLE_VERSION.trim() : "9" // 自定义Gradle版本
    // npm包管理工具类型 如:  npm、yarn、pnpm
    NPM_PACKAGE_TYPE = jsonParams.NPM_PACKAGE_TYPE ? jsonParams.NPM_PACKAGE_TYPE.trim() : "pnpm"
    NPM_RUN_PARAMS = jsonParams.NPM_RUN_PARAMS ? jsonParams.NPM_RUN_PARAMS.trim() : "" // npm run [build]的前端项目参数
    // 如果Maven模块化存在二级模块目录 设置一级模块目录名称
    MAVEN_ONE_LEVEL = jsonParams.MAVEN_ONE_LEVEL ? jsonParams.MAVEN_ONE_LEVEL.trim() : "${map.maven_one_level}"

    IS_MONO_REPO = jsonParams.IS_MONO_REPO ? jsonParams.IS_MONO_REPO : false // 是否MonoRepo单体式仓库  单仓多包
    // 是否Maven单模块代码
    IS_MAVEN_SINGLE_MODULE = jsonParams.IS_MAVEN_SINGLE_MODULE ? jsonParams.IS_MAVEN_SINGLE_MODULE : false
    // 是否执行Maven单元测试
    IS_RUN_MAVEN_TEST = jsonParams.IS_RUN_MAVEN_TEST ? jsonParams.IS_RUN_MAVEN_TEST : false
    // 是否使用Docker容器环境方式构建打包 false使用宿主机环境
    IS_DOCKER_BUILD = jsonParams.IS_DOCKER_BUILD == "false" ? false : true
    // 是否开启Docker多架构CPU构建支持
    IS_DOCKER_BUILD_MULTI_PLATFORM = jsonParams.IS_DOCKER_BUILD_MULTI_PLATFORM ? jsonParams.IS_DOCKER_BUILD_MULTI_PLATFORM : false
    IS_BLUE_GREEN_DEPLOY = jsonParams.IS_BLUE_GREEN_DEPLOY ? jsonParams.IS_BLUE_GREEN_DEPLOY : false // 是否蓝绿部署
    IS_ROLL_DEPLOY = jsonParams.IS_ROLL_DEPLOY ? jsonParams.IS_ROLL_DEPLOY : false // 是否滚动部署
    // 是否灰度发布  金丝雀发布  A/B测试
    IS_CANARY_DEPLOY = jsonParams.IS_CANARY_DEPLOY ? jsonParams.IS_CANARY_DEPLOY : params.IS_CANARY_DEPLOY
    IS_K8S_DEPLOY = jsonParams.IS_K8S_DEPLOY ? jsonParams.IS_K8S_DEPLOY : false // 是否K8S集群部署
    IS_SERVERLESS_DEPLOY = jsonParams.IS_SERVERLESS_DEPLOY ? jsonParams.IS_SERVERLESS_DEPLOY : false
    // 是否Serverless发布
    IS_STATIC_RESOURCE = jsonParams.IS_STATIC_RESOURCE ? jsonParams.IS_STATIC_RESOURCE : false // 是否静态web资源
    IS_UPLOAD_OSS = jsonParams.IS_UPLOAD_OSS ? jsonParams.IS_UPLOAD_OSS : false // 是否构建产物上传到OSS
    // K8s集群业务应用是否使用Session 做亲和度关联
    IS_USE_SESSION = jsonParams.IS_USE_SESSION ? jsonParams.IS_USE_SESSION : false
    // 是否是NextJs服务端React框架
    IS_NEXT_JS = jsonParams.IS_NEXT_JS ? jsonParams.IS_NEXT_JS : false
    // 服务器部署时不同机器的代码配置是否不相同
    IS_DIFF_CONF_IN_DIFF_MACHINES = jsonParams.IS_DIFF_CONF_IN_DIFF_MACHINES ? jsonParams.IS_DIFF_CONF_IN_DIFF_MACHINES : false
    // 是否开启K8S自动水平弹性扩缩容
    IS_K8S_AUTO_SCALING = jsonParams.IS_K8S_AUTO_SCALING ? jsonParams.IS_K8S_AUTO_SCALING : false
    // 是否禁用K8S健康探测
    IS_DISABLE_K8S_HEALTH_CHECK = jsonParams.IS_DISABLE_K8S_HEALTH_CHECK ? jsonParams.IS_DISABLE_K8S_HEALTH_CHECK : false
    // 是否开启Spring Native原生镜像 显著提升性能同时降低资源使用
    IS_SPRING_NATIVE = jsonParams.IS_SPRING_NATIVE ? jsonParams.IS_SPRING_NATIVE : false
    // 是否进行代码质量分析的开关
    IS_CODE_QUALITY_ANALYSIS = jsonParams.IS_CODE_QUALITY_ANALYSIS ? jsonParams.IS_CODE_QUALITY_ANALYSIS : params.IS_CODE_QUALITY_ANALYSIS
    // 是否进集成测试
    IS_INTEGRATION_TESTING = jsonParams.IS_INTEGRATION_TESTING ? jsonParams.IS_INTEGRATION_TESTING : false
    // 是否直接源码部署 无需打包 自定义命令启动
    IS_SOURCE_CODE_DEPLOY = jsonParams.IS_SOURCE_CODE_DEPLOY ? jsonParams.IS_SOURCE_CODE_DEPLOY : false
    // 是否只依赖源码和自定义命令部署方式
    IS_CODE_AND_COMMAND_DEPLOY = jsonParams.IS_CODE_AND_COMMAND_DEPLOY ? jsonParams.IS_CODE_AND_COMMAND_DEPLOY : false
    // 是否直接构建包部署方式  如无源码的情况
    IS_PACKAGE_DEPLOY = jsonParams.IS_PACKAGE_DEPLOY ? jsonParams.IS_PACKAGE_DEPLOY : false
    // 是否使用Gradle构建方式
    IS_GRADLE_BUILD = jsonParams.IS_GRADLE_BUILD ? jsonParams.IS_GRADLE_BUILD : false

    // 设置monorepo单体仓库主包文件夹名
    MONO_REPO_MAIN_PACKAGE = jsonParams.MONO_REPO_MAIN_PACKAGE ? jsonParams.MONO_REPO_MAIN_PACKAGE.trim() : "projects"
    AUTO_TEST_PARAM = jsonParams.AUTO_TEST_PARAM ? jsonParams.AUTO_TEST_PARAM.trim() : ""  // 自动化集成测试参数
    // Java框架类型 1. Spring Boot  2. Spring MVC 3. Quarkus
    JAVA_FRAMEWORK_TYPE = jsonParams.JAVA_FRAMEWORK_TYPE ? jsonParams.JAVA_FRAMEWORK_TYPE.trim() : "1"
    // 自定义Docker挂载映射 docker run -v 参数(格式 宿主机挂载路径:容器内目标路径)  多个用逗号,分割
    DOCKER_VOLUME_MOUNT = jsonParams.DOCKER_VOLUME_MOUNT ? jsonParams.DOCKER_VOLUME_MOUNT.trim() : "${map.docker_volume_mount}".trim()
    // 自定义特殊化的Nginx配置文件在项目源码中的路径  用于替换CI仓库的config默认标准配置文件
    CUSTOM_NGINX_CONFIG = jsonParams.CUSTOM_NGINX_CONFIG ? jsonParams.CUSTOM_NGINX_CONFIG.trim() : ""
    // 不同部署节点动态批量替换多个环境配置文件 源文件目录 目标文件目录 逗号,分割  如 resources/config,resources
    SOURCE_TARGET_CONFIG_DIR = jsonParams.SOURCE_TARGET_CONFIG_DIR ? jsonParams.SOURCE_TARGET_CONFIG_DIR.trim() : ""
    // 不同项目通过文件目录区分放在相同的仓库中 设置Git代码项目文件夹名称 用于找到相关应用源码
    GIT_PROJECT_FOLDER_NAME = jsonParams.GIT_PROJECT_FOLDER_NAME ? jsonParams.GIT_PROJECT_FOLDER_NAME.trim() : ""
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
    // 自定义Maven打包命令参数
    CUSTOM_MAVEN_PACKAGE_COMMAND = jsonParams.CUSTOM_MAVEN_PACKAGE_COMMAND ? jsonParams.CUSTOM_MAVEN_PACKAGE_COMMAND.trim() : ""
    // 自定义部署Dockerfile名称 如 Dockerfile.xxx
    CUSTOM_DOCKERFILE_NAME = jsonParams.CUSTOM_DOCKERFILE_NAME ? jsonParams.CUSTOM_DOCKERFILE_NAME.trim() : "Dockerfile"
    // 自定义Python版本
    CUSTOM_PYTHON_VERSION = jsonParams.CUSTOM_PYTHON_VERSION ? jsonParams.CUSTOM_PYTHON_VERSION.trim() : "3.14.0"
    // 自定义Python启动文件名称 默认app.py文件
    CUSTOM_PYTHON_START_FILE = jsonParams.CUSTOM_PYTHON_START_FILE ? jsonParams.CUSTOM_PYTHON_START_FILE.trim() : "app.py"
    // 自定义服务部署启动命令
    CUSTOM_STARTUP_COMMAND = jsonParams.CUSTOM_STARTUP_COMMAND ? jsonParams.CUSTOM_STARTUP_COMMAND.trim() : ""
    // 自定义服务部署安装包 多个空格分隔
    CUSTOM_INSTALL_PACKAGES = jsonParams.CUSTOM_INSTALL_PACKAGES ? jsonParams.CUSTOM_INSTALL_PACKAGES.trim() : ""

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

    // 未来可独立拆分成不同参数传入 更易于理解和维护
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

    // Maven Docker构建镜像名称
    mavenDockerName = "maven"
    if ("${IS_SPRING_NATIVE}" == "true") {
        mavenDockerName = "csanchez/maven"  // 支持graalvm的maven版本
        JDK_PUBLISHER = "graalvm-community" // 支持graalvm的jdk版本
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

    // Git Tag版本变量定义
    tagVersion = ""
    // 扫描二维码地址
    qrCodeOssUrl = ""
    // Java构建包OSS地址Url
    javaOssUrl = ""
    // Java打包类型 jar、war
    javaPackageType = ""
    // 构建包大小
    buildPackageSize = ""
    // 构建打包后产物的位置
    buildPackageLocation = ""
    // 是否健康探测失败状态
    isHealthCheckFail = false
    // 计算应用启动时间
    healthCheckTimeDiff = "未知"
    // Qodana代码质量准备不同语言的镜像名称
    qodanaImagesName = ""
    // 源码部署的打包文件名称
    sourceCodeDeployName = "source-code"
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
        if ("${IS_SOURCE_CODE_DEPLOY}" == 'true') {
            NPM_PACKAGE_FOLDER = "${sourceCodeDeployName}"
        }
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
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -q ${JAVA_FRAMEWORK_TYPE} -r ${TOMCAT_VERSION} -s ${jdkPublisher} -t ${IS_SPRING_NATIVE} -u ${IS_SOURCE_CODE_DEPLOY} "
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
        if ("${CUSTOM_STARTUP_COMMAND}" != "") {
            // 处理shell无法传递空格问题
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -v " + "${CUSTOM_STARTUP_COMMAND}".replaceAll(" ", "#")
        }
        println "${SHELL_PARAMS_GETOPTS}"
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
        addBadge(id: "auto-trigger-badge", text: "自动触发", color: 'purple', cssClass: 'badge-text--background')
    } else {
        wrap([$class: 'BuildUser']) {
            try {
                BUILD_USER = env.BUILD_USER
                // BUILD_USER_EMAIL = env.BUILD_USER_EMAIL
                // 获取钉钉插件手机号 注意需要系统设置里in-process script approval允许权限
                def user = hudson.model.User.getById(env.BUILD_USER_ID, false).getProperty(io.jenkins.plugins.DingTalkUserProperty.class)
                BUILD_USER_MOBILE = user.mobile  // 用记号用于群@提醒
                if (user.mobile == null || "${user.mobile}".trim() == "") {
                    BUILD_USER_MOBILE = env.BUILD_USER // 未填写钉钉插件手机号则使用用户名代替显示
                }
            } catch (error) {
                println "获取账号部分信息失败"
                println error.getMessage()
            }
        }
    }

    // 构建过程中徽章展示信息
    addInfoBadge(id: "launch-badge", icon: 'symbol-rocket plugin-ionicons-api', text: "${BUILD_USER}同学 正在为你加速构建部署${SHELL_ENV_MODE}环境 ...")
}

/**
 * 获取项目代码
 */
def pullProjectCode() {
    // 直接构建包部署方式
    packageDeploy()
    if (IS_PACKAGE_DEPLOY == true) {
        return  // 终止后续阶段执行 比如拉取项目代码 因为直接是包部署方式 不需要源码
    }

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
        // sh "git --version"  // 建议使用git 2.0以上的高级版本  否则可能有兼容性问题
        // sh "which git"
        // https仓库下载报错处理 The certificate issuer's certificate has expired.  Check your system date and time.
        sh "git config --global http.sslVerify false || true"
        // def git = git url: "${REPO_URL}", branch: "${BRANCH_NAME}", credentialsId: "${GIT_CREDENTIALS_ID}"
        // println "${git}"

        // 在node节点工具位置选项配置 which git的路径 才能拉取代码!!!
        // 对于大体积仓库或网络不好情况 自定义代码下载超时时间
        checkout([$class           : 'GitSCM', // 其它代码版本工具 MercurialSCM、SubversionSCM
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

    // 无构建部署 源码直接部署方式
    sourceCodeDeploy()

    // 当前job是否有代码变更记录并提醒
    JenkinsCI.getNoChangeLogAndTip(this)
}

/**
 * 获取CI代码库
 */
def pullCIRepo() {
    // 同步部署脚本和配置文件等
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
 * 直接构建包部署方式  如无源码的情况
 * 无需打包 只需要包上传到服务器上执行自定义命令启动
 */
def packageDeploy() {
    // 参数化上传或者Git仓库下载或从http地址下载包
    try { // 是否存在声明
        if ("${IS_SOURCE_CODE_DEPLOY}" != 'true') {
            println("上传文件中: ${DEPLOY_PACKAGE_FILENAME}")
            unstash 'DEPLOY_PACKAGE' // 获取文件 上传到具体job根目录下和源码同级结构
            // sh 'cat DEPLOY_PACKAGE'
            // 文件恢复原始文件名称  原始文件名称是 定义变量名称+ _FILENAME 固定后缀组合
            // 文件恢复原始文件名称  原始文件名称是 定义变量名称+ _FILENAME 固定后缀组合
            sh 'mv DEPLOY_PACKAGE $DEPLOY_PACKAGE_FILENAME'
            Tools.printColor(this, "${DEPLOY_PACKAGE_FILENAME} 文件上传成功 ✅")
            buildPackageSize = Utils.getFileSize(this, "${DEPLOY_PACKAGE_FILENAME}")
            IS_PACKAGE_DEPLOY = true
            // 统一部署文件名称 SSH传输包到部署服务器
        }
    } catch (error) {
        // 如果是必须上传文件的job任务 构建后报错提醒 或者构建先input提醒
    }
    // 如果直接包部署方式 后面流程不需要打包 也不再依赖Git仓库
}

/**
 * 无构建部署 源码直接部署方式
 * 无需打包 只需要压缩上传到服务器上执行自定义命令启动
 */
def sourceCodeDeploy() {
    if ("${IS_SOURCE_CODE_DEPLOY}" == 'true') {
        dir("${env.WORKSPACE}/") { // 源码在特定目录下
            def tarFile = "${sourceCodeDeployName}.tar.gz"
            sh " rm -f ${tarFile} && rm -f DEPLOY_PACKAGE && " +
                    " tar --warning=no-file-changed -zcvf  ${tarFile} --exclude='.git' --exclude='ci*' --exclude='*.log' --exclude='*.tar.gz' ./${GIT_PROJECT_FOLDER_NAME} "
            buildPackageSize = Utils.getFileSize(this, "${tarFile}")
            Tools.printColor(this, "源码压缩打包成功 ✅")
            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                // 替换自定义的nginx配置文件
                Deploy.replaceNginxConfig(this)
            }
        }
    }
}

/**
 * 代码质量分析
 */
def codeQualityAnalysis() {
    // 创建项目
    SonarQube.createProject(this, "${FULL_PROJECT_NAME}")
    // 扫描项目
    // SonarQube.scan(this, "${FULL_PROJECT_NAME}")
    // SonarQube.getStatus(this, "${PROJECT_NAME}")
/*    def scannerHome = tool 'SonarQube' // 工具名称
    withSonarQubeEnv('SonarQubeServer') { // 服务地址链接名称
        // 如果配置了多个全局服务器连接，则可以指定其名称
        sh "${scannerHome}/bin/sonar-scanner"
        // sh "/usr/local/bin/sonar-scanner --version"
    }*/
    // 可自动提交自动修复PR代码或打通项目管理平台自动提交bug指派任务
}

/**
 * Node编译构建
 */
def nodeBuildProject(map) {
    dir("${env.WORKSPACE}/${GIT_PROJECT_FOLDER_NAME}") { // 源码在特定目录下
        monoRepoProjectDir = "" // monorepo项目所在目录 默认根目录
        if ("${IS_MONO_REPO}" == 'true') {  // 是否MonoRepo单体式仓库  单仓多包
            monoRepoProjectDir = "${MONO_REPO_MAIN_PACKAGE}/${PROJECT_NAME}"
        }

        if ("${IS_STATIC_RESOURCE}" == 'true') { // 静态资源项目
            if ("${IS_MONO_REPO}" == 'true') {  // 是否MonoRepo单体式仓库  单仓多包
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
            if ("${IS_MONO_REPO}" == 'true') {  // 是否MonoRepo单体式仓库  单仓多包
                // 基于Lerna管理的Monorepo仓库打包
                Web.monorepoBuild(this)
            } else {
                if ("${IS_NEED_SASS}" == 'true') { // 是否需要css预处理器sass  兼容老项目老代码
                    // 是否需要css预处理器sass处理
                    Web.needSass(this)
                }

                timeout(time: 45, unit: 'MINUTES') {
                    try {
                        def retryCount = 0 // 重试次数初始值
                        retry(3) {
                            retryCount++
                            if (retryCount >= 2) { // 第一次构建不处理
                                sh "rm -rf node_modules && rm -f *lock*"
                                // 如果包404下载失败  可以更换官方镜像源重新下载
                                // Node.setOfficialMirror(this)
                            }
                            if (Git.isExistsChangeFile(this) || retryCount >= 2) {
                                // 自动判断是否需要下载依赖  根据依赖配置文件在Git代码是否变化
                                println("安装依赖 📥")
                                // npm ci 与 npm install类似 进行CI/CD或生产发布时，最好使用npm ci 防止版本号错乱但依赖lock文件
                                sh " ${NPM_PACKAGE_TYPE} install || npm install || pnpm install || npm ci || yarn install "
                                // --prefer-offline &> /dev/null 加速安装速度 优先离线获取包不打印日志 但有兼容性问题
                            }

                            println("执行Node构建 🏗️  ")
                            sh " rm -rf ${NPM_PACKAGE_FOLDER} || true "
                            sh " pwd && npm run '${NPM_RUN_PARAMS}' "
                        }
                    } catch (e) {
                        println(e.getMessage())
                        sh "rm -rf node_modules && rm -f *lock*"
                        error("Web打包失败, 终止当前Pipeline运行 ❌")
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
 * Maven编译构建
 */
def mavenBuildProject(map, deployNum = 0, mavenType = "mvn") {
    def mavenCommandType = mavenType // 构建引擎类型
    if (IS_DOCKER_BUILD == false) { // 宿主机环境情况
        // 动态切换Maven内的对应的JDK版本
        Java.switchJDKByJenv(this, "${JDK_VERSION}")
    }
    sh "mvn --version" // 打印Maven与JDK版本用于调试
    sh "maven --version" // 打印Maven与JDK版本用于调试
    sh "${mavenCommandType} --version" // 打印Maven与JDK版本用于调试
    sh " which sh "
    dir("${env.WORKSPACE}/${GIT_PROJECT_FOLDER_NAME}") { // 源码在特定目录下
        println("执行3 ")
        // 自动替换不同分布式部署节点的环境文件  deployNum部署节点数
        Deploy.replaceEnvFile(this, deployNum)
        // maven如果存在多级目录 一级目录设置
        MAVEN_ONE_LEVEL = "${MAVEN_ONE_LEVEL}".trim() != "" ? "${MAVEN_ONE_LEVEL}/" : "${MAVEN_ONE_LEVEL}".trim()
        println("执行Maven构建 🏗️  ")
        def isMavenTest = "${IS_RUN_MAVEN_TEST}" == "true" ? "" : "-Dmaven.test.skip=true"  // 是否Maven单元测试
        def isMavenProfile = " " // 基于Maven Profile方式动态添加依赖包和插件 设置Profile ID值 如 -P package
        timeout(time: 45, unit: 'MINUTES') { // 超时终止防止非正常构建情况 长时间占用资源
            retry(2) {
                // 对于Spring Boot 3.x及Spring Native与GaalVM集成的项目，通过以下命令来构建原生镜像  特性：性能明显提升 使用资源明显减少
                if ("${IS_SPRING_NATIVE}" == "true") { // 构建原生镜像包
                    Maven.springNative(this, map, mavenCommandType, isMavenTest)
                } else if ("${map.maven_settings_xml_id}".trim() == "") { // 是否自定义maven仓库
                    // 更快的构建工具mvnd 多个的守护进程来服务构建请求来达到并行构建的效果  源码: https://github.com/apache/maven-mvnd
                    if ("${IS_MAVEN_SINGLE_MODULE}" == 'true') { // 如果是整体单模块项目 不区分多模块也不需要指定项目模块名称
                        MAVEN_ONE_LEVEL = ""
                        // 在pom.xml文件目录下执行 规范是pom.xml在代码根目录
                        // def pomPath = Utils.getShEchoResult(this, " find . -name \"pom.xml\" ").replace("pom.xml", "")
                        sh "${mavenCommandType} clean install -T 2C -Dmaven.compile.fork=true ${isMavenTest} "
                    } else {  // 多模块情况
                        // 单独指定模块构建 -pl指定项目名 -am 同时构建依赖项目模块 跳过测试代码  -T 1C 参数，表示每个CPU核心跑一个工程并行构建
                        sh "${mavenCommandType} clean install -T 2C -pl ${MAVEN_ONE_LEVEL}${PROJECT_NAME} -am -Dmaven.compile.fork=true ${isMavenTest} ${CUSTOM_MAVEN_PACKAGE_COMMAND} "
                    }
                } else {
                    // 基于自定义setting.xml文件方式打包 如私有包等
                    Maven.packageBySettingFile(this, map, mavenCommandType, isMavenTest)
                }
                // 获取pom文件信息
                // Maven.getPomInfo(this)
            }
        }
        def mavenTarget = "target" // Maven打包目录
        if ("${JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringBoot) {
            javaPackageType = "jar"
            // Spring Native默认Linux无后缀 原生直接执行的文件 也无需JVM环境
            if ("${IS_SPRING_NATIVE}" == "true") {
                javaPackageType = ""
            }
        } else if ("${JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringMVC) {
            javaPackageType = "war"
        } else if ("${JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.Quarkus) {
            // 核心包在 target/quarkus-app/ 下面  启动命令 java -jar target/quarkus-app/quarkus-run.jar
            javaPackageType = "tar.gz"
            def quarkusAppName = "quarkus-app"
            sh "cd ${mavenTarget}/ && pwd && ls && chmod -R 755 ${quarkusAppName} && " +
                    "tar -zcvf ${quarkusAppName}.${javaPackageType} ${quarkusAppName} " // >/dev/null 2>&1
        }

        // Maven打包产出物位置
        if ("${IS_MAVEN_SINGLE_MODULE}" == 'true') {
            buildPackageLocationDir = "${mavenTarget}"
        } else {
            buildPackageLocationDir = ("${MAVEN_ONE_LEVEL}" == "" ? "${PROJECT_NAME}" : "${MAVEN_ONE_LEVEL}${PROJECT_NAME}") + "/${mavenTarget}"
        }
        buildPackageLocation = "${buildPackageLocationDir}" + "/*.${javaPackageType}"
        if ("${IS_SPRING_NATIVE}" == "true") {
            // 名称为pom.xml下build内的imageName标签名称 统一名称或动态定义配置
            buildPackageLocation = "${buildPackageLocationDir}" + "/spring-native-graalvm"
        }
        println(buildPackageLocation)
        buildPackageSize = Utils.getFileSize(this, buildPackageLocation)
        Tools.printColor(this, "Maven打包成功 ✅")
        // 上传部署文件到OSS
        uploadOss(map)
    }
}

/**
 * Gradle编译构建
 */
def gradleBuildProject(map) {
    println("执行Gradle构建 🏗️  ")
    dir("${env.WORKSPACE}/${GIT_PROJECT_FOLDER_NAME}") { // 源码在特定目录下
        timeout(time: 30, unit: 'MINUTES') { // 超时终止防止非正常构建情况 长时间占用资源
            retry(2) {
                Gradle.build(this, "bootJar") // 打包命令
                buildPackageLocationDir = "build/libs"  // Gradle构建产物目录
            }
        }
        dir(buildPackageLocationDir) {
            sh "rm -f *-plain.jar && ls"  // 删除无效的jar包
        }
        buildPackageLocation = "${buildPackageLocationDir}" + "/*.jar"
        println(buildPackageLocation)
        buildPackageSize = Utils.getFileSize(this, buildPackageLocation)
        Tools.printColor(this, "Gradle打包成功 ✅")
    }
}

/**
 * Python编译构建
 */
def pythonBuildProject(map) {
    dir("${env.WORKSPACE}/${GIT_PROJECT_FOLDER_NAME}") {
        // 压缩源码文件 加速传输
        Python.codePackage(this)
    }
    Tools.printColor(this, "Python语言构建成功 ✅")
}

/**
 * Go编译构建
 */
def goBuildProject() {
    Go.build(this)
    Tools.printColor(this, "Go语言构建成功 ✅")
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
def buildImage(map) {
    // Docker多阶段镜像构建处理
    Docker.multiStageBuild(this, "${DOCKER_MULTISTAGE_BUILD_IMAGES}")
    // 构建并上传Docker镜像仓库  只构建一次
    retry(2) { // 重试几次 可能网络等问题导致构建失败
        Docker.build(this, "${dockerImageName}")
    }
    // 自动替换相同应用不同分布式部署节点的环境文件  打包构建上传不同的镜像
    if ("${IS_DIFF_CONF_IN_DIFF_MACHINES}" == 'true' && "${SOURCE_TARGET_CONFIG_DIR}".trim() != "" && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
        def deployNum = 2  // 暂时区分两个不同环境文件 实际还存在每一个部署服务的环境配置文件都不一样
        docker.image("${mavenDockerName}:${map.maven.replace('Maven', '')}-${JDK_PUBLISHER}-${JDK_VERSION}").inside("-v /var/cache/maven/.m2:/root/.m2") {
            mavenBuildProject(map, deployNum) // 需要mvn jdk构建环境
        }
        // Docker多阶段镜像构建处理
        Docker.multiStageBuild(this, "${DOCKER_MULTISTAGE_BUILD_IMAGES}")
        // 构建并上传Docker镜像仓库  多节点部署只构建一次
        Docker.build(this, "${dockerImageName}", deployNum)
    }
}

/**
 * 上传部署文件到OSS
 * 方便下载构建部署包
 */
def uploadOss(map) {
    if ("${IS_UPLOAD_OSS}" == 'true') {
        try {
            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                // 源文件地址
                def sourceFile = "${env.WORKSPACE}/${buildPackageLocation}"
                // 目标文件
                def targetFile = "backend/${env.JOB_NAME}/${PROJECT_NAME}-${SHELL_ENV_MODE}-${env.BUILD_NUMBER}.${javaPackageType}"
                javaOssUrl = AliYunOSS.upload(this, map, sourceFile, targetFile)
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
def uploadRemote(filePath, map) {
    retry(3) {   // 重试几次 可能网络等问题导致上传失败
        // 应用包部署目录
        projectDeployFolder = "/${DEPLOY_FOLDER}/${FULL_PROJECT_NAME}/"
        // ssh免密登录检测和设置
        autoSshLogin(map)
        timeout(time: 2, unit: 'MINUTES') {
            // 同步脚本和配置到部署服务器
            // if (IS_CODE_AND_COMMAND_DEPLOY == false) {
            syncScript()
            // }
        }
        println("上传部署文件到部署服务器中... 🚀 ")

        // 基于scp或rsync同步文件到远程服务器
        if ("${IS_PUSH_DOCKER_REPO}" != 'true') { // 远程镜像库方式不需要再上传构建产物 直接远程仓库docker pull拉取镜像
            if (IS_PACKAGE_DEPLOY == true) { // 直接构建包部署方式  如无源码的情况
                sh " scp ${proxyJumpSCPText} ${DEPLOY_PACKAGE_FILENAME} ${remote.user}@${remote.host}:${projectDeployFolder} "
            } else if ("${IS_SOURCE_CODE_DEPLOY}" == 'true') {  // 源码直接部署 无需打包 只需要压缩上传到服务器上执行自定义命令启动
                sh " scp ${proxyJumpSCPText} ${sourceCodeDeployName}.tar.gz ${remote.user}@${remote.host}:${projectDeployFolder} "
            } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                dir("${env.WORKSPACE}/${GIT_PROJECT_FOLDER_NAME}") { // 源码在特定目录下
                    sh " scp ${proxyJumpSCPText} ${npmPackageLocation} " +
                            "${remote.user}@${remote.host}:${projectDeployFolder}"
                }
            } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                // 上传前删除部署目录的jar包 防止名称修改等导致多个部署目标jar包存在  jar包需要唯一性
                sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd ${projectDeployFolder} && rm -f *.${javaPackageType}' "
                dir("${env.WORKSPACE}/${GIT_PROJECT_FOLDER_NAME}") {
                    // 上传构建包到远程服务器
                    sh " scp ${proxyJumpSCPText} ${buildPackageLocation} ${remote.user}@${remote.host}:${projectDeployFolder} "
                }
            } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Go) {
                // Go语言打包产物 上传包到远程服务器
                sh "cd ${filePath} && scp ${proxyJumpSCPText} main.go ${remote.user}@${remote.host}:${projectDeployFolder} "
            } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
                // Python语言打包产物 上传包到远程服务器
                // sh "cd ${filePath}/dist && scp ${proxyJumpSCPText} app ${remote.user}@${remote.host}:${projectDeployFolder} "
                dir("${env.WORKSPACE}/${GIT_PROJECT_FOLDER_NAME}") {
                    sh "scp ${proxyJumpSCPText} python.tar.gz ${remote.user}@${remote.host}:${projectDeployFolder} "
                }
            } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Cpp) {
                // C++语言打包产物 上传包到远程服务器
                sh "cd ${filePath} && scp ${proxyJumpSCPText} app ${remote.user}@${remote.host}:${projectDeployFolder} "
            }
        }
        Tools.printColor(this, "上传部署文件到部署服务器完成 ✅")
    }
}

/**
 * 人工卡点审批
 * 每一个人都有点击执行流水线权限  但是不一定有发布上线的权限 为了保证项目稳定安全等需要人工审批
 */
def manualApproval(map) {
    // 针对生产环境部署前做人工发布审批
    // if ("${IS_PROD}" == 'true') {
    // 选择具有审核权限的人员 可以配置一个或多个 也可以相互审批
    def approvalPersons = ["admin", "潘维吉", "**科技"] // 多审批人数组 参数化配置 也可指定审批人
    def approvalPersonMobiles = "18863302302" // 审核人的手机 多个逗号分隔 用于钉钉通知等

    // 两种审批 1. 或签(一名审批人员同意或拒绝即可) 2. 会签(须所有审批人同意)
    if ("${approvalPersons}".contains("${BUILD_USER}")) {
        // 如果是有审核权限人员发布的跳过本次审核
    } else {
        // 同时钉钉通知到审核人 点击链接自动进入要审核流水线  如果Jenkins提供Open API审核可直接在钉钉内完成点击审批
        DingTalk.noticeMarkDown(this, map.ding_talk_credentials_ids, "发布流水线申请人工审批通知", "### 发布流水线申请人工审批通知 ✍🏻 " +
                " \n #### ${BUILD_USER}申请发布${PROJECT_NAME}服务 !" +
                " \n ### [请您点击链接去审批](${env.JOB_URL}) 👈🏻 " +
                " \n ##### Git代码  [变更日志](${REPO_URL.replace('.git', '')}/-/commits/${BRANCH_NAME}/)  " +
                " \n ###### Jenkins  [运行日志](${env.BUILD_URL}console)  " +
                " \n ###### 发布人: ${BUILD_USER}" +
                " \n ###### 通知时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                "${approvalPersonMobiles}")
        // 中断询问审批
        input(
                message: "请相关人员审批本次部署, 是否同意继续发布 ?",
                ok: "同意"
        )
        def approvalCurrentUserId = ""
        def approvalCcurrentUser = ""
        wrap([$class: 'BuildUser']) {
            approvalCurrentUser = env.BUILD_USER
            approvalCurrentUserId = env.BUILD_USER_ID
        }
        println(approvalCurrentUserId)
        println(approvalCcurrentUser)
        if (!"${approvalPersons}".contains(approvalCcurrentUser) || !"${approvalPersons}".contains(approvalCurrentUserId)) {
            error("人工审批失败, 您没有审批的权限, 请重新运行流水线发起审批 ❌")
        } else {
            // 审核人同意后通知发布人 消息自动及时高效传递
            DingTalk.noticeMarkDown(this, map.ding_talk_credentials_ids, "发布流水线申请人工审批同意通知", "### 您发布流水线已被${approvalCcurrentUser}审批同意 ✅" +
                    " \n #### 前往流水线 [查看](${env.JOB_URL})  !" +
                    " \n ###### 审批时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                    "${BUILD_USER_MOBILE}")
        }
    }
    // }
}

/**
 * 部署启动运行项目
 */
def runProject(map) {
    try {
        retry(2) { // 重试几次 可能网络等问题导致构建失败
            if (IS_CODE_AND_COMMAND_DEPLOY == true) { // 只依赖代码和命令直接部署方式
                // tar -xzf /源目录/源文件.tar.gz -C /目标目录
                sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} '${CUSTOM_STARTUP_COMMAND}' "
            } else {
                // 初始化docker
                initDocker()

                if ("${IS_PUSH_DOCKER_REPO}" == 'true') {
                    // 拉取远程仓库Docker镜像
                    Docker.pull(this, "${dockerImageName}")
                }
                if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                    sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER}/web " +
                            "&& ./docker-release-web.sh '${SHELL_WEB_PARAMS_GETOPTS}' ' "
                } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                    // 部署之前的相关操作
                    beforeRunProject(map)
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
            }

            Tools.printColor(this, "执行应用部署完成 ✅")
        }
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
    Tools.printColor(this, "开始应用服务健康探测, 请耐心等待... 🚀 ")
    def healthCheckParams = null
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

    // 单节点部署启动最大超时时间 可根据项目大小动态配置健康探测时长
    timeout(time: 8, unit: 'MINUTES') {  // health-check.sh有检测超时时间 timeout为防止shell脚本超时失效兼容处理
        healthCheckMsg = sh(
                script: "ssh  ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER}/ && ./health-check.sh ${healthCheckParams} '",
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
        dingNotice(map, 1, "**失败或超时或已回滚❌** [点击我验证](${healthCheckUrl}) 👈 ", "${BUILD_USER_MOBILE}")

        try {
            // 打印应用服务启动失败日志 方便快速排查错误
            Tools.printColor(this, "------------ 应用服务${healthCheckUrl} 启动异常日志开始 START 👇 ------------", "red")
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'docker logs ${dockerContainerName} "
            Tools.printColor(this, "------------ 应用服务${healthCheckUrl} 启动异常日志结束 END 👆 ------------", "red")
        } catch (e) {
        }
        if ("${IS_ROLL_DEPLOY}" == 'true' || "${IS_BLUE_GREEN_DEPLOY}" == 'true') {
            println '分布式部署情况, 服务启动失败, 自动中止取消job, 防止继续部署导致其他应用服务挂掉 。'
            IS_ROLL_DEPLOY = false
        }

        // 服务启动失败回滚到上一个版本  保证服务高可用性
        Docker.rollbackServer(this, map, "${dockerImageName}", "${dockerContainerName}")

        IS_ARCHIVE = false // 不归档
        currentBuild.result = 'FAILURE' // 失败  不稳定UNSTABLE 取消ABORTED
        error("应用服务健康探测失败, 终止当前Pipeline运行 ❌")
        return
    }
}

/**
 * 各种类型测试
 * 1. 单元测试  2. API集成测试  3. 端到端测试  4. 性能测试  5. 安全测试  6. UI测试  7. 冒烟测试
 */
def integrationTesting(map) {
    // 可先动态传入数据库名称部署集成测试应用 启动测试完成销毁 再重新部署业务应用
    try {
        // 创建JMeter性能压测报告
        Tests.createJMeterReport(this)
        // 创建冒烟测试报告
        Tests.createSmokeReport(this)

        // 结合YApi或者ApiFox接口管理做自动化API测试
        ApiFox.autoTest(this)

        def apiFoxUrl = "http://apiFox.panweiji.com"
        def testUrl = "${apiFoxUrl}/api/open/run_auto_test?${AUTO_TEST_PARAM}"
        // 执行接口测试
        def content = HttpRequest.get(this, "${testUrl}")
        def json = readJSON text: "${content}"
        def failedNum = "${json.message.failedNum}"
        def projectId = "${AUTO_TEST_PARAM}".trim().split("&")[2].split("=")[0].replaceAll("env_", "")
        def testCollectionId = "${AUTO_TEST_PARAM}".trim().split("&")[0].replaceAll("id=", "")
        DingTalk.noticeMarkdown(this, map.ding_talk_credentials_ids, "自动化API集成测试报告", "### 自动化API集成测试报告 🙋 " +
                "\n #### ${json.message.msg} \n #### 测试报告: [查看结果](${testUrl.replace("mode=json", "mode=html")}) 🚨" +
                "\n ##### 测试总耗时:  ${json.runTime} \n ##### 测试用例不完善也可导致不通过 👉[去完善](${apiFoxUrl}/project/${projectId}/interface/col/${testCollectionId})  ",
                "${failedNum}" == "0" ? "" : "${BUILD_USER_MOBILE}")
    } catch (e) {
        println "自动化集成测试失败 ❌"
        println e.getMessage()
    }
}

/**
 * 蓝绿部署
 */
def blueGreenDeploy(map) {
    // 蓝绿部署: 好处是只用一个主单点服务资源实现部署过程中不间断提供服务
    // 1、先启动部署一个临时服务将流量切到蓝服务器上  2、再部署真正提供服务的绿服务器  3、部署完绿服务器,销毁蓝服务器,将流量切回到绿服务器
    // 先判断是否在一台服务器部署
    if ("${IS_SAME_SERVER}" == 'false') { // 不同服务器蓝绿部署
        def mainServerIp = remote.host // 主服务器IP
        def blueServerIp = ""  // 蓝服务器IP
        // 先部署一个临时服务将流量切到蓝服务器上
        if (remote_worker_ips.isEmpty()) {
            error("多机蓝绿部署, 请先在相关的Jenkinsfile.x配置从服务器ip数组remote_worker_ips参数 ❌")
        }
        // 循环串行执行多机分布式部署
        remote_worker_ips.each { ip ->
            println ip
            remote.host = ip
            blueServerIp = ip

            uploadRemote(Utils.getShEchoResult(this, "pwd"), map)  // 上传代码到远程服务器
            runProject(map)  // 运行部署
            if (params.IS_HEALTH_CHECK == true) {
                MACHINE_TAG = "蓝机"
                healthCheck(map)
            }
        }
        // 再部署真正提供服务的绿服务器
        remote.host = mainServerIp
        runProject(map)
        if (params.IS_HEALTH_CHECK == true) {
            MACHINE_TAG = "绿机"
            healthCheck(map)
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
            // 先部署一个临时服务将流量切到蓝服务器上
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
                    healthCheck(map, " -a ${PROJECT_TYPE} -b ${healthCheckUrl}")
                } catch (error) {
                    // 注意：这地方是使用的旧镜像部署，会导致一个问题，如果旧镜像本身就有问题，会导致部署失败，因为永远无法使用新镜像
                    println error.getMessage()
                    println("从服务器健康探测失败异常捕获, 因为可能是旧镜像导致, 继续部署主服务器 ❌")
                }
            }
            // 再部署真正提供服务的绿服务器
            runProject(map)
            if (params.IS_HEALTH_CHECK == true) {
                MACHINE_TAG = "绿机"
                healthCheck(map)
            }
            sleep(time: 2, unit: "SECONDS") // 暂停pipeline一段时间，单位为秒
            // 部署完绿服务器,销毁蓝服务器,将流量切回到绿服务器
            def workDockerContainerName = "${FULL_PROJECT_NAME}-worker-${SHELL_ENV_MODE}"
            sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} ' docker stop ${workDockerContainerName} --time=0 || true && docker rm ${workDockerContainerName} || true ' "
            // 自动配置nginx负载均衡
            // Nginx.conf(this, "${remote.host}", "${SHELL_HOST_PORT}", "${remote.host}", "${workHostPort}")
        }
    }
}

/**
 * 滚动部署
 */
def scrollToDeploy(map) {
    if ("${IS_CANARY_DEPLOY}" == "true") {  // 金丝雀和灰度部署方式
        println "Docker灰度发布:  滚动部署情况 只部署第一个节点 单机部署阶段已部署 退出滚动部署步骤"
        return  // 返回后续代码不再执行
        /* if (machineNum >= 2) { // 金丝雀分批部署控制阀门
            return
        } */
    }

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

            // 如果配置多节点动态替换不同的配置文件重新执行maven构建打包或者直接替换部署服务器文件
            if ("${IS_DIFF_CONF_IN_DIFF_MACHINES}" == 'true' && "${SOURCE_TARGET_CONFIG_DIR}".trim() != "" && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                docker.image("${mavenDockerName}:${map.maven.replace('Maven', '')}-${JDK_PUBLISHER}-${JDK_VERSION}").inside("-v /var/cache/maven/.m2:/root/.m2") {
                    mavenBuildProject(map) // 需要mvn jdk构建环境
                }
            }

            uploadRemote(Utils.getShEchoResult(this, "pwd"), map) // 上传部署到远程服务器
            runProject(map) //  运行部署项目
            if (params.IS_HEALTH_CHECK == true) {
                healthCheck(map)
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
                healthCheck(map, " -a ${PROJECT_TYPE} -b ${healthCheckUrl}")
            }
        }
    }
}

/**
 * 灰度发布  金丝雀发布  A/B测试
 * 基于Nginx Ingress 灰度发布  实现多版本并存 非强制用户更新提升用户体验
 */
def grayscaleDeploy(map) {
    // 灰度发布  金丝雀发布  A/B测试  Nginx-ingress 是使用 Nginx 作为反向代理和负载平衡器的 Kubernetes 的 Ingress 控制器
    // Kubernetes.ingressDeploy(this, map)
    Kubernetes.deploy(this, map)
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
 *  Serverless工作流发布  无服务器架构免运维 只需要按照云函数的定义要求进行少量的声明或者配置
 *  可在两个不同物理可用区弹性部署  自动化的服务托管和弹性伸缩功能，使得系统可以根据实际需求动态调整资源和计费，有效降低了运维复杂度
 */
def serverlessDeploy() {
    // K8s中的Knative或者结合公有云方案 实现Serverless无服务
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
    println "自动同步脚本和配置等到部署服务器"
    try {
        // 自动创建服务器部署目录
        // ssh登录概率性失败 连接数超报错: kex_exchange_identification
        // 解决vim /etc/ssh/sshd_config中 MaxSessions与MaxStartups改大2000 默认10 重启生效 systemctl restart sshd.service
        sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'mkdir -p /${DEPLOY_FOLDER}/${FULL_PROJECT_NAME}' "
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
            // 处理 .dockerignore文件被忽略了 .dockerignore 必须位于构建上下文根目录 docker build 命令的最后一个参数决定 如 .
            sh " scp ${proxyJumpSCPText} .ci/.dockerignore  ${remote.user}@${remote.host}:${projectDeployFolder} "
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
    if (!fileExists(".ci")) {
        // println "为保证先后顺序拉取代码 可能导致第一次构建时候无法找到CI仓库代码 重新拉取代码"
        pullCIRepo()
    }
}

/**
 * 部署运行之前操作
 */
def beforeRunProject(map) {
    // 多节点部署无感知不执行部署前通知
    if ("${IS_BEFORE_DEPLOY_NOTICE}" == 'true' && "${IS_ROLL_DEPLOY}" == 'false' && "${IS_BLUE_GREEN_DEPLOY}" == 'false') {
        // 部署之前通知
        dingNotice(map, 2)
    }
    try {
        if ("${IS_GRACE_SHUTDOWN}" == 'true') { // 准备启用 有新方案
            // Spring Boot优雅停机 curl几秒超时  需要开放监控服务  但是开放监控服务又安全性问题  建议使用Spring Boot新版本自带的优雅停机配置
            sh " curl --connect-timeout 3 --max-time 10  ${healthCheckUrl}/actuator/shutdown -X POST "
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
    Docker.initDocker(this)
}

/**
 * 归档文件
 */
def archive() {
    try {
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            archiveArtifacts artifacts: "${npmPackageLocation}", onlyIfSuccessful: true
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
            archiveArtifacts artifacts: "${buildPackageLocation}", onlyIfSuccessful: true
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
            sh " rm -f ${buildPackageLocation} "
        }
        //}
    } catch (error) {
        // println "删除打包产出物异常"
        // println error.getMessage()
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
            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                sh "rm -f *.${imageSuffixName}"
                QRCode.generate(this, "${healthCheckUrl}", imageName)
                def sourceFile = "${env.WORKSPACE}/${imageName}.${imageSuffixName}" // 源文件
                def targetFile = "frontend/${env.JOB_NAME}/${env.BUILD_NUMBER}/${imageName}.${imageSuffixName}"
                // 目标文件
                qrCodeOssUrl = AliYunOSS.upload(this, map, sourceFile, targetFile)
                println "${qrCodeOssUrl}"
            }
        } catch (error) {
            println " 生成二维码失败 ❌ "
            println error.getMessage()
        }
    }
}

/**
 * 控制服务 启动 停止 重启等
 */
def controlService(map) {
    // 内部语法使用docker镜像
    if ("${IS_K8S_DEPLOY}" == 'true') {
        docker.image("panweiji/k8s:latest").inside {
            Deploy.controlService(this, map)
        }
    } else {
        Deploy.controlService(this, map)
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
    // AliYunOSS.upload(this, map)

}

/**
 * 总会执行统一处理方法
 */
def alwaysPost() {
    // sh 'pwd'
    // deleteDir()  // 清空工作空间
    // Jenkins全局安全配置->标记格式器内设置Safe HTML支持html文本
    try {
        def releaseEnvironment = "${NPM_RUN_PARAMS != "" ? NPM_RUN_PARAMS : SHELL_ENV_MODE}"
        def noticeHealthCheckUrl = "${APPLICATION_DOMAIN == "" ? healthCheckUrl : healthCheckDomainUrl}"
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            currentBuild.description = "${IS_GEN_QR_CODE == 'true' ? "<img src=${qrCodeOssUrl} width=250 height=250 > <br/> " : ""}" +
                    " 分支: ${BRANCH_NAME} <br/> 环境: ${releaseEnvironment}  包大小: ${buildPackageSize} <br/> 发布人: ${BUILD_USER}"
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
            currentBuild.description =
                    "${javaOssUrl.trim() != '' ? "<br/><a href='${javaOssUrl}'> 👉直接下载构建${javaPackageType}包</a>" : ""}" +
                            " 分支: ${BRANCH_NAME} <br/> 环境: ${releaseEnvironment}  包大小: ${buildPackageSize} <br/> 发布人: ${BUILD_USER}"
        }
        // 构建徽章展示关键信息
        if ("${IS_PROD}" == 'true') {
            addBadge(id: "version-badge", text: "${tagVersion}", color: 'green', cssClass: 'badge-text--background')
        } else {
            addBadge(id: "env-badge", text: "${releaseEnvironment}".toUpperCase(), color: 'blue', cssClass: 'badge-text--background')
        }
        if (IS_CANARY_DEPLOY == true) { // 金丝雀部署方式
            addBadge(id: "canary-deploy-badge", text: "金丝雀", color: 'purple', cssClass: 'badge-text--background')
        }

        addBadge(id: "url-badge", icon: 'symbol-link plugin-ionicons-api', text: '访问地址', link: "${noticeHealthCheckUrl}", target: '_blank')
        removeBadges(id: "launch-badge")
    } catch (error) {
        println error.getMessage()
    }
}

/**
 * 生成版本tag和变更日志
 */
def gitTagLog() {
    if (IS_PACKAGE_DEPLOY == true) {
        return  // 终止后续阶段执行  因为直接是包部署方式 无源码仓库
    }
    // 文件夹的所有者和现在的用户不一致导致 git命令异常
    // sh "git config --global --add safe.directory ${env.WORKSPACE} || true"

    // 未获取到参数 兼容处理 因为参数配置从代码拉取 必须先执行一次jenkins任务才能生效
    if (!params.IS_GIT_TAG && params.IS_GIT_TAG != false) {
        params.IS_GIT_TAG = true
    }
    // 构建成功后生产环境并发布类型自动打tag和变更记录  指定tag方式不再重新打tag
    if (params.IS_GIT_TAG == true && "${IS_PROD}" == 'true' && params.GIT_TAG == GlobalVars.noGit) {
        // 获取变更记录
        def gitChangeLog = ""
        if ("${Constants.DEFAULT_VERSION_COPYWRITING}" == params.VERSION_DESCRIPTION) {
            gitChangeLog = changeLog.genChangeLog(this, 100).replaceAll("\\;", "\n")
        } else {
            // 使用自定义文案
            gitChangeLog = "${params.VERSION_DESCRIPTION}"
        }
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
    // 非生产环境下也需要回滚版本 所以需要打tag版本 如 1.0.0-beta 或者 0.x.y 等
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
        if (params.GIT_TAG != GlobalVars.noGit) {
            rollbackTag = "**Git Tag构建版本: ${params.GIT_TAG}**" // Git Tag版本添加标识
        }
        def monorepoProjectName = ""
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd && "${IS_MONO_REPO}" == 'true') {
            monorepoProjectName = "MonoRepo项目: ${PROJECT_NAME} \n"   // 单体仓库区分项目
        }
        // Docker部署方式
        def deployType = ""
        def k8sPodContent = ""
        if ("${IS_ROLL_DEPLOY}" == "true") {
            deployType = "部署方式: Docker集群滚动发布"
            if ("${IS_CANARY_DEPLOY}" == "true") {  // 金丝雀部署方式
                deployType = "部署方式: Docker集群金丝雀发布"
            }
        }
        // K8S部署方式
        if ("${IS_K8S_DEPLOY}" == "true") {
            deployType = "部署方式: K8S集群滚动发布"
            if ("${IS_CANARY_DEPLOY}" == "true") {  // 金丝雀部署方式
                deployType = "部署方式: K8S集群金丝雀发布"
            } else {
                k8sPodContent = "- K8S集群部署Pod节点数: *${K8S_POD_REPLICAS}* 个 \n"
                if ("${IS_K8S_AUTO_SCALING}" == "true") {
                    deployType = deployType + "+自动弹性扩缩容"
                }
            }
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
        def noticeHealthCheckUrl = "${APPLICATION_DOMAIN == "" ? healthCheckUrl : healthCheckDomainUrl}"

        try {
            if (type == 0) { // 失败
                if (!isHealthCheckFail) {
                    DingTalk.noticeMarkDown(this, map.ding_talk_credentials_ids,
                            "❌ CI/CD失败通知 ${PROJECT_TAG}${envTypeMark}${projectTypeName}",
                            "### [${env.JOB_NAME}#${env.BUILD_NUMBER}](${env.BUILD_URL}) ${PROJECT_TAG}${envTypeMark}${projectTypeName}项目${msg} \n" +
                                    "#### 请及时处理 🏃 \n" +
                                    "##### <font color=red> 流水线失败原因:</font> [运行日志](${env.BUILD_URL}console) 👈  \n" +
                                    "###### 发布环境: ${releaseEnvironment}  持续时间: ${durationTimeString} \n" +
                                    "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL}) \n" +
                                    "###### 发布人: ${BUILD_USER}  构建机器: ${NODE_NAME} \n" +
                                    "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                            "${BUILD_USER_MOBILE}")
                }
            } else if (type == 1 && "${IS_ONLY_NOTICE_CHANGE_LOG}" == 'false') { // 部署完成
                if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                    // 生成二维码 方便手机端扫描
                    genQRCode(map)
                    def screenshot = "![screenshot](${qrCodeOssUrl})"
                    if ("${qrCodeOssUrl}" == "") {
                        screenshot = ""
                    }

                    DingTalk.noticeActionCard(this, map.ding_talk_credentials_ids,
                            "✅ CI/CD ${PROJECT_TAG}${envTypeMark}${projectTypeName}部署结果通知",
                            "${screenshot} \n" +
                                    "### [${env.JOB_NAME}#${env.BUILD_NUMBER} ${PROJECT_TAG}${envTypeMark}${projectTypeName} ${MACHINE_TAG}](${env.JOB_URL}) \n" +
                                    "##### Nginx Web服务启动${msg} \n" +
                                    "${monorepoProjectName}" +
                                    "##### ${deployType} \n" +
                                    "###### ${rollbackTag} \n" +
                                    "##### 详细信息 \n" +
                                    "- 启动用时: ${healthCheckTimeDiff}   持续时间: ${durationTimeString} \n" +
                                    "- 构建分支: ${BRANCH_NAME}   环境: ${releaseEnvironment} \n" +
                                    "- Node版本: ${NODE_VERSION}   包大小: ${buildPackageSize} \n" +
                                    "${k8sPodContent}" +
                                    "- 访问URL: [${noticeHealthCheckUrl}](${noticeHealthCheckUrl}) \n" +
                                    "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL}) \n" +
                                    "###### 发布人: ${BUILD_USER}  构建机器: ${NODE_NAME} \n" +
                                    "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                            "访问Web服务",
                            "${noticeHealthCheckUrl}",
                            isHealthCheckFail == true ? atMobiles : (notifierPhone == '110' ? '' : notifierPhone))
                } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
                    def javaInfo = ""
                    if ("${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                        javaInfo = "- 构建版本: JDK${JDK_VERSION}   包大小: ${buildPackageSize} \n"
                        if ("${javaOssUrl}".trim() != '') {
                            javaInfo = javaInfo + "[直接下载构建${javaPackageType}包](${javaOssUrl})  👈 \\n"
                        }
                    }
                    def pythonInfo = ""
                    if ("${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
                        pythonInfo = "- 运行版本: Python${CUSTOM_PYTHON_VERSION}   包大小: ${buildPackageSize} \n"
                    }

                    DingTalk.noticeMarkDown(this, map.ding_talk_credentials_ids,
                            "✅ CI/CD ${PROJECT_TAG}${envTypeMark}${projectTypeName}部署结果通知",
                            "### [${env.JOB_NAME}#${env.BUILD_NUMBER} ${PROJECT_TAG}${envTypeMark}${projectTypeName} ${MACHINE_TAG}](${env.JOB_URL}) \n" +
                                    "#### CI/CD部署启动${msg} \n" +
                                    "##### ${deployType} \n" +
                                    "###### ${rollbackTag} \n" +
                                    "##### 详细信息 \n" +
                                    "- 启动用时: ${healthCheckTimeDiff}   持续时间: ${durationTimeString} \n" +
                                    "- 构建分支: ${BRANCH_NAME}   环境: ${releaseEnvironment} \n" +
                                    "${javaInfo}" +
                                    "${pythonInfo}" +
                                    "${k8sPodContent}" +
                                    "- API地址: [${noticeHealthCheckUrl}](${noticeHealthCheckUrl}) \n" +
                                    "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL}) \n" +
                                    "###### 发布人: ${BUILD_USER}  构建机器: ${NODE_NAME} \n" +
                                    "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                            isHealthCheckFail == true ? atMobiles : (notifierPhone == '110' ? '' : notifierPhone))
                }
            } else if (type == 2 && "${IS_ONLY_NOTICE_CHANGE_LOG}" == 'false') { // 部署之前
                DingTalk.noticeMarkDown(this, map.ding_talk_credentials_ids,
                        "🚀 CI/CD ${PROJECT_TAG}${envTypeMark}${projectTypeName}部署前通知",
                        "### [${env.JOB_NAME}#${env.BUILD_NUMBER} ${envTypeMark}${projectTypeName}](${env.JOB_URL}) \n" +
                                "#### ${PROJECT_TAG}服务部署启动中 🚀  请稍等...  ☕ \n" +
                                "###### 发布人: ${BUILD_USER}  构建机器: ${NODE_NAME} \n" +
                                "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                        "")
            } else if (type == 3) { // 变更记录 有些场景精简提醒只推送发布日志消
                def gitChangeLog = ""
                if ("${Constants.DEFAULT_VERSION_COPYWRITING}" == params.VERSION_DESCRIPTION) {
                    gitChangeLog = changeLog.genChangeLog(this, 20).replaceAll("\\;", "\n")
                } else {
                    // 使用自定义文案
                    gitChangeLog = "${params.VERSION_DESCRIPTION}"
                }
                if ("${gitChangeLog}" != GlobalVars.noChangeLog) {
                    def titlePrefix = "${PROJECT_TAG} BUILD#<font color=green>${env.BUILD_NUMBER}</font>"
                    // 如果gitChangeLog为空 赋值提醒文案
                    if ("${gitChangeLog}" == '') {
                        gitChangeLog = "无版本变更记录 🈳"
                    }

                    try {
                        if ("${tagVersion}") {
                            titlePrefix = "${PROJECT_TAG} <font color=green>${tagVersion}</font>"
                        }
                    } catch (e) {
                    }

                    DingTalk.noticeMarkDown(this, map.ding_talk_credentials_ids,
                            "📜 ${PROJECT_TAG} ${tagVersion} ${envTypeMark}${projectTypeName}发布日志",
                            "### ${titlePrefix} ${envTypeMark}${projectTypeName}发布日志 🎉 \n" +
                                    "#### 项目: ${PROJECT_NAME} \n" +
                                    "#### 环境: *${projectTypeName} ${IS_PROD == 'true' ? "生产环境" : "${releaseEnvironment}内测环境"}* \n" +
                                    "##### 描述: ${JenkinsCI.getCurrentBuildDescription(this)} \n" +
                                    "${gitChangeLog} \n" +
                                    ">  👉  前往 [变更日志](${REPO_URL.replace('.git', '')}/blob/${BRANCH_NAME}/CHANGELOG.md) 查看 \n" +
                                    "###### 发布人: ${BUILD_USER} \n" +
                                    "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                            "")
                }
            }
        } catch (e) {
            echo "钉钉通知失败，原因：${e.getMessage()}"
        }

    }
}

