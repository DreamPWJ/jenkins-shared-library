#!groovy
import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*
import shared.library.devops.ChangeLog
import shared.library.devops.GitTagLog

/**
 * @author 潘维吉
 * @description 通用核心共享Pipeline脚本库  针对桌面客户端 (Windows、MacOS、Linux)
 * 桌面端技术类型 0.原生桌面技术(如C++ For Windows、Linux, Swift For MacOS) 1.Electron 2.Flutter 3.Unity 4.ComposeMultiPlatform 5.Qt
 */
def call(String type = 'desktop', Map map) {
    echo "Pipeline共享库脚本类型: ${type}, jenkins分布式节点名: ${map.jenkins_node}"
    // 应用共享方法定义
    changeLog = new ChangeLog()
    gitTagLog = new GitTagLog()

    // 初始化参数
    getInitParams(map)
    // 钉钉授信ID数组 系统设置里面配置 自动生成
    dingTalkIds = "${map.ding_talk_credentials_id}".split(",")

    if (type == "desktop") {
        pipeline {
            agent {
                label "${map.jenkins_node}"  // 指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
            }

            parameters {
                gitParameter(name: 'GIT_BRANCH', type: 'PT_BRANCH', defaultValue: "${DEFAULT_GIT_BRANCH}", selectedValue: "DEFAULT",
                        useRepository: "${REPO_URL}", sortMode: 'ASCENDING', branchFilter: 'origin/(.*)',
                        description: "选择要构建的Git分支 默认: " + "${DEFAULT_GIT_BRANCH} (可自定义配置具体任务的默认常用分支, 实现一键或全自动构建)")
                gitParameter(name: 'GIT_TAG', type: 'PT_TAG', defaultValue: GlobalVars.noGit, selectedValue: GlobalVars.noGit,
                        useRepository: "${REPO_URL}", sortMode: 'DESCENDING_SMART', tagFilter: '*',
                        description: "可选择指定Git Tag版本标签构建, 默认不选择是获取指定分支下的最新代码, 选择后按tag代码而非分支代码构建⚠️, 同时可作为一键回滚版本使用 🔙 ")
                choice(name: 'PUBLISH_ENV_TYPE', choices: "${NPM_RUN_PARAMS}", description: '选择指定环境和类型发布')
                string(name: 'VERSION_NUM', defaultValue: "", description: '选填 设置桌面端语义化版本号 如1.0.0 (默认不填写 自动获取之前设置的版本号并自增, 自动更新package.json内的版本号) 🖊')
                text(name: 'VERSION_DESCRIPTION', defaultValue: "${Constants.DEFAULT_VERSION_COPYWRITING}",
                        description: "填写版本描述文案 (文案会显示在钉钉通知、应用商店、Git Tag、CHANGELOG.md等, " +
                                "不填写用默认文案在钉钉、Git Tag、CHANGELOG.md则使用Git提交记录作为发布日志) 🖊 ")
                booleanParam(name: 'IS_GIT_TAG', defaultValue: "${map.is_git_tag}", description: "是否正式环境自动给Git仓库设置Tag版本和生成CHANGELOG.md变更记录以及package.json的版本号")
                booleanParam(name: 'IS_DING_NOTICE', defaultValue: "${map.is_ding_notice}", description: "是否开启钉钉群通知 📢 ")
                choice(name: 'NOTIFIER_PHONES', choices: "${contactPeoples}", description: '选择要通知的人 (钉钉群内@提醒发布结果) 📢 ')
            }

            triggers {
                // 根据提交代码自动触发CI/CD流水线 在代码库设置WebHooks连接后生效: http://jenkins.domain.com/generic-webhook-trigger/invoke?token=jenkins-desktop
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
                        token: "jenkins-desktop", // 唯一标识 env.JOB_NAME
                        causeString: 'Triggered on $ref',
                        printContributedVariables: true,
                        printPostContent: true,
                        silentResponse: false,
                        regexpFilterText: '$project_git_http_url_$git_message',
                        // 自动触发提交记录的分支作为构建代码
                        regexpFilterExpression: '^(' + "${REPO_URL}" + ')' +
                                '_(release).*$'
                )
            }

            environment {
                // 系统环境变量
                LANG = 'en_US.UTF-8'
                LC_ALL = 'en_US.UTF-8'
                SYSTEM_HOME = "$HOME" // 系统主目录
                GEM_HOME = "~/.gems" // gem环境 ~/.gems  执行gem env或bundle env查看

                NODE_VERSION = "${map.nodejs}" // nodejs版本
                CI_GIT_CREDENTIALS_ID = "${map.ci_git_credentials_id}" // CI仓库信任IDÒ
                GIT_CREDENTIALS_ID = "${map.git_credentials_id}" // Git信任ID
                PROJECT_TAG = "${map.project_tag}" // 项目标签或项目简称
                IS_AUTO_TRIGGER = false // 是否是自动触发构建
                IS_ARCHIVE = false // 是否归档
                IS_NOTICE_CHANGE_LOG = "${map.is_notice_change_log}" // 是否通知变更记录
            }

            options {
                //失败重试次数
                retry(0)
                //超时时间 job会自动被终止
                timeout(time: 1, unit: 'HOURS')
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
                            getGitBranch(map)
                            getUserInfo()
                        }
                    }
                }

                stage('获取代码') {
                    steps {
                        script {
                            pullProjectCode()
                            pullCIRepo()
                        }
                    }
                }

                stage('代码质量') {
                    when { expression { return false } }
                    steps {
                        // 只显示当前阶段stage失败  而整个流水线构建显示成功
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            script {
                                echo "代码质量, 可打通项目管理系统自动提交bug指派任务"
                                codeQualityAnalysis()
                            }
                        }
                    }
                }

                stage('自动化测试') {
                    when { expression { return false } }
                    steps {
                        script {
                            echo "自动化测试"
                        }
                    }
                }

                stage('设置版本信息') {
                    when { expression { return true } }
                    steps {
                        script {
                            setVersionInfo()
                        }
                    }
                }

                stage('安装依赖') {
                    tools {
                        // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中
                        nodejs "${NODE_VERSION}"
                    }
                    steps {
                        script {
                            echo "更新下载桌面端应用依赖 📥"
                            installDependencies()
                        }
                    }
                }

                stage('多系统构建') {
                    failFast false  // 其他阶段失败 中止parallel块同级正在进行的并行阶段
                    parallel {
                        stage('Windows系统') {
                            when {
                                beforeAgent true   // 只有在 when 条件验证为真时才会进入 agent
                                expression {
                                    return (IS_DOCKER_BUILD == false && ("${SYSTEM_TYPE}".toInteger() == GlobalVars.AllDesktop
                                            || "${SYSTEM_TYPE}".toInteger() == GlobalVars.Windows))
                                }
                            }
                            tools {
                                // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中
                                nodejs "${NODE_VERSION}"
                            }
                            steps {
                                script {
                                    echo "Windows系统应用构建"
                                    windowsBuild()
                                }
                            }
                        }
                        stage('Docker For Electron') {
                            when {
                                beforeAgent true   // 只有在 when 条件验证为真时才会进入 agent
                                expression {
                                    return (IS_DOCKER_BUILD == true && ("${SYSTEM_TYPE}".toInteger() == GlobalVars.AllDesktop
                                            || "${SYSTEM_TYPE}".toInteger() == GlobalVars.Windows))
                                }
                            }
                            agent {
                                // label "linux"
                                docker {
                                    // Electron构建环境和代码签名  构建完成自动删除容器
                                    // 文档: https://www.electron.build/multi-platform-build#docker
                                    image "electronuserland/builder:wine"
                                    // 配置映射
                                    args " -v ${PWD}:/project " +
                                            " -v ${PWD}/node-modules:/project/node_modules " +
                                            " -v /my/.cache/electron:/root/.cache/electron " +
                                            " -v /my/.cache/electron-builder:/root/.cache/electron-builder "
                                    reuseNode true // 使用根节点
                                }
                            }
                            steps {
                                script {
                                    echo "Docker环境内构建Electron方式"
                                    windowsBuild()
                                }
                            }
                        }
                        stage('MacOS系统') {
                            when {
                                beforeAgent true
                                expression {
                                    return ("${SYSTEM_TYPE}".toInteger() == GlobalVars.AllDesktop
                                            || "${SYSTEM_TYPE}".toInteger() == GlobalVars.MacOS)
                                }
                            }
                            tools {
                                // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中
                                nodejs "${NODE_VERSION}"
                            }
                            steps {
                                script {
                                    echo "MacOS系统应用构建"
                                    echo "MacOS系统Intel芯片、Apple Silicon芯片Xcode签名构建pkg、dmg安装包"
                                    macosBuild()
                                }
                            }
                        }
                        stage('Linux系统') {
                            /*   agent {
                                     //  指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
                                     label "slave-jdk11-prod"
                                 }*/
                            when {
                                beforeAgent true
                                expression {
                                    return ("${SYSTEM_TYPE}".toInteger() == GlobalVars.AllDesktop
                                            || "${SYSTEM_TYPE}".toInteger() == GlobalVars.Linux)
                                }
                            }
                            tools {
                                // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中
                                nodejs "${NODE_VERSION}"
                            }
                            steps {
                                script {
                                    echo "Linux系统应用构建"
                                    linuxBuild()
                                }
                            }
                        }
                    }
                }
                stage('Unity For Windows系统') {
                    when {
                        beforeAgent true
                        expression { return "${PROJECT_TYPE}".toInteger() == GlobalVars.desktopUnity }
                    }
                    agent {
                        // label "linux"
                        docker {
                            // Unity环境  构建完成自动删除容器
                            // 容器仓库: https://hub.docker.com/r/unityci/editor/tags?page=1&ordering=last_updated
                            // unityci/editor:ubuntu-2020.3.13f1-ios-0.13.0 、windows-mono-0.13.0、webgl-0.13.0
                            image "unityci/editor:ubuntu-${unityVersion}-windows-mono-0.13.0"
                            // Unity授权许可协议激活核心配置映射
                            args " -v ${env.WORKSPACE}/ci/_jenkins/unity/${unityActivationFile}:/root/.local/share/unity3d/Unity/Unity_lic.ulf "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            echo "Unity For Windows系统应用构建"
                            unityWindowsBuild(map)
                        }
                    }
                }

                stage('上传制品') {
                    steps {
                        script {
                            echo "上传制品"
                            uploadProducts()
                        }
                    }
                }

                stage('制作二维码') {
                    when {
                        expression { return true }
                    }
                    steps {
                        script {
                            echo "制作二维码"
                            genQRCode()
                        }
                    }
                }

                stage('应用商店') {
                    when { expression { return false } }
                    steps {
                        script {
                            echo "应用商店"
                            uploadPCMarket(map)
                        }
                    }
                }

                stage('钉钉通知') {
                    when {
                        expression { return ("${params.IS_DING_NOTICE}" == 'true') }
                    }
                    steps {
                        script {
                            dingNotice(1, "成功") // ✅
                        }
                    }
                }

                stage('发布日志') {
                    steps {
                        script {
                            // 自动打tag和生成CHANGELOG.md文件
                            gitTagLog()
                            // 发布日志
                            dingNotice(3)
                        }
                    }
                }

                stage('归档') {
                    when {
                        expression {
                            return ("${IS_ARCHIVE}" == 'true' && "${map.jenkins_node}" == "master") // 是否归档
                        }
                    }
                    steps {
                        script {
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
                        //cleanWs()
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
            }
        }
    } else if (type == "desktop-2") {  //  注意！！！ 差异性较大的Pipeline建议区分groovy文件维护

    }
}

