#!groovy
import groovy.json.JsonSlurper
import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*
import shared.library.devops.ChangeLog
import shared.library.devops.GitTagLog

/**
 * @author 潘维吉
 * @description 通用核心共享Pipeline脚本库 针对小程序
 * 技术类型 1. 原生小程序 2. Taro跨端小程序 3. uni-app跨端小程序 4. mpvue跨端小程序 5. Remax跨端小程序
 */
def call(String type = 'wx-mini', Map map) {
    echo "Pipeline共享库脚本类型: ${type}, jenkins分布式节点名: ${map.jenkins_node}"
    // 应用共享方法定义
    changeLog = new ChangeLog()
    gitTagLog = new GitTagLog()

    // 初始化参数
    getInitParams(map)

    if (type == "wx-mini") { // 针对微信小程序项目Pipeline脚本
        pipeline {
            agent {
                label "${map.jenkins_node}"  // 指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
            }

            parameters {
                choice(name: 'BUILD_TYPE', choices: ["${Constants.TRIAL_TYPE}", "${Constants.DEVELOP_TYPE}", "${Constants.RELEASE_TYPE}"],
                        description: "发布构建类型  1. ${Constants.DEVELOP_TYPE}开发版生成预览码  2. ${Constants.TRIAL_TYPE}体验版上传公众平台并自动设置为体验版 " +
                                " 3. ${Constants.RELEASE_TYPE}正式版(自动提审、打tag版本和生成变更记录等, 确保线上无正在待审核的版本)")
                gitParameter(name: 'GIT_BRANCH', type: 'PT_BRANCH', defaultValue: "${DEFAULT_GIT_BRANCH}", selectedValue: "DEFAULT",
                        useRepository: "${REPO_URL}", sortMode: 'ASCENDING', branchFilter: 'origin/(.*)',
                        description: '选择要构建的Git分支 默认: ' + "${DEFAULT_GIT_BRANCH} (可自定义配置具体任务的默认常用分支, 实现一键或全自动构建)")
                gitParameter(name: 'GIT_TAG', type: 'PT_TAG', defaultValue: GlobalVars.noGit, selectedValue: GlobalVars.noGit,
                        useRepository: "${REPO_URL}", sortMode: 'DESCENDING_SMART', tagFilter: '*',
                        description: "可选择指定Git Tag版本标签构建, 默认不选择是获取指定分支下的最新代码, 选择后按tag代码而非分支代码构建⚠️, 同时可作为一键回滚版本使用 🔙 ")
                string(name: 'VERSION_NUM', defaultValue: "", description: '选填 设置小程序的语义化版本号 如1.0.0 (默认不填写 自动获取之前设置的版本号并自增) 🖊')
                text(name: 'VERSION_DESC', defaultValue: "${Constants.MINI_DEFAULT_VERSION_COPYWRITING}",
                        description: '填写小程序版本描述文案(文案会显示在钉钉通知、小程序平台、Git Tag、CHANGELOG.md等, ' +
                                '不填写用默认文案在钉钉、Git Tag、CHANGELOG.md则使用Git提交记录作为发布日志) 🖊')
                booleanParam(name: 'IS_AUTO_SUBMIT_FOR_REVIEW', defaultValue: true,
                        description: "是否自动提交审核 (⚠️确保CI机器人提交的已为体验版并在小程序平台列表第一个, 同时满足${Constants.RELEASE_TYPE}正式版才会自动提审)")
                choice(name: 'CI_ROBOT', choices: "1\n2\n3\n4\n5\n6\n7\n8\n9\n10",
                        description: '选择指定的ci机器人 (同一个机器人上传成功后自动设置为体验版本, 不同机器人实现多版本并存) 🤖')
                booleanParam(name: 'IS_GIT_TAG', defaultValue: "${map.is_git_tag}", description: "是否正式环境自动给Git仓库设置Tag版本和生成CHANGELOG.md变更记录")
                booleanParam(name: 'IS_DING_NOTICE', defaultValue: "${map.is_ding_notice}", description: "是否开启钉钉群通知 📢 ")
                choice(name: 'NOTIFIER_PHONES', choices: "${contactPeoples}", description: '选择要通知的人 (钉钉群内@提醒发布结果) 📢 ')
            }

            triggers {
                // 根据提交代码自动触发CI/CD流水线 在代码库设置WebHooks连接后生效: http://jenkins.domain.com/generic-webhook-trigger/invoke?token=jenkins-mini
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
                        token: "jenkins-mini", // 唯一标识 env.JOB_NAME
                        causeString: 'Triggered on $ref',
                        printContributedVariables: true,
                        printPostContent: true,
                        silentResponse: false,
                        regexpFilterText: '$project_git_http_url_$git_message',
                        // 自动触发提交记录的分支作为构建代码
                        // 针对monorepo单仓多包仓库 可根据changed_files变量中变更文件所在的项目匹配自动触发构建具体的分支
                        regexpFilterExpression: '^(' + "${REPO_URL}" + ')' +
                                '_(release).*$'
                )
            }

            environment {
                NODE_VERSION = "${map.nodejs}" // nodejs版本
                CI_GIT_CREDENTIALS_ID = "${map.ci_git_credentials_id}" // CI仓库信任ID
                GIT_CREDENTIALS_ID = "${map.git_credentials_id}" // Git信任ID
                DING_TALK_CREDENTIALS_ID = "${map.ding_talk_credentials_id}" // 钉钉授信ID 系统设置里面配置 自动生成
                PROJECT_TAG = "${map.project_tag}" // 项目标签或项目简称
                IS_AUTO_TRIGGER = false // 是否是自动触发构建
                IS_NOTICE_CHANGE_LOG = "${map.is_notice_change_log}" // 是否通知变更记录
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
                                echo "代码质量, 可打通项目管理平台自动提交bug指派任务"
                                codeQualityAnalysis()
                            }
                        }
                    }
                }

                stage('自动化测试') {
                    when { expression { return false } }
                    steps {
                        script {
                            // Appium或Playwright自动录制 生成回归和冒烟等测试脚本
                            echo "Appium或Playwright自动化测试"
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

                stage('编译构建') {
                    /*   when {
                           beforeAgent true  // 只有在 when 条件验证为真时才会进入 agent
                           expression { return ("${PROJECT_TYPE}".toInteger() == GlobalVars.taro) }
                       }*/
                    tools {
                        // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中
                        nodejs "${NODE_VERSION}"
                    }
                    steps {
                        script {
                            buildProject()
                        }
                    }
                }

                stage('预览代码') {
                    when {
                        expression { return ("${params.BUILD_TYPE}" == "${Constants.DEVELOP_TYPE}") }
                    }
                    steps {
                        script {
                            previewUpload()
                        }
                    }
                }

                stage('上传代码') {
                    when {
                        expression { return ("${params.BUILD_TYPE}" == "${Constants.TRIAL_TYPE}" || "${params.BUILD_TYPE}" == "${Constants.RELEASE_TYPE}") }
                    }
                    steps {
                        script {
                            previewUpload()
                        }
                    }
                }

                stage('小程序信息') {
                    when {
                        expression { return true }
                    }
                    steps {
                        script {
                            miniInfo()
                        }
                    }
                }

                stage('上传二维码') {
                    when {
                        expression { return ("${params.BUILD_TYPE}" == "${Constants.DEVELOP_TYPE}") }
                    }
                    steps {
                        script {
                            previewImageUpload(map)
                        }
                    }
                }

                stage('提审授权') {
                    when {
                        expression {
                            return ("${params.BUILD_TYPE}" == "${Constants.RELEASE_TYPE}"
                                    && "${params.IS_AUTO_SUBMIT_FOR_REVIEW}" == 'true')
                        }
                    }
                    steps {
                        // 只显示当前阶段stage失败  而整个流水线构建显示成功
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            //  script {
                            parallel( // 步骤内并发执行
                                    '提审': {
                                        submitAudit()
                                    },
                                    '授权': {
                                        submitAuthorization(map)
                                    })
                            // }
                        }
                    }
                }

                stage('发布上架') {
                    when { expression { return false } }
                    steps {
                        script {
                            echo "发布上架"
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
                            echo "发布日志"
                            // 自动打tag和生成CHANGELOG.md文件
                            gitTagLog()
                            // 发布日志
                            dingNotice(3)
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
    } else if (type == "wx-mini-2") { //   注意！！！ 差异性较大的Pipeline建议区分groovy文件维护

    }
}

/**
 * 常量定义类型
 */
class Constants {
    static final String MASTER_BRANCH = 'master' // 正式生产Git分支

    // 小程序平台
    static final String WEIXIN_MINI = 'weixin' // 微信小程序
    static final String ALIPAY_MINI = 'alipay' // 支付宝小程序

    // develop开发版生成预览码  trial体验版上传公众平台 release提交审核发布上架
    static final String DEVELOP_TYPE = 'develop'
    static final String TRIAL_TYPE = 'trial'
    static final String RELEASE_TYPE = 'release'

    // 小程序默认版本描述文案
    static final String MINI_DEFAULT_VERSION_COPYWRITING = '1. 优化了一些细节体验\n2. 修复了一些已知问题 \n'

    // 微信公众平台url地址
    static final String WECHAT_PUBLIC_PLATFORM_URL = "https://mp.weixin.qq.com/"
}

/**
 * 获取初始化参数方法
 */
def getInitParams(map) {
    // echo sh(returnStdout: true, script: 'env')
    // JSON_PARAMS为单独项目的初始化参数  JSON_PARAMS为key值  value为json结构  请选择jenkins动态参数中的 "文本参数" 配置  具体参数定义如下
    def jsonParams = readJSON text: "${JSON_PARAMS}"
    // println "${jsonParams}"
    PROJECT_TYPE = jsonParams.PROJECT_TYPE ? jsonParams.PROJECT_TYPE.trim() : ""  // 项目类型 1 原生小程序 2 Taro跨端小程序
    REPO_URL = jsonParams.REPO_URL ? jsonParams.REPO_URL.trim() : "" // Git地址
    // 默认常用构建分支 针对环境和单独任务都可自定义设置 构建无需再次选择 实现一键构建或全自动构建
    DEFAULT_GIT_BRANCH = jsonParams.DEFAULT_GIT_BRANCH ? jsonParams.DEFAULT_GIT_BRANCH.trim() : "${map.default_git_branch}"
    // 原生小程序是否需要npm
    IS_MINI_NATIVE_NEED_NPM = jsonParams.IS_MINI_NATIVE_NEED_NPM ? jsonParams.IS_MINI_NATIVE_NEED_NPM : false
    // npm包管理工具类型 如:  npm、yarn、pnpm
    NPM_PACKAGE_TYPE = jsonParams.NPM_PACKAGE_TYPE ? jsonParams.NPM_PACKAGE_TYPE.trim() : "npm"
    NPM_RUN_PARAMS = jsonParams.NPM_RUN_PARAMS ? jsonParams.NPM_RUN_PARAMS.trim() : "" // npm run [test]的前端项目参数
    NPM_BUILD_DIRECTORY = jsonParams.NPM_BUILD_DIRECTORY ? jsonParams.NPM_BUILD_DIRECTORY.trim() : "" // npm 构建目录
    PROJECT_CHINESE_NAME = jsonParams.PROJECT_CHINESE_NAME ? jsonParams.PROJECT_CHINESE_NAME.trim() : "" // 自定义项目中文名称
    // 小程序体验码url地址
    MINI_EXPERIENCE_CODE_URL = jsonParams.MINI_EXPERIENCE_CODE_URL ? jsonParams.MINI_EXPERIENCE_CODE_URL.trim() : ""
    // 小程序码url地址
    MINI_CODE_URL = jsonParams.MINI_CODE_URL ? jsonParams.MINI_CODE_URL.trim() : ""
    // URL Scheme打开已发布的小程序 都可基于浏览器打开 iOS和Android打开方式不同  公众平台上手动生成
    MINI_URL_SCHEME = jsonParams.MINI_URL_SCHEME ? jsonParams.MINI_URL_SCHEME.trim() : ""
    // 是否是单体式仓库
    IS_MONO_REPO = jsonParams.IS_MONO_REPO ? jsonParams.IS_MONO_REPO : false
    // 单体式仓库业务项目代码工程目录名称
    PROJECT_NAME = jsonParams.PROJECT_NAME ? jsonParams.PROJECT_NAME.trim() : ""

    try {
        miniReviewInfo = " --demoUser=''  --demoPassword='' "
        // 小程序信息
        miniJobInfo = readJSON text: "${MINI_JOB_INFO}"
        if (miniJobInfo) {
            // 审核需要登录的账号密码
            def demoUser = miniJobInfo.demoUser
            def demoPassword = miniJobInfo.demoPassword
            miniReviewInfo = " --demoUser='${demoUser}'  --demoPassword='${demoPassword}' "
        }
    } catch (e) {
        //println("获取小程序信息失败")
        println(e.getMessage())
    }

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

    // 默认小程序版本号
    MINI_VERSION_NUM = "1.0.0"
    // 版本号和版本记录存储文件
    VERSION_FILE = "${PROJECT_NAME}" + "version.json"

    // 自动提审是否成功
    isSubmitAuditSucceed = false
    // 小程序总包大小
    miniTotalPackageSize = ""
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
                if ("${BUILD_USER_MOBILE}".trim() == "") {
                    BUILD_USER_MOBILE = BUILD_USER // 未填写钉钉插件手机号则使用用户名代替显示
                }
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
    if (params.GIT_TAG == GlobalVars.noGit) { // 基于分支最新代码构建
        //git url: "${REPO_URL}", branch: "${BRANCH_NAME}", credentialsId: "${GIT_CREDENTIALS_ID}"
        // 对于大体积仓库或网络不好情况 自定义代码下载超时时间 默认10分钟
        checkout([$class           : 'GitSCM',
                  branches         : [[name: "*/${BRANCH_NAME}"]],
                  extensions       : [[$class: 'CloneOption', timeout: 30]],
                  gitTool          : 'Default',
                  userRemoteConfigs: [[credentialsId: "${GIT_CREDENTIALS_ID}", url: "${REPO_URL}"]]
        ])
    } else { // 基于Git标签代码构建
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
    // 项目配置JSON文件是存在主工程代码中 说明不是标准的单体式monorepo仓库 自动重新设置覆盖任务配置的错误参数
    if ("${IS_MONO_REPO}" == "true") {
        projectConfigFile = "project.config.json"
        if (fileExists("${projectConfigFile}")) {
            println("不是标准的小程序单体式monorepo仓库 自动重新设置覆盖任务配置的错误参数")
            IS_MONO_REPO = false
            PROJECT_NAME = "" // 单体式仓库业务项目代码工程目录名称
            // 版本号和版本记录存储文件
            VERSION_FILE = "${PROJECT_NAME}" + "version.json"
        }
    }

    // 设置版本号和描述
    setVersion()
    // 获取版本号和描述
    getVersion()
    // 获取应用名称
    // getProjectName()
}

/**
 * 设置版本号和描述
 */
def setVersion() {
    if (!fileExists("${VERSION_FILE}")) { // 文件不存在则创建
        writeJSON file: "${VERSION_FILE}", json: [version: "${MINI_VERSION_NUM}", versionDesc: params.VERSION_DESC], pretty: 2
    }

    if ("${params.VERSION_NUM}".trim() != "") { // 手动输入版本号情况
        try {
            // 写入本地版本文件
            writeJSON file: "${VERSION_FILE}", json: [version: params.VERSION_NUM, versionDesc: params.VERSION_DESC], pretty: 2
        } catch (e) {
            println(e.getMessage())
            println("设置${VERSION_FILE}本地文件内的版本号和描述失败, 不影响流水线运行 ❌ ")
        }
    }
}

/**
 * 获取版本号和描述
 */
def getVersion() {
    try {
        if ("${params.VERSION_NUM}".trim() == "") { // 没有手动输入版本号情况
            if (params.GIT_TAG == GlobalVars.noGit && fileExists("${VERSION_FILE}")) {
                // 读取版本信息
                def versionJson = readJSON file: "${VERSION_FILE}", text: ''
                // println(versionJson.version)
                // println(versionJson.versionDesc)
                // 自增版本号
                def newVersion = Utils.genSemverVersion(this, versionJson.version)
                println("自增版本号: " + newVersion)
                MINI_VERSION_NUM = newVersion
                // 写入本地版本文件
                writeJSON file: "${VERSION_FILE}", json: [version: "${MINI_VERSION_NUM}", versionDesc: params.VERSION_DESC], pretty: 2
            } else if (params.GIT_TAG != GlobalVars.noGit) { // 回滚版本情况
                MINI_VERSION_NUM = params.GIT_TAG
            }
        } else { // 手动输入版本号情况
            MINI_VERSION_NUM = params.VERSION_NUM
        }
    } catch (e) {
        println(e.getMessage())
        println("获取${VERSION_FILE}本地文件内的版本号和描述失败, 不影响流水线运行 ❌ ")
    }
}

/**
 * 获取应用名称
 */
def getProjectName() {
    try {
        // 读取project.config.json文件内projectname字段应用名称  description项目描述

        // 考虑Monorepo代码组织方式
        if ("${IS_MONO_REPO}" == "true") {
            projectConfigFile = "${env.WORKSPACE}/${PROJECT_NAME}/" + "${projectConfigFile}"
        }
        def projectConfigJson = readJSON file: "${projectConfigFile}", text: ''
        def projectName = projectConfigJson.projectname
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
 * 构建编译打包
 */
def buildProject() {
    // 初始化Node环境变量
    Node.initEnv(this)

    // Node环境设置镜像
    Node.setMirror(this)

    dir("${env.WORKSPACE}/${PROJECT_NAME}") {
        println("安装依赖 📥")
        sh "yarn"
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.miniNativeCode) {
            // 安装微信小程序CI依赖工具   二维码生成库qrcode-terminal
            try {
                println("本地离线安装miniprogram-ci")
                sh "yarn add miniprogram-ci --dev  --offline"
            } catch (e) {
                println(e.getMessage())
                println("远程线上安装miniprogram-ci")
                sh "yarn add miniprogram-ci --dev"
            }
            //sh "npm i -D miniprogram-ci"

            // 原生小程序编译前自定义命令 支持monorepo方式多包复用
            if ("${IS_MONO_REPO}" == "true") {
                def compileFileName = "pre-compile.js"
                // monorepo兼容两种方案  1. pre-compile.js脚本复制基础包到业务包下 2. 直接用npm package.json file:"../"引入方式
                if (fileExists("${compileFileName}")) {
                    // sh "cd ../base-libs && npm install" // 基础通用包引用第三方npm 需要先install
                    sh "cd .. && cp -p ${compileFileName} ${PROJECT_NAME}/${compileFileName}"
                    sh "node ${compileFileName}" // 直接复制基础包到业务包内后引用
                }
            }

        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.taro) {
            // sh "rm -rf node_modules"
            // sh "npm install"
            sh "npm run '${NPM_RUN_PARAMS}'"
        }
    }
}

/**
 * 预览上传
 */
def previewUpload() {
    dir("${env.WORKSPACE}/${PROJECT_NAME}") {
        // 小程序配置目录
        miniConfigDir = "${env.WORKSPACE}/ci/_jenkins/mini"
        // 同步脚本和删除构建产物
        sh "cp -r ${miniConfigDir}/deploy.js ./ "
        sh "rm -f *.jpg"

        // 密钥文件是否存在代码中 用于CI授信执行
        def privateKeyFile = "private.key"
        if (!fileExists("${privateKeyFile}")) {
            println("请参考文档配置: https://developers.weixin.qq.com/miniprogram/dev/devtools/ci.html")
            error("${privateKeyFile}密钥文件不存在于项目代码工程根目录中 ❌")
        }

        try {
            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.miniNativeCode) {
                // 自动校正IS_MINI_NATIVE_NEED_NPM参数 是否存在package.json文件 来判断是否需要小程序原生构建npm
                def packageJsonFile = "package.json"
                if (fileExists("${packageJsonFile}")) {
                    // 存在package.json文件, 需要小程序原生构建npm
                    IS_MINI_NATIVE_NEED_NPM = true
                } else {
                    // 不存在package.json文件, 无需小程序原生构建npm
                    IS_MINI_NATIVE_NEED_NPM = false
                }
            }
        } catch (e) {
            println(e.getMessage())
        }

        // 微信CI返回的结果文件存储
        wxCiResultFile = "wx-ci-result.json"
        sh "rm -f ${wxCiResultFile}"
        wxPreviewQrcodeName = "preview-qrcode-v${MINI_VERSION_NUM}" // 微信预览码图片名称
        println("执行小程序自动化预览上传 🚀 ")
        // 执行自动化预览上传
        sh "node deploy.js --type=${params.BUILD_TYPE} --v=${MINI_VERSION_NUM} --desc='${params.VERSION_DESC}' " +
                " --isNeedNpm='${IS_MINI_NATIVE_NEED_NPM}' --buildDir=${NPM_BUILD_DIRECTORY} --wxCiResultFile='${wxCiResultFile}' " +
                " --qrcodeName=${wxPreviewQrcodeName} --robot=${params.CI_ROBOT}"
    }
    println("小程序预览上传成功 ✅")
}

/**
 * 小程序信息
 */
def miniInfo() {
    dir("${env.WORKSPACE}/${PROJECT_NAME}") {
        // 读取文件信息
        wxCiResult = readFile(file: "${wxCiResultFile}")
        println("${wxCiResult}")
        def jsonSlurper = new JsonSlurper()
        def jsonParams = jsonSlurper.parseText("${wxCiResult}")
        // 小程序总包大小
        miniTotalPackageSize = Utils.kbConvertMb("${jsonParams.totalPackageSize}")
        // 小程序主包大小
        try {
            miniMainPackageSize = Utils.kbConvertMb("${jsonParams.mainPackageSize}")
        } catch (e) {
            miniMainPackageSize = miniTotalPackageSize
        }
    }
    // 现在miniprogram-ci功能简陋 没有返回太多信息和提审发布等 可基于UI自动化替换重复的人工行为
}

/**
 * 预览码图片上传OSS
 */
def previewImageUpload(map) {
    wxPreviewQrcodeUrl = "" // 微信预览码图片访问Url
    // 源文件地址
    def sourceFile = "${env.WORKSPACE}/${PROJECT_NAME == "" ? "" : "${PROJECT_NAME}/"}${wxPreviewQrcodeName}.jpg"
    def targetFile = "mini/${env.JOB_NAME}/${wxPreviewQrcodeName}-${env.BUILD_NUMBER}.jpg" // 目标文件
    wxPreviewQrcodeUrl = AliYunOSS.upload(this, map, sourceFile, targetFile)
    println "${wxPreviewQrcodeUrl}"
}

/**
 * 提交审核
 */
def submitAudit() {
    // 微信小程序官方CI暂不提供自动审核和发布等功能
    // Puppeteer或Playwright基于UI操作的服务，主要提供获取体验码、送审、发布服务
    // 自动化审核提交
    try {
        timeout(time: 20, unit: 'MINUTES') { // 下载playwright支持的浏览器下载比较耗时
            PlayWright.miniPlatform(this)
            isSubmitAuditSucceed = true // 自动提审是否成功
            submitAuditMsg = "小程序自动提交审核成功 ✅ "
            println "${submitAuditMsg}"
        }
    } catch (e) {
        isSubmitAuditSucceed = false
        println("自动提交审核失败  ❌")
        println(e.getMessage())
        sh "exit 1" // 本阶段制造异常
    }
}

/**
 * 提审授权
 */
def submitAuthorization(map) {
    try {
        def screenshotFile = "mini-playwright-screenshot.png"
        sh "rm -f ${screenshotFile}"

        // 延迟等待登录授权二维码图片生成
        sleep(time: 6, unit: "SECONDS")
        // waitUntil比retry更适合等待任务 反复运行它的身体，直到它返回true。如果返回false，请稍等片刻，然后重试
        // initialRecurrencePeriod设置重试之间的初始等待时间 默认为 250 毫秒 每次失败都会将尝试之间的延迟减慢至最多 15 秒
        waitUntil(initialRecurrencePeriod: 250) {
            if (fileExists("${screenshotFile}")) {
                return true
            } else {
                println("暂未找自动生成的登录授权二维码图片, 自动等待重试中...")
                return false
            }
        }
        /*       retry(20) {
                   sleep(time: 10, unit: "SECONDS")
                   if (!fileExists("${screenshotFile}")) {
                       throw new Exception("暂未找自动生成的登录授权二维码图片, 重试N次中")
                   }
               }*/

        // 将授权二维码图片上传到OSS
        wxScreenshotFileQrcodeUrl = "" // 微信截屏访问Url
        // 源文件地址
        def sourceFile = "${env.WORKSPACE}/${screenshotFile}"
        def targetFile = "mini/${env.JOB_NAME}/${screenshotFile.replace('.png', '')}-${env.BUILD_NUMBER}.png" // 目标文件
        wxScreenshotFileQrcodeUrl = AliYunOSS.upload(this, map, sourceFile, targetFile)
        println "👉 授权登录二维码: ${wxScreenshotFileQrcodeUrl}"

        if ("${params.IS_DING_NOTICE}" == 'true') { // 是否钉钉通知
            // 钉钉@通知 扫描微信小程序平台二维码登录授权具体小程序
            DingTalk.noticeImage(this, "${DING_TALK_CREDENTIALS_ID}", "${wxScreenshotFileQrcodeUrl}",
                    "${PROJECT_CHINESE_NAME}${PROJECT_TAG}授权提审小程序二维码 👆 v${MINI_VERSION_NUM}",
                    "#### · 【${PROJECT_CHINESE_NAME}】小程序管理权限的人员扫码授权  📱  " +
                            "\n #### ·  授权二维码有效期为几分钟, 确保线上无正在待审核的版本  ⚠️ " +
                            "\n #### ·  扫码授权后会继续运行流水线" +
                            "\n ###### 扫码授权后无需人工登录公众平台操作, 即可完成小程序全自动化提交审核流程  🤫 " +
                            "\n ###### Jenkins  [运行日志](${env.BUILD_URL}console)   公众平台  [查看](${Constants.WECHAT_PUBLIC_PLATFORM_URL})" +
                            "\n ###### 发布人: ${BUILD_USER}" +
                            "\n ###### 通知时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                    "${BUILD_USER_MOBILE}")
        }
    } catch (e) {
        isSubmitAuditSucceed = false
        println("自动提审授权登录失败  ❌")
        println(e.getMessage())
        sh "exit 1"  // 本阶段制造异常
    }
    // input message: "是否在钉钉中扫码微信二维码完成登录？", ok: "完成"
}

/**
 * 总会执行统一处理方法
 */
def alwaysPost() {
    try {
        // 使用jenkins 的 description setter 插件 显示html需要在全局安全设置-》标记格式器 选择
        if ("${params.BUILD_TYPE}" == "${Constants.DEVELOP_TYPE}") { // 开发版
            currentBuild.description = "<img src=${wxPreviewQrcodeUrl} width=250 height=250 > " +
                    "<br/> 开发版预览码 ( 二维码有效期半小时 ⚠️ )"
        } else if ("${params.BUILD_TYPE}" == "${Constants.TRIAL_TYPE}") { // 体验版
            currentBuild.description = "<img src=${MINI_EXPERIENCE_CODE_URL} width=250 height=250 > " +
                    "<br/> 体验版体验码 ( 上传成功自动设置为体验版本 )" +
                    "<br/> <a href='${Constants.WECHAT_PUBLIC_PLATFORM_URL}'> 👉提交审核</a> "
        } else if ("${params.BUILD_TYPE}" == "${Constants.RELEASE_TYPE}") { // 正式版
            currentBuild.description = "<img src=${MINI_CODE_URL} width=250 height=250 > " +
                    "<br/> 正式版小程序码" +
                    "<br/> <a href='${Constants.WECHAT_PUBLIC_PLATFORM_URL}'> 👉微信公众平台</a> "
        }
        currentBuild.description += "\n  <br/> ${PROJECT_CHINESE_NAME} v${MINI_VERSION_NUM}  <br/> 大小: ${miniTotalPackageSize} " +
                " <br/> 分支: ${BRANCH_NAME} <br/> 发布人: ${BUILD_USER}"
    } catch (e) {
        println(e.getMessage())
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
    // 构建成功后生产环境并发布类型自动打tag和变更记录 指定tag方式不再重新打tag  // && "${BRANCH_NAME}" == Constants.MASTER_BRANCH
    if (params.IS_GIT_TAG == true && "${params.BUILD_TYPE}" == "${Constants.RELEASE_TYPE}" && params.GIT_TAG == GlobalVars.noGit) {
        // 获取变更记录
        def gitChangeLog = ""
        if ("${Constants.MINI_DEFAULT_VERSION_COPYWRITING}" == params.VERSION_DESC) {
            gitChangeLog = changeLog.genChangeLog(this, 100).replaceAll("\\;", "\n")
        } else {
            // 使用自定义文案
            gitChangeLog = "${params.VERSION_DESC}"
        }
        // 获取版本号
        def tagVersion = "${MINI_VERSION_NUM}"
        // monorepo单体式仓库 独立版本号Tag重复处理
        if ("${IS_MONO_REPO}" == "true") {
            tagVersion = tagVersion + "-" + "${PROJECT_NAME}".toLowerCase()
        }
        // 生成tag和变更日志
        gitTagLog.genTagAndLog(this, tagVersion, gitChangeLog, "${REPO_URL}", "${GIT_CREDENTIALS_ID}")
    }
}

/**
 * 钉钉通知
 * @type 0 失败 1 构建完成 3 变更记录
 * @msg 自定义消息* @atMobiles 要@的手机号
 */
def dingNotice(int type, msg = '', atMobiles = '') {
    if ("${params.IS_DING_NOTICE}" == 'true') { // 是否钉钉通知
        println("钉钉通知: " + params.NOTIFIER_PHONES)
        def durationTimeString = "${currentBuild.durationString.replace(' and counting', '').replace('sec', 's')}".replace(' ', '')
        def codeUrl = "" // 二维码url
        def buildTypeMsg = ""  // 构建版本类型
        def buildNoticeMsg = "" // 构建版本类型提示信息
        def rollbackTag = ""
        if (params.GIT_TAG != GlobalVars.noGit) {
            rollbackTag = "**Git Tag构建版本: ${params.GIT_TAG}**" // Git Tag版本添加标识
        }
        switch (params.BUILD_TYPE) {
            case Constants.DEVELOP_TYPE:
                codeUrl = "${wxPreviewQrcodeUrl}"
                buildTypeMsg = "开发版"
                buildNoticeMsg = "预览二维码有效期半小时 ⚠️"
                break
            case Constants.TRIAL_TYPE:
                codeUrl = "${MINI_EXPERIENCE_CODE_URL}"
                buildTypeMsg = "体验版"
                buildNoticeMsg = "上传成功自动设置为体验版 ✅ "
                break
            case Constants.RELEASE_TYPE:
                codeUrl = "${MINI_CODE_URL}"
                buildTypeMsg = "正式版"
                buildNoticeMsg = "${isSubmitAuditSucceed == true ? "${submitAuditMsg}" : "请去小程序平台手动提审(自动提审失败) ❌️"}"
                break
        }

        if (type == 0) { // 失败
            dingtalk(
                    robot: "${DING_TALK_CREDENTIALS_ID}",
                    type: 'MARKDOWN',
                    title: 'CI/CD小程序失败通知',
                    text: [
                            "### [${env.JOB_NAME}#${env.BUILD_NUMBER}](${env.BUILD_URL})${PROJECT_TAG}项目${msg}",
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

            if ("${params.IS_AUTO_SUBMIT_FOR_REVIEW}" == 'true') {
                switch (params.BUILD_TYPE) {
                    case Constants.RELEASE_TYPE:
                        // 正式版自动提审失败后通知构建人员及时处理
                        notifierPhone = "${isSubmitAuditSucceed == true ? "" : "${BUILD_USER_MOBILE}"}"
                        break
                }
            }

            dingtalk(
                    robot: "${DING_TALK_CREDENTIALS_ID}",
                    type: 'ACTION_CARD',
                    title: "${PROJECT_CHINESE_NAME} 小程序 v${MINI_VERSION_NUM} 发布通知",
                    text: [
                            "![screenshot](${codeUrl})",
                            "### [${PROJECT_CHINESE_NAME}${PROJECT_TAG}${buildTypeMsg}小程序🌱 v${MINI_VERSION_NUM} #${env.BUILD_NUMBER}](${env.JOB_URL})",
                            "###### ${rollbackTag}",
                            "##### 版本信息",
                            "- 构建分支: ${BRANCH_NAME}",
                            "- 总包: ${miniTotalPackageSize}   主包: ${miniMainPackageSize}",
                            "- 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                            "##### ${buildNoticeMsg}",
                            "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL})",
                            "###### 发布人: ${BUILD_USER}   持续时间: ${durationTimeString}"
                    ],
                    btnLayout: 'V',
                    btns: [
                            [
                                    title    : '去提交审核',
                                    actionUrl: "${Constants.WECHAT_PUBLIC_PLATFORM_URL}"
                            ],
                            [
                                    title    : "${MINI_URL_SCHEME == "" ? "去官方文档" : "打开正式版"}",
                                    actionUrl: "${MINI_URL_SCHEME == "" ? "https://developers.weixin.qq.com/miniprogram/dev/framework/" : "${MINI_URL_SCHEME}"}"
                            ]
                    ],
                    at: [notifierPhone == '110' ? '' : notifierPhone]
            )
        } else if (type == 3) { // 变更记录
            if ("${IS_NOTICE_CHANGE_LOG}" == 'true') {
                def gitChangeLog = ""
                if ("${Constants.MINI_DEFAULT_VERSION_COPYWRITING}" == params.VERSION_DESC) {
                    gitChangeLog = changeLog.genChangeLog(this, 20).replaceAll("\\;", "\n")
                } else {
                    // 使用自定义文案
                    gitChangeLog = "${params.VERSION_DESC}".replace("\\n", "\\n ##### ")
                }

                if ("${gitChangeLog}" != GlobalVars.noChangeLog) {
                    dingtalk(
                            robot: "${DING_TALK_CREDENTIALS_ID}",
                            type: 'MARKDOWN',
                            title: "${PROJECT_CHINESE_NAME} 小程序 v${MINI_VERSION_NUM} 发布日志",
                            text: [
                                    "### ${PROJECT_CHINESE_NAME}${PROJECT_TAG}${buildTypeMsg}小程序🌱 v${MINI_VERSION_NUM} 发布日志 🎉",
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

