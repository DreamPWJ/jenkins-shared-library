#!groovy
import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*
import shared.library.devops.ChangeLog
import shared.library.devops.GitTagLog

/**
 * @author 潘维吉
 * @description 通用核心共享Pipeline脚本库 针对IoT物联网
 * 类型  1. 嵌入式  2. VR AR XR  3. 元宇宙
 * 基于PlatformIO实现嵌入式固件自动化构建和OTA空中升级
 */

def call(String type = 'iot', Map map) {
    echo "Pipeline共享库脚本类型: ${type}, jenkins分布式节点名: ${map.jenkins_node} "
    // 应用共享方法定义
    changeLog = new ChangeLog()
    gitTagLog = new GitTagLog()

    // 初始化参数
    getInitParams(map)

    if (type == "iot") { // 针对标准项目
        pipeline {
            // 指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
            agent { label "${map.jenkins_node}" }

            parameters {
                choice(name: 'DEPLOY_MODE', choices: [GlobalVars.release, GlobalVars.rollback],
                        description: '选择部署方式  1. ' + GlobalVars.release + '发布 2. ' + GlobalVars.rollback +
                                '回滚(基于jenkins归档方式回滚选择' + GlobalVars.rollback + ', 基于Git Tag方式回滚请选择' + GlobalVars.release + ')')
                /*              choice(name: 'MONOREPO_PROJECT_NAME', choices: "${MONOREPO_PROJECT_NAMES}",
                                      description: "选择MonoRepo单体式统一仓库项目名称, ${GlobalVars.defaultValue}选项是MultiRepo多体式独立仓库或未配置, 大统一单体式仓库流水线可减少构建时间和磁盘空间") */
                string(name: 'VERSION_NUM', defaultValue: "", description: '选填 设置IoT物联网固件的语义化版本号 如1.0.0 (默认不填写 自动获取之前设置的版本号并自增) 🖊')
                booleanParam(name: 'IS_OTA_UPGRADE', defaultValue: "${map.is_ota_upgrade}", description: "是否开启OTA空中升级功能  🌎 ")
                gitParameter(name: 'GIT_BRANCH', type: 'PT_BRANCH', defaultValue: "${BRANCH_NAME}", selectedValue: "DEFAULT",
                        useRepository: "${REPO_URL}", sortMode: 'ASCENDING', branchFilter: 'origin/(.*)',
                        description: "选择要构建的Git分支 默认: " + "${BRANCH_NAME} (可自定义配置具体任务的默认常用分支, 实现一键或全自动构建)")
                gitParameter(name: 'GIT_TAG', type: 'PT_TAG', defaultValue: GlobalVars.noGit, selectedValue: GlobalVars.noGit,
                        useRepository: "${REPO_URL}", sortMode: 'DESCENDING_SMART', tagFilter: '*',
                        description: "DEPLOY_MODE基于" + GlobalVars.release + "部署方式, 可选择指定Git Tag版本标签构建, 默认不选择是获取指定分支下的最新代码, 选择后按tag代码而非分支代码构建⚠️, 同时可作为一键回滚版本使用 🔙 ")
                text(name: 'VERSION_DESC', defaultValue: "${Constants.IOT_DEFAULT_VERSION_COPYWRITING}",
                        description: '填写IoT物联网版本描述文案(文案会显示在钉钉通知、Git Tag、CHANGELOG.md等, ' +
                                '不填写用默认文案在钉钉、Git Tag、CHANGELOG.md则使用Git提交记录作为发布日志) 🖊')
                booleanParam(name: 'IS_GIT_TAG', defaultValue: "${map.is_git_tag}",
                        description: '是否在生产环境中自动给Git仓库设置Tag版本和生成CHANGELOG.md变更记录')
                booleanParam(name: 'IS_DING_NOTICE', defaultValue: "${map.is_ding_notice}", description: "是否开启钉钉群通知 📢 ")
                choice(name: 'NOTIFIER_PHONES', choices: "${contactPeoples}", description: '选择要通知的人 (钉钉群内@提醒发布结果) 📢 ')
            }

            triggers {
                // 根据提交代码自动触发CI/CD流水线 在代码库设置WebHooks连接后生效: http://jenkins.domain.com/generic-webhook-trigger/invoke?token=jenkins-iot
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
                        token: "jenkins-iot", // 唯一标识 env.JOB_NAME
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
                                '_(release).*$'
                )
                // 每分钟判断一次代码是否存在变化 有变化就执行
                // pollSCM('H/1 * * * *')
            }

            environment {
                // 系统环境变量
                CI_GIT_CREDENTIALS_ID = "${map.ci_git_credentials_id}" // CI仓库信任ID
                GIT_CREDENTIALS_ID = "${map.git_credentials_id}" // Git信任ID
                DING_TALK_CREDENTIALS_ID = "${map.ding_talk_credentials_id}" // 钉钉授信ID 系统设置里面配置 自动生成
                PROJECT_TAG = "${map.project_tag}" // 项目标签或项目简称
                IS_PROD = "${map.is_prod}" // 是否是生产环境
                IS_AUTO_TRIGGER = false // 是否是自动触发构建
                IS_ARCHIVE = true // 是否归档
                IS_CODE_QUALITY_ANALYSIS = false // 是否进行代码质量分析的总开关
                IS_INTEGRATION_TESTING = false // 是否进集成测试
                IS_NOTICE_CHANGE_LOG = "${map.is_notice_change_log}" // 是否通知变更记录
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
                    steps {
                        script {
                            pullProjectCode()
                            pullCIRepo()
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
                                branch 'main'
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
                        /* label "linux"*/
                        /*   docker {
                               // sonarqube环境  构建完成自动删除容器
                               image "sonarqube:community"
                               reuseNode true // 使用根节点
                           }*/
                        docker {
                            // js、jvm、php、jvm-android、python、php。 jvm-community是免费版
                            image 'jetbrains/qodana-jvm-community'
                            args " --entrypoint='' -v ${env.WORKSPACE}:/data/project/ -v ${env.WORKSPACE}/qodana-reports:/data/results/ -v $HOME/.m2/:/root/.m2/ "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        // 只显示当前阶段stage失败  而整个流水线构建显示成功
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            script {
                                // codeQualityAnalysis()
                                Qodana.analyse(this)
                            }
                        }
                    }
                }

                stage('单元测试') {
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
                            // 是否进行集成测试
                            return ("${IS_INTEGRATION_TESTING}" == 'true')
                        }
                    }
                    steps {
                        script {
                            integrationTesting()
                        }
                    }
                }

                stage('设置版本信息') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return ("${IS_OTA}" == 'true')
                        }
                    }
                    steps {
                        script {
                            setVersionInfo(map)
                        }
                    }
                }

                stage('嵌入式构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == true) }
                    }
                    agent {
                        docker {
                            // PlatformIO 环境  构建完成自动删除容器  https://hub.docker.com/r/infinitecoding/platformio-for-ci
                            image "infinitecoding/platformio-for-ci:latest"
                            args " -v /root/.platformio:/root/.platformio "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            embeddedBuildProject()
                        }
                    }
                }

                stage('上传固件') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return ("${IS_UPLOAD_OSS}" == 'true')
                        }
                    }
                    steps {
                        script {
                            uploadOss(map)
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

                stage('OTA空中升级') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return ("${IS_OTA}" == 'true')
                        }
                    }
                    steps {
                        script {
                            echo "OTA空中升级"
                            otaUpgrade(map)
                        }
                    }
                }

                stage('钉钉通知') {
                    when {
                        expression { return true }
                    }
                    steps {
                        script {
                            if ("${params.IS_DING_NOTICE}" == 'true') {
                                dingNotice(1, "成功 ✅ ") // ✅
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
                            dingNotice(3)
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
                        // deletePackagedOutput()
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

    } else if (type == "iot-2") {  // 注意！！！ 差异性较大的Pipeline建议区分groovy文件维护

    }

}

/**
 * 常量定义类型
 */
class Constants {
    // IoT物联网默认版本描述文案
    static final String IOT_DEFAULT_VERSION_COPYWRITING = '1. 优化了一些细节体验\n2. 修复了一些已知问题 \n'
}

/**
 *  获取初始化参数方法
 */
def getInitParams(map) {
    // JSON_PARAMS为单独项目的初始化参数  JSON_PARAMS为key值  value为json结构  请选择jenkins动态参数中的 "文本参数" 配置  具体参数定义如下
    def jsonParams = readJSON text: "${JSON_PARAMS}"
    // println "${jsonParams}"
    REPO_URL = jsonParams.REPO_URL ? jsonParams.REPO_URL.trim() : "" // Git源码地址
    BRANCH_NAME = jsonParams.BRANCH_NAME ? jsonParams.BRANCH_NAME.trim() : GlobalVars.defaultBranch  // Git默认分支
    PROJECT_TYPE = jsonParams.PROJECT_TYPE ? jsonParams.PROJECT_TYPE.trim() : "1"  // 项目类型 1. 嵌入式  2. VR AR XR  3. 元宇宙
    // 计算机语言类型 1. C++  2. C  3. Python 4. JavaScript 5. Rust
    COMPUTER_LANGUAGE = jsonParams.COMPUTER_LANGUAGE ? jsonParams.COMPUTER_LANGUAGE.trim() : "1"
    // 项目名 获取部署资源位置和指定构建模块名等
    PROJECT_NAME = jsonParams.PROJECT_NAME ? jsonParams.PROJECT_NAME.trim() : ""
    PROJECT_CHINESE_NAME = jsonParams.PROJECT_CHINESE_NAME ? jsonParams.PROJECT_CHINESE_NAME.trim() : "" // 自定义项目中文名称
    // 环境类型变量设置
    ENV_TYPE = jsonParams.ENV_TYPE ? jsonParams.ENV_TYPE.trim() : ""

    // 是否使用Docker容器环境方式构建打包 false使用宿主机环境
    IS_DOCKER_BUILD = jsonParams.IS_DOCKER_BUILD == "false" ? false : true
    IS_UPLOAD_OSS = jsonParams.IS_UPLOAD_OSS ? jsonParams.IS_UPLOAD_OSS : false // 是否构建产物上传到OSS
    IS_OTA = jsonParams.IS_OTA ? jsonParams.IS_OTA : params.IS_OTA_UPGRADE // 是否进行OTA空中升级
    IS_OTA_DIFF = jsonParams.IS_OTA_DIFF ? jsonParams.IS_OTA_DIFF : false // 是否进行OTA差分补丁升级方式
    IS_OTA_MD5 = jsonParams.IS_OTA_MD5 ? jsonParams.IS_OTA_MD5 : false // 是否进行OTA升级MD5签名算法
    IS_MONO_REPO = jsonParams.IS_MONO_REPO ? jsonParams.IS_MONO_REPO : false // 是否MonoRepo单体式仓库  单仓多包

    // 设置monorepo单体仓库主包文件夹名
    MONO_REPO_MAIN_PACKAGE = jsonParams.MONO_REPO_MAIN_PACKAGE ? jsonParams.MONO_REPO_MAIN_PACKAGE.trim() : "projects"
    // 嵌入式框架类型 1. Arduino  2. ESP-IDF
    IOT_FRAMEWORK_TYPE = jsonParams.IOT_FRAMEWORK_TYPE ? jsonParams.IOT_FRAMEWORK_TYPE.trim() : "1"
    // PlatformIO的多环境名称 platformio.ini配置
    PLATFORMIO_ENV = jsonParams.PLATFORMIO_ENV ? jsonParams.PLATFORMIO_ENV.trim() : ""

    // 默认统一设置项目级别的分支 方便整体控制改变分支 将覆盖单独job内的设置
    if ("${map.default_git_branch}".trim() != "") {
        BRANCH_NAME = "${map.default_git_branch}"
    }

    // 项目全名 防止项目名称重复
    FULL_PROJECT_NAME = "${PROJECT_NAME}"

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
    // IoT产物构建固件包OSS地址Url
    iotOssUrl = ""
    // IoT固件OTA升级OSS地址Url
    otaOssUrl = ""
    // IoT产物构建包大小
    iotPackageSize = ""
    // IoT产物构建固件位置
    iotPackageLocation = ""
    // IoT产物构建差分固件位置
    iotPatchPackageLocation = ""
    // IoT产物构建固件文件格式
    iotPackageType = "bin" // hex
    // 默认IoT固件版本号
    IOT_VERSION_NUM = "1.0.0"
    // 版本号和固件地址记录存储文件名称
    VERSION_FILE = "${PROJECT_NAME}" + "ota.json"
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
}

/**
 * 组装初始化shell参数
 */
def getShellParams(map) {

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
}

/**
 * 集成测试
 */
def integrationTesting() {
    try {
        PlatformIO.unitTest(this)
    } catch (e) {
        println "自动化集成测试失败 ❌"
        println e.getMessage()
    }
}

/**
 * 设置版本信息
 * 自动生成升级Json文件 包含版本号和固件地址
 */
def setVersionInfo(map) {
    firmwareUrl = "" // 固件地址
    if ("${IS_MONO_REPO}" == "true") { // 是单体式monorepo仓库
    }
    // 设置版本号和固件地址
    setVersion()
    // 获取版本号和固件地址
    getVersion()
    // 获取应用名称
    // getProjectName()
}

/**
 * 设置版本号和固件地址
 */
def setVersion() {
    if (!fileExists("${VERSION_FILE}")) { // 文件不存在则创建
        writeJSON file: "${VERSION_FILE}", json: [version: "${IOT_VERSION_NUM}", file: firmwareUrl], pretty: 2
    }

    if ("${params.VERSION_NUM}".trim() != "") { // 手动输入版本号情况
        try {
            // 写入本地版本文件
            writeJSON file: "${VERSION_FILE}", json: [version: params.VERSION_NUM, file: firmwareUrl], pretty: 2
        } catch (e) {
            println(e.getMessage())
            println("设置${VERSION_FILE}本地文件内的版本号和固件地址失败, 不影响流水线运行 ❌ ")
        }
    }
}

/**
 * 获取版本号和固件地址
 */
def getVersion() {
    try {
        if ("${params.VERSION_NUM}".trim() == "") { // 没有手动输入版本号情况
            if (params.GIT_TAG == GlobalVars.noGit && fileExists("${VERSION_FILE}")) {
                // 读取版本信息
                def versionJson = readJSON file: "${VERSION_FILE}", text: ''
                // println(versionJson.version)
                // println(versionJson.file)
                // 自增版本号
                def newVersion = Utils.genSemverVersion(this, versionJson.version)
                println("自增版本号: " + newVersion)
                IOT_VERSION_NUM = newVersion
                // 写入本地版本文件
                writeJSON file: "${VERSION_FILE}", json: [version: "${IOT_VERSION_NUM}", file: firmwareUrl], pretty: 2
            } else if (params.GIT_TAG != GlobalVars.noGit) { // 回滚版本情况
                IOT_VERSION_NUM = params.GIT_TAG
            }
        } else { // 手动输入版本号情况
            IOT_VERSION_NUM = params.VERSION_NUM
        }
    } catch (e) {
        println(e.getMessage())
        println("获取${VERSION_FILE}本地文件内的版本号和固件地址失败, 不影响流水线运行 ❌ ")
    }
}

/**
 * 设置代码中的版本信息
 */
def setCodeVersion() {
    // 对于PlatformIO平台 规范定义在src/main.cpp中的CI_OTA_FIRMWARE_VERSION关键字或使用JSON文件定义
    def versionFile = "src/main.cpp"
    if ("${IS_MONO_REPO}" == 'true') {  // 是否MonoRepo单体式仓库  单仓多包
        versionFile = "${MONO_REPO_MAIN_PACKAGE}/${PROJECT_NAME}/" + versionFile
    }
    def versionFileContent = readFile(file: "${versionFile}")
    writeFile file: "${versionFile}", text: "${versionFileContent}"
            .replaceAll("CI_OTA_FIRMWARE_VERSION", "${IOT_VERSION_NUM}")
}

/**
 * 嵌入式编译构建
 */
def embeddedBuildProject() {
    if ("${IS_OTA}" == "true") {
        // 设置代码中的版本信息
        setCodeVersion()
    }
    println("执行嵌入式程序PlatformIO构建 🏗️  ")
    PlatformIO.build(this)
    Tools.printColor(this, "嵌入式固件打包成功 ✅")
}

/**
 * 上传部署文件到OSS
 * 方便下载构建部署包
 */
def uploadOss(map) {
    // try {
    // 源文件地址
    def sourceFile = "${env.WORKSPACE}/${iotPackageLocation}"
    // 目标文件
    def targetFile = "iot/${PROJECT_NAME}/${PLATFORMIO_ENV}/${ENV_TYPE}/firmware.${iotPackageType}"
    iotOssUrl = AliYunOSS.upload(this, map, sourceFile, targetFile)
    println "${iotOssUrl}"
    Tools.printColor(this, "上传固件文件到OSS成功 ✅")

//    } catch (error) {
//        println "上传固件文件到OSS异常"
//        println error.getMessage()
//    }
}

/**
 * 人工卡点审批
 * 每一个人都有点击执行流水线权限  但是不一定有发布上线的权限 为了保证项目稳定安全等需要人工审批
 */
def manualApproval() {

}

/**
 * 获取文件MD5签名值
 */
def getMD5() {
    if ("${IS_OTA_MD5}" == "true") {
        try {
            def filePath = "" // 固件文件路径
            // 获取固件md5值用于 OTA升级安全签名校验  升级json文件中的原始md5值和http请求头中Content-MD5的md5值保持一致
            def result = Utils.getShEchoResult(this, "md5sum " + filePath)
            otaMD5 = result.split("  ")[0]
            println(otaMD5)
        } catch (e) {
            println(e.getMessage())
            println("获取${VERSION_FILE}文件MD5值失败, 不影响流水线运行 ❌ ")
        }
    }
}

/**
 * 制作OTA固件升级差分包
 */
def otaDiff(map) {
    if ("${IS_OTA_DIFF}" == "true") {
        // 差分升级是将老版本和新版本取差异部分进行增量升级，可以极大的减少下载包的流量，同时能节省存储升级固件的ROM或Flash存储空间
        // 差分算法: https://github.com/mendsley/bsdiff
        try {
            // sudo apt-get install -y bsdiff
            try {
                // 判断服务器是是否安装bsdiff 环境
                sh "bsdiff"
            } catch (error) {
                // 自动安装bsdiff环境  sudo apt-get update
                // sh "apt-get update || true"
                sh "apt-get install -y bsdiff || true"
                sh "yum install -y bsdiff || true"
                sh "brew install -y bsdiff || true"
            }
            // 命令文档: https://manpages.ubuntu.com/manpages/jammy/en/man1/bsdiff.1.html
            dir("${env.WORKSPACE}/${iotPackageLocationPath}") {
                sh " bsdiff  firmware-old.bin  firmware.bin  firmware-patch.bin " // 制作差分补丁包命令
            }
            iotPatchPackageLocation = "${iotPackageLocationPath}/firmware-patch.bin"
            // 源文件地址
            def sourceFile = "${env.WORKSPACE}/${iotPatchPackageLocation}"
            // 目标文件
            def targetFile = "iot/${PROJECT_NAME}/${PLATFORMIO_ENV}/${ENV_TYPE}/firmware.${iotPackageType}"
            iotOssUrl = AliYunOSS.upload(this, map, sourceFile, targetFile)
            println "${iotOssUrl}"
            Tools.printColor(this, "上传差分固件文件到OSS成功 ✅")

        } catch (e) {
            println(e.getMessage())
            println("制作OTA固件升级差分包失败 ❌ ")
        }
    }
}

/**
 * OTA空中升级
 */
def otaUpgrade(map) {
    // 1. 整包固件升级  2. 差分固件升级
    otaDiff(map)

    // 重新写入固件地址
    firmwareUrl = "${iotOssUrl}".trim().replace("https://", "http://") // 固件地址  去掉https协议
    writeJSON file: "${VERSION_FILE}", json: [version: "${IOT_VERSION_NUM}", file: firmwareUrl], pretty: 2

    // 将固件包上传到OTA服务器、上传设置版本号和新固件地址的JSON升级文件  嵌入式设备会自动检测升级
    // try {
    def sourceJsonFile = "${env.WORKSPACE}/${VERSION_FILE}"
    def targetJsonFile = "iot/${PROJECT_NAME}/${PLATFORMIO_ENV}/${ENV_TYPE}/${VERSION_FILE}"
    otaOssUrl = AliYunOSS.upload(this, map, sourceJsonFile, targetJsonFile)
    println "${otaOssUrl}"
    Tools.printColor(this, "上传OTA固件升级文件到OSS成功 ✅")
/*    } catch (e) {
        println e.getMessage()
        println "OTA固件升级JSON文件上传失败"
    }*/
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
        archiveArtifacts artifacts: "${iotPackageLocation}", onlyIfSuccessful: true
        if ("${IS_OTA_DIFF}" == "true") {
            // OTA差分升级 旧固件包重新命名
            sh " mv ${iotPackageLocation} ${iotPackageLocationPath}/firmware-old.bin "
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
        sh " rm -f ${iotPackageLocation} "
        //}
    } catch (error) {
        println "删除打包产出物异常"
        println error.getMessage()
    }
}

/**
 * 总会执行统一处理方法
 */
def alwaysPost() {
    // sh 'pwd'
    // cleanWs()  // 清空工作空间
    // Jenkins全局安全配置->标记格式器内设置Safe HTML支持html文本
    try {
        def releaseEnvironment = "${ENV_TYPE}"
        currentBuild.description = "${iotOssUrl.trim() != '' ? "<a href='${iotOssUrl}'> 👉 直接下载固件</a>" : ""}" +
                "<br/> ${PROJECT_CHINESE_NAME} v${IOT_VERSION_NUM}" +
                "<br/> 大小: ${iotPackageSize} <br/> 分支: ${BRANCH_NAME} <br/> 环境: ${releaseEnvironment} <br/> 发布人: ${BUILD_USER}"
    } catch (error) {
        println error.getMessage()
    }
}

/**
 * 生成tag和变更日志
 */
def gitTagLog() {
    // 未获取到参数 兼容处理 因为参数配置从代码拉取 必须先执行一次jenkins任务才能生效
    if (!params.IS_GIT_TAG && params.IS_GIT_TAG != false) {
        params.IS_GIT_TAG = true
    }
    // 构建成功后生产环境并发布类型自动打tag和变更记录  指定tag方式不再重新打tag
    if (params.IS_GIT_TAG == true && "${IS_PROD}" == 'true' && params.GIT_TAG == GlobalVars.noGit) {
        // 获取变更记录
        def gitChangeLog = ""
        if ("${Constants.IOT_DEFAULT_VERSION_COPYWRITING}" == params.VERSION_DESC) {
            gitChangeLog = changeLog.genChangeLog(this, 100).replaceAll("\\;", "\n")
        } else {
            // 使用自定义文案
            gitChangeLog = "${params.VERSION_DESC}"
        }
        // 生成语义化版本号
        tagVersion = "${IOT_VERSION_NUM}"
        // monorepo单体式仓库 独立版本号Tag重复处理
        if ("${IS_MONO_REPO}" == "true") {
            // tagVersion = tagVersion + "-" + "${PROJECT_NAME}".toLowerCase()
            tagVersion = tagVersion
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
 * @type 0 失败 1 发布通知 2 部署之前 3 变更记录
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
        def rollbackTag = ""
        if (params.GIT_TAG != GlobalVars.noGit) {
            rollbackTag = "**Git Tag构建版本: ${params.GIT_TAG}**" // Git Tag版本添加标识
        }
        def monorepoProjectName = ""
        if ("${IS_MONO_REPO}" == 'true') {
            monorepoProjectName = "MonoRepo项目: ${PROJECT_NAME}"   // 单体仓库区分项目
        }
        def buildNoticeMsg = "" // 构建版本类型提示信息
        def projectTypeName = ""
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.Embedded) {
            projectTypeName = "嵌入式"
            if ("${IS_UPLOAD_OSS}" == 'true') {
                buildNoticeMsg = "嵌入式固件"
            }
            if ("${IS_OTA}" == 'true') {
                buildNoticeMsg = buildNoticeMsg + "与OTA配置"
            }
            buildNoticeMsg = buildNoticeMsg + "上传成功 ✅ "
        }
        def releaseEnvironment = "${ENV_TYPE}"
        def envTypeMark = "内测版"  // 环境类型标志
        if ("${IS_PROD}" == 'true') {
            envTypeMark = "正式版"
        }

        if (type == 0) { // 失败
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
        } else if (type == 1) { // 发布通知
            dingtalk(
                    robot: "${DING_TALK_CREDENTIALS_ID}",
                    type: 'ACTION_CARD',
                    title: "CI/CD ${PROJECT_CHINESE_NAME} ${projectTypeName} v${IOT_VERSION_NUM} 发布通知",
                    text: [
                            "### [${PROJECT_CHINESE_NAME}${PROJECT_TAG}${envTypeMark}${projectTypeName} 📟  v${IOT_VERSION_NUM} #${env.BUILD_NUMBER} ](${env.JOB_URL})",
                            "###### ${rollbackTag}",
                            "##### 版本信息",
                            "- 构建分支: ${BRANCH_NAME}   环境: ${releaseEnvironment}",
                            "- 固件大小: ${iotPackageSize}   持续时间: ${durationTimeString}",
                            "- 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                            "##### ${buildNoticeMsg}",
                            "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL})",
                            "###### 发布人: ${BUILD_USER}  构建机器: ${NODE_LABELS}",
                    ],
                    btnLayout: 'V',
                    btns: [
                            [
                                    title    : "OTA空中升级配置",
                                    actionUrl: "${otaOssUrl}"
                            ],
                            [
                                    title    : '嵌入式固件直接下载',
                                    actionUrl: "${iotOssUrl}"
                            ]
                    ],
                    at: [notifierPhone == '110' ? '' : notifierPhone]
            )
        } else if (type == 2) { // 部署之前

        } else if (type == 3) { // 变更记录
            if ("${IS_NOTICE_CHANGE_LOG}" == 'true') {
                def gitChangeLog = changeLog.genChangeLog(this, 20).replaceAll("\\;", "\n")
                if ("${gitChangeLog}" != GlobalVars.noChangeLog) {
                    dingtalk(
                            robot: "${DING_TALK_CREDENTIALS_ID}",
                            type: 'MARKDOWN',
                            title: "${PROJECT_CHINESE_NAME}${projectTypeName} v${IOT_VERSION_NUM} 发布日志",
                            text: [
                                    "### ${PROJECT_CHINESE_NAME}${PROJECT_TAG}${envTypeMark}${projectTypeName} 📟  v${IOT_VERSION_NUM} 发布日志 🎉",
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