/**
 * 常量定义类型
 */
class Constants {
    static final String MASTER_BRANCH = 'master' // 正式生产Git分支

    // 默认版本描述文案
    static final String DEFAULT_VERSION_COPYWRITING = '1. 优化了一些细节体验\n2. 修复了一些已知问题'
}

/**
 *  获取初始化参数方法
 */
def getInitParams(map) {
    // JSON_PARAMS为单独项目的初始化参数  JSON_PARAMS为key值  value为json结构  请选择jenkins动态参数中的 "文本参数" 配置  具体参数定义如下
    def jsonParams = readJSON text: "${JSON_PARAMS}"
    // 桌面端技术类型 0.原生桌面技术(如C++ For Windows、Linux, Swift For MacOS) 1.Electron 2.Flutter 3.Unity 4.ComposeMultiPlatform 5.Qt
    PROJECT_TYPE = jsonParams.PROJECT_TYPE ? jsonParams.PROJECT_TYPE.trim() : ""
    REPO_URL = jsonParams.REPO_URL ? jsonParams.REPO_URL.trim() : "" // Git源码地址
    // 默认常用构建分支 针对环境和单独任务都可自定义设置 构建无需再次选择 实现一键构建或全自动构建
    DEFAULT_GIT_BRANCH = jsonParams.DEFAULT_GIT_BRANCH ? jsonParams.DEFAULT_GIT_BRANCH.trim() : "${map.default_git_branch}"
    PROJECT_CHINESE_NAME = jsonParams.PROJECT_CHINESE_NAME ? jsonParams.PROJECT_CHINESE_NAME.trim() : "" // 自定义项目中文名称
    // npm包管理工具类型 如:  npm、yarn、pnpm
    NPM_PACKAGE_TYPE = jsonParams.NPM_PACKAGE_TYPE ? jsonParams.NPM_PACKAGE_TYPE.trim() : "npm"
    // 发布打包多环境和类型 多个按顺序逗号,分隔  npm run [test]的前端项目参数
    NPM_RUN_PARAMS = jsonParams.NPM_RUN_PARAMS ? jsonParams.NPM_RUN_PARAMS.trim().replace(",", "\n") : ""
    // 是否使用Docker容器环境方式构建打包 false使用宿主机环境
    IS_DOCKER_BUILD = jsonParams.IS_DOCKER_BUILD ? jsonParams.IS_DOCKER_BUILD : false
    IS_MONO_REPO = jsonParams.IS_MONO_REPO ? jsonParams.IS_MONO_REPO : false // 是否MonoRepo单体式仓库  单仓多包
    // 设置monorepo单体仓库主包文件夹名
    MONO_REPO_MAIN_PACKAGE = jsonParams.MONO_REPO_MAIN_PACKAGE ? jsonParams.MONO_REPO_MAIN_PACKAGE.trim() : "projects"

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


    // 打包目标系统类型 0.全平台、1.Windows、2.MacOS、3.Linux
    SYSTEM_TYPE = GlobalVars.Windows
    SYSTEM_TYPE_NAME = "Windows"
    if ("${params.PUBLISH_ENV_TYPE}".contains("mac")) {
        SYSTEM_TYPE = GlobalVars.MacOS
        SYSTEM_TYPE_NAME = "MacOS"
    } else if ("${params.PUBLISH_ENV_TYPE}".contains("linux")) {
        SYSTEM_TYPE = GlobalVars.Linux
        SYSTEM_TYPE_NAME = "Linux"
    }

    // 构建环境
    BUILD_ENVIRONMENT = "development"
    // 环境类型标志
    ENV_TYPE_MARK = "内测版"
    if ("${params.PUBLISH_ENV_TYPE}".contains("dev")) {
        BUILD_ENVIRONMENT = "development"
    } else if ("${params.PUBLISH_ENV_TYPE}".contains("test")) {
        BUILD_ENVIRONMENT = "test"
    } else if ("${params.PUBLISH_ENV_TYPE}".contains("prod")) {
        BUILD_ENVIRONMENT = "production"
        ENV_TYPE_MARK = "正式版"
    }

    // 默认桌面端版本号
    DESKTOP_VERSION_NUM = "1.0.0"
    // 版本存储文件
    VERSION_JSON_FILE = "package.json"

    unityVersion = "2020.3.13f1"  // unity编辑器版本
    unityActivationFile = "Unity_v2020.x.ulf" // unity激活许可文件名称

    packageOssUrl = "" // 包访问Url
    qrCodeOssUrl = "" // 二维码访问url
    packageSuffixName = "" // 包后缀名称 如.exe .dmg .deb等
    packageSize = "" // 包大小

}

/**
 *  获取Git分支信息
 */
def getGitBranch(map) {
    BRANCH_NAME = "${params.GIT_BRANCH}"  // Git分支

    try {
        echo "$git_event_name"
        IS_AUTO_TRIGGER = true
    } catch (e) {
    }

    if ("${IS_AUTO_TRIGGER}" == 'true') { // 自动触发构建
        BRANCH_NAME = "$ref".replaceAll("refs/heads/", "")  // 自动获取构建分支
    }
    println "Git构建分支是: ${BRANCH_NAME} 📇"
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
 * 获取代码
 */
def pullProjectCode() {
    // 未获取到参数 兼容处理 因为参数配置从代码拉取 必须先执行jenkins任务才能生效
    if (!params.GIT_TAG) {
        params.GIT_TAG = GlobalVars.noGit
    }
    // 获取应用打包代码
    if (params.GIT_TAG == GlobalVars.noGit) {
        // git url: "${REPO_URL}", branch: "${BRANCH_NAME}", credentialsId: "${GIT_CREDENTIALS_ID}"
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
}

/**
 * 代码质量分析
 */
def codeQualityAnalysis() {

}

/**
 * 设置版本信息
 */
def setVersionInfo() {
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.electron) {
        // 设置版本号和描述
        setVersion()
        // 获取应用名称
        getProjectName()
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopUnity) {

    }
}

/**
 * 设置版本号和描述
 */
def setVersion() {
    try {
        // 读取版本信息
        def versionJson = readJSON file: "${VERSION_JSON_FILE}", text: ''
        if ("${params.VERSION_NUM}".trim() == "") { // 没有手动输入版本号情况
            if (params.GIT_TAG == GlobalVars.noGit) {
                // 自增版本号
                def newVersion = Utils.genSemverVersion(versionJson.version)
                println("自增版本号: " + newVersion)
                DESKTOP_VERSION_NUM = newVersion
            } else { // 回滚版本情况
                DESKTOP_VERSION_NUM = params.GIT_TAG
            }
        } else { // 手动输入版本号情况
            DESKTOP_VERSION_NUM = params.VERSION_NUM
        }
        versionJson.version = DESKTOP_VERSION_NUM
        // 写入本地版本文件
        writeJSON file: "${VERSION_JSON_FILE}", json: versionJson, pretty: 2

    } catch (e) {
        println(e.getMessage())
        println("设置版本号和描述失败 ❌")
    }
}

/**
 * 获取应用名称
 */
def getProjectName() {
    try {
        // 读取package.json文件内build.productName字段应用名称
        def projectConfigFile = "${VERSION_JSON_FILE}"
        // 考虑Monorepo代码组织方式
        if ("${IS_MONO_REPO}" == "true") {
            projectConfigFile = "${env.WORKSPACE}/${PROJECT_NAME}/" + "${projectConfigFile}"
        }
        def projectConfigJson = readJSON file: "${projectConfigFile}", text: ''
        def projectName = projectConfigJson.build.productName
        if (projectName != "") {
            PROJECT_CHINESE_NAME = projectName  // projectName要判断一下是否是中文
        }
        println(projectName)
    } catch (e) {
        println(e.getMessage())
        println("获取代码内应用名称失败 ❌ ")
    }
}

/**
 * 安装依赖
 */
def installDependencies() {
    // 多种POSIX-compliant 操作系统（如 Linux，MacOS 及 BSD 等）上运行 Windows 应用的兼容层 Apple在Catalina 10.15中放弃了对32位可执行文件的支持
    // brew install wine-stable
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.electron) {
        // Electron 11.0.0 已原生支持 Apple Silicon (arm64) 设备  需要在Apple Silicon构建需要升级到Electron 11.0.0以上
        // 初始化Node环境变量
        Node.initEnv(this)
        // Node环境设置镜像
        Node.setElectronMirror(this)
        // 定义Electron项目打包文件目录
        electronPackageFile = "build"

        // 安装依赖
        sh 'yarn'
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopFlutter) {
        // 初始化环境变量
        Flutter.initEnv(this)

        // 构建应用 前置条件
        Flutter.buildPrecondition(this)
    }

}

/**
 * Windows系统构建
 */
def windowsBuild() {
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.electron) {
        packageSuffixName = "exe" // Windows新安装格式msix
        multiSystemBuild()
    }
}

/**
 * Flutter For Windows系统构建
 */
def flutterWindowsBuild(map) {
    // 删除构建产物
    // sh "rm -rf  "

    // Flutter For Desktop构建Windows系统安装包
    // 注意不同平台的包需要在对于的平台构建 如Windows包需要Windows系统构建
    Flutter.buildWindowsDesktop(this)

    //packageSize = Utils.getFolderSize(this, "")
    println("Flutter For Windows打包成功 ✅")
}

/**
 * Unity For Windows系统构建
 */
def unityWindowsBuild(map) {
    jenkinsConfigDir = "${env.WORKSPACE}/ci/_jenkins"
    // fastlane配置文件CI库位置前缀
    fastlaneConfigDir = "${jenkinsConfigDir}/fastlane"
    // 同步打包执行的构建文件
    Unity.syncBuildFile(this)

    // unity命令构建的目标目录
    unityWindowsPackagesOutputDir = "windows"

    // 删除Unity构建产物
    sh "rm -rf ./${unityWindowsPackagesOutputDir} "

    // Unity构建打包
    Unity.build(this, "Win64")

    packageSize = Utils.getFolderSize(this, "${unityWindowsPackagesOutputDir}")
    println("Unity For Windows打包成功 ✅")
}

/**
 *  MacOS系统构建 除应用安装外 提供给UI视觉测试  UI设计师一般是MacOS系统
 */
def macosBuild() {
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.electron) {
        packageSuffixName = "dmg" // pkg
        // MacOS签名打包的p12配置常量   代码签名文档: https://www.electron.build/code-signing
        env.CSC_LINK = "/Users/$USER/Library/macos.p12" // p12密钥保存的位置
        env.CSC_KEY_PASSWORD = 1  // 密钥密码
        // MacOS包需要在MacOS系统上构建签名  签名需要提供macos相关的p12证书位置CSC_LINK和密码 放在环境变量内
        multiSystemBuild()
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopFlutter) {
        // Flutter For Desktop构建MacOS系统安装包
        Flutter.buildMacOSDesktop(this)
    }
}

/**
 *  Linux系统构建
 */
def linuxBuild() {
    /* pullProjectCode()
     installDependencies()*/

    packageSuffixName = "deb"
    multiSystemBuild()
}

/**
 * 多系统构建
 */
def multiSystemBuild() {
    println("执行桌面端构建 🏗️  ")
    // Github下载失败或慢  请配置hosts中DNS映射
    // 执行构建命令
    sh "npm run ${params.PUBLISH_ENV_TYPE}"

    // package.json内的build的各平台的构建产物名称   "artifactName": "${productName}_Windows_${version}.${packageSuffixName}"

    /* buildPackageName = "${PROJECT_CHINESE_NAME}-${SYSTEM_TYPE_NAME}-v${DESKTOP_VERSION_NUM}_${env.BUILD_NUMBER}-${Utils.formatDate('yyyy-MM-dd_HH:mm')}"
     sh "mv ./${electronPackageFile}/*.${packageSuffixName} ${buildPackageName}.${packageSuffixName}" */

    // 包路径和名称
    buildPackagePath = Utils.getShEchoResult(this, "find ${electronPackageFile}/*.${packageSuffixName}")
    buildPackageName = "${buildPackagePath}".replace("${electronPackageFile}/", "").replaceAll(".${packageSuffixName}", "")

    packageSize = Utils.getFileSize(this, "${electronPackageFile}/${buildPackageName}.${packageSuffixName}")
    println("${SYSTEM_TYPE_NAME}桌面端应用打包成功 ✅")
}

/**
 * 上传制品到OSS
 */
def uploadProducts() {
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.electron) {
        def sourceFile = "${env.WORKSPACE}/${electronPackageFile}/${buildPackageName}.${packageSuffixName}" // 源文件
        def targetFile = "desktop/${env.JOB_NAME}/${BUILD_ENVIRONMENT}/${buildPackageName}.${packageSuffixName}" // 目标文件
        packageOssUrl = AliYunOSS.upload(this, sourceFile, targetFile)
        try {
            def updateFileName = "latest.yml"
            def sourceYamlFile = "${env.WORKSPACE}/${electronPackageFile}/${updateFileName}"
            def targetYamlFile = "desktop/${env.JOB_NAME}/${BUILD_ENVIRONMENT}/${updateFileName}"
            AliYunOSS.upload(this, sourceYamlFile, targetYamlFile)
        } catch (e) {
            println e.getMessage()
            println "Electron应用内升级yml文件上传失败"
        }
        sh "rm -f ${sourceFile}"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopFlutter) {

    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopUnity) {
        // 将exe文件和_Data打包成一个zip包方便分发
        sh " zip -r ${unityWindowsPackagesOutputDir}.zip ${unityWindowsPackagesOutputDir} "

        def sourceFile = "${env.WORKSPACE}/${unityWindowsPackagesOutputDir}.zip" // 源文件
        def targetFile = "desktop/${env.JOB_NAME}/${env.BUILD_NUMBER}/${unityWindowsPackagesOutputDir}.zip" // 目标文件
        packageOssUrl = AliYunOSS.upload(this, sourceFile, targetFile)
        sh "rm -f ${sourceFile}"
    }

    println "${packageOssUrl}"
    println("上传制品到OSS成功 ✅")
}

/**
 * 生成二维码
 */
def genQRCode() {
    imageSuffixName = "png"
    sh "rm -f *.${imageSuffixName}"
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.electron) {
        QRCode.generate(this, "${packageOssUrl}", "${buildPackageName}")
        def sourceFile = "${env.WORKSPACE}/${buildPackageName}.${imageSuffixName}" // 源文件
        // 目标文件
        def targetFile = "desktop/${env.JOB_NAME}/${BUILD_ENVIRONMENT}/${buildPackageName}.${imageSuffixName}"
        qrCodeOssUrl = AliYunOSS.upload(this, sourceFile, targetFile)
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopFlutter) {

    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopUnity) {
        QRCode.generate(this, "${packageOssUrl}", "${unityWindowsPackagesOutputDir}")
        def sourceFile = "${env.WORKSPACE}/${unityWindowsPackagesOutputDir}.${imageSuffixName}" // 源文件
        // 目标文件
        def targetFile = "desktop/${env.JOB_NAME}/${env.BUILD_NUMBER}/${unityWindowsPackagesOutputDir}.${imageSuffixName}"
        qrCodeOssUrl = AliYunOSS.upload(this, sourceFile, targetFile)
    }
    println "生成二维码: ${qrCodeOssUrl}"
}

/**
 * 上传PC应用市场
 */
def uploadPCMarket(map) {
    // Windows应用发布到Microsoft Store市场: https://developer.microsoft.com/zh-cn/microsoft-store/ 或华为AppGallery Connect支持PC端应用市场 exe格式: https://developer.huawei.com/consumer/cn/doc/development/app/agc-help-pcapp-0000001146516651
    // MacOS应用发布到App Store市场:  https://developer.apple.com/app-store/submitting/
    // Linux应用发布到Snap Store市场: https://snapcraft.io/

}

/**
 * 归档文件
 */
def archive() {
    try {
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.electron) {
            archiveArtifacts artifacts: "/${electronPackageFile}/*.${packageSuffixName}", fingerprint: true
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopFlutter) {

        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopUnity) {

        }
    } catch (e) {
        println e.getMessage()
    }
}

/**
 * 删除打包产出物 减少磁盘占用
 */
def deletePackagedOutput() {
    try {
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.electron) {
            sh " rm -f *.${packageSuffixName} "
            sh " rm -f ${electronPackageFile}/*.${packageSuffixName} "
            sh " rm -f ${electronPackageFile}/*.zip "
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopFlutter) {

        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.desktopUnity) {

        }
    } catch (error) {
        println "删除打包产出物异常"
        println error.getMessage()
    }
}

/**
 * 总会执行统一处理方法
 */
def alwaysPost() {
    try {
        currentBuild.description = "<img src=${qrCodeOssUrl} width=250 height=250 >" +
                "<br/> <a href='${packageOssUrl}'> 👉直接下载${SYSTEM_TYPE_NAME}版制品</a> " +
                "<br/> ${PROJECT_CHINESE_NAME} ${SYSTEM_TYPE_NAME} v${DESKTOP_VERSION_NUM}  <br/> 大小: ${packageSize} " +
                "<br/> 分支: ${BRANCH_NAME} <br/> 模式: ${params.PUBLISH_ENV_TYPE} " +
                "<br/> 发布人: ${BUILD_USER}"
    } catch (e) {
        println e.getMessage()
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
    // 构建成功后生产环境并发布类型自动打tag和变更记录 指定tag方式不再重新打tag && "${BRANCH_NAME}" == Constants.MASTER_BRANCH
    if (params.IS_GIT_TAG == true && "${params.PUBLISH_ENV_TYPE}".contains("prod") && params.GIT_TAG == GlobalVars.noGit) {
        // 获取变更记录
        def gitChangeLog = ""
        if ("${Constants.DEFAULT_VERSION_COPYWRITING}" == params.VERSION_DESCRIPTION) {
            gitChangeLog = changeLog.genChangeLog(this, 100)
        } else {
            // 使用自定义文案
            gitChangeLog = "${params.VERSION_DESCRIPTION}"
        }
        // 获取版本号
        def tagVersion = "${DESKTOP_VERSION_NUM}"
        // 生成tag和变更日志
        gitTagLog.genTagAndLog(this, tagVersion, gitChangeLog, "${REPO_URL}", "${GIT_CREDENTIALS_ID}")
    }

    if (params.IS_GIT_TAG == true) {
        // 将版本变更文件推送到Git仓库
        Git.pushFile(this, "${VERSION_JSON_FILE}", "chore(release): 发布${ENV_TYPE_MARK} v${DESKTOP_VERSION_NUM}")
    }
}

/**
 * 钉钉通知
 * @type 0 失败 1 构建完成  3 变更记录 4 审核通知
 * @msg 自定义消息* @atMobiles 要@的手机号
 */
def dingNotice(int type, msg = '', atMobiles = '') {
    if ("${params.IS_DING_NOTICE}" == 'true') { // 是否钉钉通知
        println("钉钉通知: " + params.NOTIFIER_PHONES)
        def rollbackTag = ""
        if (params.GIT_TAG != GlobalVars.noGit) {
            rollbackTag = "**Git Tag构建版本: ${params.GIT_TAG}**" // Git Tag版本添加标识
        }
        // 支持多个钉钉群通知
        dingTalkIds.each { dingId ->
            def durationTimeString = "${currentBuild.durationString.replace(' and counting', '').replace('sec', 's')}".replace(' ', '')
            if (type == 0) { // 失败
                dingtalk(
                        robot: "${dingId}",
                        type: 'MARKDOWN',
                        title: 'CI/CD桌面端失败通知',
                        text: [
                                "### [${env.JOB_NAME}#${env.BUILD_NUMBER} ${PROJECT_TAG}](${env.BUILD_URL})${ENV_TYPE_MARK}${SYSTEM_TYPE_NAME}项目${msg}",
                                "#### 请及时处理 🏃",
                                "###### ** 流水线失败原因: [运行日志](${env.BUILD_URL}console) 👈 **",
                                "###### Jenkins地址  [查看](${env.JENKINS_URL})   源码地址  [查看](${REPO_URL})",
                                "###### 发布人: ${BUILD_USER}   持续时间: ${durationTimeString}",
                                "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})"
                        ],
                        at: ["${BUILD_USER_MOBILE}"]
                )
            } else if (type == 1) { // 构建完成
                def notifierPhone = params.NOTIFIER_PHONES.split("-")[1].trim()
                if (notifierPhone == "oneself") { // 通知自己
                    notifierPhone = "${BUILD_USER_MOBILE}"
                }
                dingtalk(
                        robot: "${dingId}",
                        type: 'ACTION_CARD',
                        title: "${PROJECT_CHINESE_NAME} ${SYSTEM_TYPE_NAME} v${DESKTOP_VERSION_NUM} 发布通知",
                        text: [
                                "![screenshot](${qrCodeOssUrl})",
                                "### [${PROJECT_CHINESE_NAME}${PROJECT_TAG} ${ENV_TYPE_MARK}${SYSTEM_TYPE_NAME} 🖥  v${DESKTOP_VERSION_NUM} #${env.BUILD_NUMBER}](${env.JOB_URL})",
                                "###### ${rollbackTag}",
                                "##### 版本信息",
                                "- 构建分支: ${BRANCH_NAME}",
                                "- 大小: ${packageSize}   模式: ${params.PUBLISH_ENV_TYPE}",
                                "- 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                                "##### 制品分发连接: [${packageOssUrl}](${packageOssUrl})",
                                "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL})",
                                "###### 发布人: ${BUILD_USER}   持续时间: ${durationTimeString}",
                        ],
                        btns: [
                                [
                                        title    : "${SYSTEM_TYPE_NAME}版直接下载 📥",
                                        actionUrl: "${packageOssUrl}"
                                ]
                        ],
                        at: [notifierPhone == '110' ? '' : notifierPhone]
                )
            } else if (type == 3) { // 变更记录
                if ("${IS_NOTICE_CHANGE_LOG}" == 'true') {
                    def gitChangeLog = ""
                    if ("${Constants.DEFAULT_VERSION_COPYWRITING}" == params.VERSION_DESCRIPTION) {
                        gitChangeLog = changeLog.genChangeLog(this, 10)
                    } else {
                        // 使用自定义文案
                        gitChangeLog = "${params.VERSION_DESCRIPTION}".replace("\\n", "\\n ##### ")
                    }

                    if ("${gitChangeLog}" != GlobalVars.noChangeLog) {
                        dingtalk(
                                robot: "${dingId}",
                                type: 'MARKDOWN',
                                title: "${PROJECT_CHINESE_NAME} ${SYSTEM_TYPE_NAME} v${DESKTOP_VERSION_NUM} 发布日志",
                                text: [
                                        "### ${PROJECT_CHINESE_NAME}${PROJECT_TAG} ${ENV_TYPE_MARK}${SYSTEM_TYPE_NAME} 🖥  v${DESKTOP_VERSION_NUM} 发布日志 🎉",
                                        "#### 打包模式: ${params.PUBLISH_ENV_TYPE}",
                                        "${gitChangeLog}",
                                        ">  👉  前往 [变更日志](${REPO_URL.replace('.git', '')}/blob/${BRANCH_NAME}/CHANGELOG.md) 查看",
                                        "###### 发布人: ${BUILD_USER}",
                                        "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})"
                                ],
                                at: []
                        )
                    }
                }
            } else if (type == 4) { // 应用商店

            }
        }
    }
}
