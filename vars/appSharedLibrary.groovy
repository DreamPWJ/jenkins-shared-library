#!groovy
import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*
import shared.library.devops.ChangeLog
import shared.library.devops.GitTagLog

/**
 * @author 潘维吉
 * @description 通用核心共享Pipeline脚本库  针对原生Android、iOS、Flutter、React Native、Unity 技术项目
 * 基于Fastlane 、Mac mini、Docker、VMware vSphere(esxi)、VirtualBox等技术实现
 */
def call(String type = 'android-ios', Map map) {
    echo "Pipeline共享库脚本类型: ${type}, jenkins分布式节点名: ${map.jenkins_node}"
    // 应用共享方法定义
    changeLog = new ChangeLog()
    gitTagLog = new GitTagLog()

    // 初始化参数
    getInitParams(map)
    // 钉钉授信ID数组 系统设置里面配置 自动生成
    dingTalkIds = "${map.ding_talk_credentials_id}".split(",")

    if (type == "android-ios") {
        pipeline {
            agent {
                label "${map.jenkins_node}"  // 指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
            }

            parameters {
                gitParameter(name: 'GIT_BRANCH', type: 'PT_BRANCH', defaultValue: "${DEFAULT_GIT_BRANCH}", selectedValue: "DEFAULT",
                        useRepository: "${REPO_URL}", sortMode: 'ASCENDING', branchFilter: 'origin/(.*)',
                        description: "选择要构建的Git分支 默认: " + "${DEFAULT_GIT_BRANCH} (可自定义配置具体任务的默认常用分支, 实现一键或全自动构建)")
                choice(name: 'CROSS_PLATFORM_SYSTEM_TYPES', choices: "${GlobalVars.defaultValue}\n${Constants.ANDROID}\n${Constants.IOS}",
                        description: "自定义跨平台(如Flutter、React Native、Unity等)构建打包的目标系统(非跨平台的原生应用不需要选择此选项默认即可) , 跨平台项目${GlobalVars.defaultValue}选项默认是同时打包Android和iOS两个平台, 也可单独选择指定平台打包")
                choice(name: 'ANDROID_STORE_IDENTIFY', choices: "${IS_ANDROID_STORE_IDENTIFY == true ? "${CUSTOM_ANDROID_FLAVOR}\n${GlobalVars.defaultValue}" : "${GlobalVars.defaultValue}"}",
                        description: "Android自定义多渠道与Flavor打包目标 ${GlobalVars.defaultValue}选项为标准模式(打包无Flavor层级目录等)" +
                                "${IS_ANDROID_STORE_IDENTIFY == true ? ", 选择在${Constants.MASTER_BRANCH}分支和${Constants.RELEASE_BUILD}模式下渠道包会自动上传华为和小米应用商店 📲 审核上架(确保版本号正确并且线上无正在审核的版本⚠️)" : ""} 🤖")
                choice(name: 'ANDROID_PACKAGING_TYPE', choices: "${GlobalVars.defaultValue}\n${Constants.DEBUG_BUILD}\n${CUSTOM_ANDROID_BUILD_TYPE}\n${Constants.RELEASE_BUILD}",
                        description: "Android自定义打包环境 ${GlobalVars.defaultValue}选项默认是" + "${Constants.MASTER_BRANCH}" + "分支模式为 ${Constants.RELEASE_BUILD} , " +
                                "其它分支默认模式为 ${Constants.DEBUG_BUILD}, 可自定义配置具体任务的多个Android打包类型 🤖")
                // booleanParam(name: 'IS_ALL_AUTO_UPLOAD_ANDROID_STORE', defaultValue: false, description: "是否全部自动上传Android各大主流应用市场(已支持华为、小米等应用商店)")
                choice(name: 'IOS_MULTI_TARGET', choices: "${IOS_MULTI_TARGET_NAMES}${GlobalVars.defaultValue}",
                        description: "iOS自定义多Target打包 ${GlobalVars.defaultValue}选项默认是非多Target方式 🍏")
                choice(name: 'IOS_SIGN_TYPE', choices: "${Constants.IOS_SIGN_DEVELOPMENT}\n${Constants.IOS_SIGN_AD_HOC}\n${Constants.IOS_SIGN_APP_STORE}\n${Constants.IOS_SIGN_ENTERPRISE}",
                        description: "iOS打包签名导出方法 (选择${Constants.IOS_SIGN_APP_STORE}方式会自动提交审核上架App Store,  " +
                                "在${Constants.IOS_SIGN_APP_STORE}方式确保代码内版本号已更新, 线上无正在等待审核或等待开发者发布的版本, 否则可能导致整个流水线运行失败⚠️) 🍏")
                choice(name: 'IOS_PACKAGING_TYPE', choices: "${GlobalVars.defaultValue}\n${Constants.DEBUG_BUILD}\n${CUSTOM_IOS_BUILD_TYPE}\n${Constants.RELEASE_BUILD}",
                        description: "iOS自定义打包环境 ${GlobalVars.defaultValue}选项默认是${Constants.IOS_SIGN_DEVELOPMENT}方式用${Constants.DEBUG_BUILD}模式打包, " +
                                "${Constants.IOS_SIGN_APP_STORE}方式用${Constants.RELEASE_BUILD}模式打包, 可自定义配置具体任务的多个iOS打包类型(Flutter打包默认为${Constants.RELEASE_BUILD}配置) 🍏")
                booleanParam(name: 'IS_AUTO_SUBMIT_FOR_REVIEW', defaultValue: true,
                        description: "iOS是否自动提交App Store审核 (选择${Constants.IOS_SIGN_APP_STORE}方式下设置有效, 默认是自动提交审核) 🍏")
                booleanParam(name: 'IS_AUTO_RELEASE_APP_STORE', defaultValue: false,
                        description: "iOS是否在审核通过后自动上架App Store商店 (选择${Constants.IOS_SIGN_APP_STORE}方式下设置有效, 默认是自动提交审核但不自动上架) 🍏")
                booleanParam(name: 'IS_ICON_ADD_BADGE', defaultValue: "${map.is_icon_add_badge}", description: "是否在非正式环境设置App的Icon图标徽章 易于区分环境和版本")
                booleanParam(name: 'IS_GIT_TAG', defaultValue: "${map.is_git_tag}", description: "是否正式环境自动给Git仓库设置Tag版本和生成CHANGELOG.md变更记录")
                booleanParam(name: 'IS_DING_NOTICE', defaultValue: "${map.is_ding_notice}", description: "是否开启钉钉群通知 📢 ")
                choice(name: 'NOTIFIER_PHONES', choices: "${contactPeoples}", description: '选择要通知的人 (钉钉群内@提醒发布结果) 📢 ')
                gitParameter(name: 'GIT_TAG', type: 'PT_TAG', defaultValue: GlobalVars.noGit, selectedValue: GlobalVars.noGit,
                        useRepository: "${REPO_URL}", sortMode: 'DESCENDING_SMART', tagFilter: '*',
                        description: "可选择指定Git Tag版本标签构建, 默认不选择是获取指定分支下的最新代码, 选择后按tag代码而非分支代码构建⚠️, 同时可作为一键回滚版本使用 🔙 ")
                string(name: 'APP_VERSION_NUM', defaultValue: "", description: '选填 设置App的语义化版本号 如1.0.0 (默认不填写 自动获取应用代码内的版本号) 🖊 ')
                text(name: 'APP_VERSION_DESCRIPTION', defaultValue: "${Constants.APP_DEFAULT_VERSION_COPYWRITING}",
                        description: "填写APP版本描述文案 (版本文案会显示在钉钉通知、内测分发平台、Android应用商店、App Store、Git Tag、CHANGELOG.md等, " +
                                "不填写用默认文案在钉钉、Git Tag、CHANGELOG.md则使用Git提交记录作为发布日志) 🖊 ")
            }

            triggers {
                // 根据提交代码自动触发CI/CD流水线 在代码库设置WebHooks连接后生效: http://jenkins.domain.com/generic-webhook-trigger/invoke?token=jenkins-app
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
                        token: "jenkins-app", // 唯一标识 env.JOB_NAME
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
                ANDROID_SDK_ROOT = "/opt/android-sdk" // 安装android sdk环境 /Users/$USER/android 或 Library/Android/sdk
                // ANDROID_NDK_HOME="" // 安装android ndk
                GEM_HOME = "~/.gems" // gem环境 ~/.gems  执行gem env或bundle env查看
                SYSTEM_HOME = "$HOME" // 系统主目录

                CI_GIT_CREDENTIALS_ID = "${map.ci_git_credentials_id}" // CI仓库信任IDÒ
                GIT_CREDENTIALS_ID = "${map.git_credentials_id}" // Git信任ID
                PGYER_API_KEY = "${map.pgyer_api_key}" // 蒲公英apikey
                PROJECT_TAG = "${map.project_tag}" // 项目标签或项目简称
                IS_AUTO_TRIGGER = false // 是否是自动触发构建
                IS_ARCHIVE = true // 是否归档
                IS_NOTICE_CHANGE_LOG = "${map.is_notice_change_log}" // 是否通知变更记录
            }

            options {
                //失败重试次数
                retry(0)
                //超时时间 job会自动被终止
                timeout(time: 2, unit: 'HOURS')
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
                            initInfo(map)
                            getGitBranch(map)
                            getUserInfo()
                        }
                    }
                }

                stage('获取代码') {
                    steps {
                        script {
                            pullProjectCode(map)
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

                stage('Android打包签名') {
                    when {
                        beforeAgent true  // 只有在 when 条件验证为真时才会进入 agent
                        expression { return ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) }
                    }
                    agent {
                        docker {
                            // android sdk环境  构建完成自动删除容器  容器自动下载jdk需要在Jenkins内设置oracle账号1287019365@qq.com Oracle@1234
                            image "mingc/android-build-box:latest" // thyrlian/android-sdk:latest
                            // 缓存gradle工具  :ro或者 :rw 前者表示容器只读，后者表示容器对数据卷是可读可写的。默认情况下是可读可写的
                            // 挂载android sdk先执行 docker run -it --rm -v /my/android/sdk:/sdk thyrlian/android-sdk bash -c 'cp -a $ANDROID_SDK_ROOT/. /sdk'
                            // 安装jdk后再执行 cd /my/android/sdk/cmdline-tools && tools/bin/sdkmanager --update
                            args " -v /var/cache/gradle:/root/.gradle:rw  " // -v /my/android/sdk:/sdk:ro
                            reuseNode true // 使用根节点
                        }
                    }
                    /*tools {
                        jdk "${JDK_VERSION}" // android 使用gradle编译的jdk版本 jenkins内配置工具 不使用Docker镜像构建情况下才需要配置
                    }*/
                    steps {
                        script {
                            echo "Android打包APK"
                            buildPackage(map)
                        }
                    }
                }
                stage('iOS打包签名') {
                    when {
                        beforeAgent true  // 只有在 when 条件验证为真时才会进入 agent
                        expression { return ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) }
                    }
                    steps {
                        script {
                            echo "iOS打包IPA"
                            buildPackage(map)
                        }
                    }
                }
                stage('Flutter打包签名') {
                    when {
                        beforeAgent true  // 只有在 when 条件验证为真时才会进入 agent
                        expression { return ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter) }
                    }
                    tools {
                        jdk "${JDK_VERSION}" // android 使用gradle编译的jdk版本
                    }
                    steps {
                        script {
                            echo "Flutter跨平台打包"
                            buildPackage(map)
                        }
                    }
                }
                stage('React Native打包签名') {
                    when {
                        beforeAgent true  // 只有在 when 条件验证为真时才会进入 agent
                        expression { return ("${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative) }
                    }
                    tools {
                        jdk "${JDK_VERSION}" // android 使用gradle编译的jdk版本
                    }
                    steps {
                        script {
                            echo "React Native跨平台打包"
                            buildPackage(map)
                        }
                    }
                }
                stage('Unity打包Android') {
                    when {
                        beforeAgent true  // 只有在 when 条件验证为真时才会进入 agent
                        expression {
                            return ("${PROJECT_TYPE}".toInteger() == GlobalVars.unity
                                    && "${BUILD_SYSTEM_TYPES}".contains("${Constants.ANDROID}"))
                        }
                    }
                    agent {
                        // label "linux"
                        docker {
                            // Unity环境  构建完成自动删除容器
                            // 容器仓库: https://hub.docker.com/r/unityci/editor/tags?page=1&ordering=last_updated
                            // unityci/editor:ubuntu-2020.3.13f1-ios-0.13.0 、windows-mono-0.13.0、webgl-0.13.0
                            image "unityci/editor:ubuntu-${unityVersion}-android-0.13.0"
                            // Unity授权许可协议激活核心配置映射
                            args " -v ${env.WORKSPACE}/ci/_jenkins/unity/${unityActivationFile}:/root/.local/share/unity3d/Unity/Unity_lic.ulf "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            echo "Unity打包Android"
                            unityBuildPackage(map, GlobalVars.android)
                        }
                    }
                }
                stage('Unity编译iOS') {
                    when {
                        beforeAgent true  // 只有在 when 条件验证为真时才会进入 agent
                        expression {
                            return ("${PROJECT_TYPE}".toInteger() == GlobalVars.unity
                                    && "${BUILD_SYSTEM_TYPES}".contains("${Constants.IOS}"))
                        }
                    }
                    agent {
                        // label "linux"
                        docker {
                            // Unity环境  构建完成自动删除容器
                            // 容器仓库: https://hub.docker.com/r/unityci/editor/tags?page=1&ordering=last_updated
                            image "unityci/editor:ubuntu-${unityVersion}-ios-0.13.0"
                            // Unity授权许可协议激活核心配置映射
                            args " -v ${env.WORKSPACE}/ci/_jenkins/unity/${unityActivationFile}:/root/.local/share/unity3d/Unity/Unity_lic.ulf "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            echo "Unity打包iOS"
                            unityBuildPackage(map, GlobalVars.ios)
                        }
                    }
                }
                stage('Unity打包iOS') {
                    when {
                        beforeAgent true  // 只有在 when 条件验证为真时才会进入 agent
                        expression {
                            return ("${PROJECT_TYPE}".toInteger() == GlobalVars.unity
                                    && "${BUILD_SYSTEM_TYPES}".contains("${Constants.IOS}"))
                        }
                    }
                    agent {
                        label "macos-mobile"
                    }
                    steps {
                        script {
                            echo "Xcode 构建 iOS"
                            unityBuildForiOS(map)
                        }
                    }
                }

                stage('APP信息') {
                    when {
                        beforeAgent true
                        expression { return true }
                    }
                    agent {
                        docker {
                            // Node环境  构建完成自动删除容器
                            //image "node:14"
                            image "panweiji/node:14" // 使用自定义Dockerfile的node环境 加速monorepo依赖构建内置lerna等相关依赖
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            echo "获取APP信息"
                            getAppInfo()
                        }
                    }
                }

                stage('热修复') {
                    when { expression { "${BRANCH_NAME}" ==~ /hotfix\/.*/ } }
                    steps {
                        script {
                            echo "热修复"
                            hotFix()
                        }
                    }
                }

                stage('加固与多渠道') {
                    when { expression { return false } }
                    steps {
                        script {
                            echo "加固与多渠道"
                            reinforcedAndMultiChannel()
                        }
                    }
                }

                stage('单元测试') {
                    when { expression { return false } }
                    steps {
                        script {
                            runTests()
                        }
                    }
                }

                stage('UI测试') {
                    when { expression { return false } }
                    steps {
                        script {
                            echo "UI测试"
                            uiTests()
                        }
                    }
                }

                stage('Firebase Test Lab') {
                    when { expression { return false } }
                    steps {
                        script {
                            echo "Firebase Test Lab进行自动化测试 同时支持AndroidS、iOS、游戏等项目"
                            firebaseTestLab(map)
                        }
                    }
                }

                stage('内测分发') {
                    // app-store打包ipa是无法通过内测分发平台安装的
                    when { expression { return ("${params.IOS_SIGN_TYPE}" != Constants.IOS_SIGN_APP_STORE) } }
                    steps {
                        script {
                            echo "内测分发"
                            uploadDistribution(map)
                        }
                    }
                }

                stage('Android应用商店') {
                    when {
                        expression {
                            return ("${BRANCH_NAME}" == Constants.MASTER_BRANCH
                                    && "${androidBuildType}".contains(Constants.RELEASE_BUILD))
                        }
                    }
                    steps {
                        // 只显示当前阶段stage失败  而整个流水线构建显示成功
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            script {
                                echo "上传Android应用市场"
                                uploadAndroidMarket(map)
                            }
                        }
                    }
                }

                stage('App Store审核上架') {
                    when { expression { return ("${params.IOS_SIGN_TYPE}" == Constants.IOS_SIGN_APP_STORE) } }
                    steps {
                        script {
                            echo "上架App Store"
                            addedAppStore()
                        }
                    }
                }

                stage('钉钉通知') {
                    when {
                        beforeAgent true
                        expression { return ("${params.IS_DING_NOTICE}" == 'true') }
                    }
                    steps {
                        script {
                            if ("${params.IOS_SIGN_TYPE}" == Constants.IOS_SIGN_APP_STORE) {
                                dingNotice(2, "成功")
                                // App Store审核后创建检测任务 审核状态的变化后通知
                                appStoreCheckState(map)
                            } else if ("${BRANCH_NAME}" == Constants.MASTER_BRANCH
                                    && "${androidBuildType}".contains(Constants.RELEASE_BUILD)
                                    && (params.ANDROID_STORE_IDENTIFY == "huawei" || params.ANDROID_STORE_IDENTIFY == "xiaomi")) {
                                dingNotice(2, "成功")
                            } else {
                                dingNotice(1, "成功")
                            }
                        }
                    }
                }

                stage('发布日志') {
                    steps {
                        script {
                            // 自动打tag和生成CHANGELOG.md文件
                            gitTagLog(map)
                            // 发布日志
                            dingNotice(3)
                        }
                    }
                }

                stage('APP内升级') {
                    when { expression { return false } }
                    steps {
                        script {
                            echo "APP内升级, 调用RESTful API接口管理应用内的版本升级"
                        }
                    }
                }

                stage('AAR与Pod库') {
                    when { expression { return false } }
                    steps {
                        script {
                            echo "AAR与Pod库"
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
    } else if (type == "android-ios-2") {  //  注意！！！ 差异性较大的Pipeline建议区分groovy文件维护

    }
}

/**
 * 常量定义类型
 */
class Constants {
    static final String MASTER_BRANCH = 'master' // 正式生产Git分支

    static final String ANDROID = 'Android' // Android
    static final String IOS = 'iOS' // iOS
    static final String FLUTTER = 'Flutter' // Flutter
    static final String REACT_NATIVE = 'React Native' // React Native
    static final String UNITY = 'Unity' // Unity

    static final String DEBUG_BUILD = 'Debug' // 调试打包
    static final String RELEASE_BUILD = 'Release' // 正式打包

    static final String ANDROID_TEST_BUILD = 'Test' // 默认Android自定义测试打包配置
    static final String IOS_TEST_BUILD = 'Test' // 默认iOS自定义测试打包配置

    // 苹果打包签名导出方式
    static final String IOS_SIGN_DEVELOPMENT = 'development'
    static final String IOS_SIGN_AD_HOC = 'ad-hoc'
    static final String IOS_SIGN_APP_STORE = 'app-store'
    static final String IOS_SIGN_ENTERPRISE = 'enterprise'

    // App默认版本描述文案
    static final String APP_DEFAULT_VERSION_COPYWRITING = '1. 优化了一些细节体验\n2. 修复了一些已知问题'

    // 蒲公英分发平台地址
    static final String PGYER_URL = "https://www.pgyer.com"
    // 蒲公英分发平台下载API地址
    static final String PGYER_INSTALL_URL = "https://www.pgyer.com/apiv2/app/install"
}

/**
 *  获取初始化参数方法
 */
def getInitParams(map) {
    // JSON_PARAMS为单独项目的初始化参数  JSON_PARAMS为key值  value为json结构  请选择jenkins动态参数中的 "文本参数" 配置  具体参数定义如下
    def jsonParams = readJSON text: "${JSON_PARAMS}"
    // 项目类型 1.Android 2.iOS 3.Flutter 4.React Native 5.Unity
    PROJECT_TYPE = jsonParams.PROJECT_TYPE ? jsonParams.PROJECT_TYPE.trim() : ""
    REPO_URL = jsonParams.REPO_URL ? jsonParams.REPO_URL.trim() : "" // Git源码地址
    // 默认常用构建分支 针对环境和单独任务都可自定义设置 构建无需再次选择 实现一键构建或全自动构建
    DEFAULT_GIT_BRANCH = jsonParams.DEFAULT_GIT_BRANCH ? jsonParams.DEFAULT_GIT_BRANCH.trim() : "${map.default_git_branch}"
    // 自定义APP打包类型 默认是Debug Release  多个按顺序逗号,分隔
    CUSTOM_ANDROID_BUILD_TYPE = jsonParams.CUSTOM_ANDROID_BUILD_TYPE ? jsonParams.CUSTOM_ANDROID_BUILD_TYPE.trim().replace(",", "\n") : Constants.ANDROID_TEST_BUILD
    CUSTOM_IOS_BUILD_TYPE = jsonParams.CUSTOM_IOS_BUILD_TYPE ? jsonParams.CUSTOM_IOS_BUILD_TYPE.trim().replace(",", "\n") : Constants.IOS_TEST_BUILD
    // 自定义Android的Flavor类型 多个按顺序逗号,分隔
    CUSTOM_ANDROID_FLAVOR = jsonParams.CUSTOM_ANDROID_FLAVOR ? jsonParams.CUSTOM_ANDROID_FLAVOR.trim().replace(",", "\n") : "${map.android_store_identify}"
    // 自定义Android的gradle配置文件路径 获取版本号等信息 app/build.gradle
    CUSTOM_ANDROID_GRADLE_CONFIG_PATH = jsonParams.CUSTOM_ANDROID_GRADLE_CONFIG_PATH ? jsonParams.CUSTOM_ANDROID_GRADLE_CONFIG_PATH.trim() : "config.gradle"
    // 自定义具体特殊APP的构建JDK版本号 统一配置在Jenkinsfile内
    JDK_VERSION = jsonParams.JDK_VERSION ? jsonParams.JDK_VERSION.trim() : "${map.jdk}"

    // Android是否存在应用商店渠道号
    IS_ANDROID_STORE_IDENTIFY = jsonParams.IS_ANDROID_STORE_IDENTIFY ? jsonParams.IS_ANDROID_STORE_IDENTIFY : false
    // 是否打包Android AAB新格式  Androld App Bundle 注意 .aab不能直接安装到设备上，需要通过工具命令把它转成.apks , aab包更小按需下载
    IS_ANDROID_AAB = jsonParams.IS_ANDROID_AAB ? jsonParams.IS_ANDROID_AAB : false
    // iOS APP唯一标识符
    IOS_APP_IDENTIFIER = jsonParams.IOS_APP_IDENTIFIER ? jsonParams.IOS_APP_IDENTIFIER.trim() : ""
    // iOS项目二级目录名称 适配有些项目存在二级目录
    IOS_PROJECT_LEVEL_DIR = jsonParams.IOS_PROJECT_LEVEL_DIR ? jsonParams.IOS_PROJECT_LEVEL_DIR.trim() : ""
    // iOS项目SCHEME_NAME 默认二级目录名称
    IOS_SCHEME_NAME = jsonParams.IOS_SCHEME_NAME ? jsonParams.IOS_SCHEME_NAME.trim() : "${IOS_PROJECT_LEVEL_DIR}"
    // iOS项目多target名称 多个按顺序逗号,分隔
    IOS_MULTI_TARGET_NAMES = jsonParams.IOS_MULTI_TARGET_NAMES ? jsonParams.IOS_MULTI_TARGET_NAMES.trim().replace(",", "\n") + "\n" : ""
    // iOS项目多target参数 多个按顺序逗号,分隔 app_identifier~scheme组合
    IOS_MULTI_TARGET_PARAMS = jsonParams.IOS_MULTI_TARGET_PARAMS ? jsonParams.IOS_MULTI_TARGET_PARAMS.trim() : ""
    // 自定义Cocoapods版本 多版本并存切换
    COCOAPODS_VERSION = jsonParams.COCOAPODS_VERSION ? jsonParams.COCOAPODS_VERSION.trim() : ""

    // 跨平台流水线构建打包的目标   默认同时打包Android和iOS两个平台  也可配置单独指定平台打包
    BUILD_SYSTEM_TYPES = jsonParams.BUILD_SYSTEM_TYPES ? jsonParams.BUILD_SYSTEM_TYPES.trim() : "${Constants.ANDROID},${Constants.IOS}"
    // 改变跨平台流水线构建打包的目标 如果用户自定义指定选择优先级高 不再使用任务默认配置
    if (params.CROSS_PLATFORM_SYSTEM_TYPES && "${params.CROSS_PLATFORM_SYSTEM_TYPES}" != "${GlobalVars.defaultValue}") {
        BUILD_SYSTEM_TYPES = "${params.CROSS_PLATFORM_SYSTEM_TYPES}"
        println("跨平台流水线构建打包的目标: ${BUILD_SYSTEM_TYPES}")
    }

    // 目标系统类型 1.Android 2.iOS 3.Flutter 4.React Native 5.Unity
    switch ("${PROJECT_TYPE}".toInteger()) {
        case GlobalVars.android:
            SYSTEM_TYPE_NAME = "Android"
            break
        case GlobalVars.ios:
            SYSTEM_TYPE_NAME = "iOS"
            break
        case GlobalVars.flutter:
            SYSTEM_TYPE_NAME = "Flutter"
            break
        case GlobalVars.reactNative:
            SYSTEM_TYPE_NAME = "React Native"
            break
        case GlobalVars.unity:
            SYSTEM_TYPE_NAME = "Unity"
            unityVersion = "2020.3.13f1"  // unity编辑器版本
            unityActivationFile = "Unity_v2020.x.ulf" // unity激活许可文件名称
            break
        default:
            SYSTEM_TYPE_NAME = "未知"
    }
    println("目标系统类型: ${SYSTEM_TYPE_NAME}")

    try {
        // Android APP信息
        androidJobAppInfo = readJSON text: "${ANDROID_APP_INFO}"
        if (androidJobAppInfo) {
            // 自定义打包Flavor目录 多个用,逗号分隔
            apkFlavorNames = androidJobAppInfo.apkFlavorNames ? androidJobAppInfo.apkFlavorNames : ""
            // 华为应用商店应用AppId
            huaweiAppGalleryAppId = androidJobAppInfo.huaweiAppGalleryAppId ? androidJobAppInfo.huaweiAppGalleryAppId : ""
            // 小米应用商店私钥字符串
            xiaomiMarketPrivateKey = androidJobAppInfo.xiaomiMarketPrivateKey ? androidJobAppInfo.xiaomiMarketPrivateKey : ""
        }
    } catch (e) {
        //println("获取Android APP信息失败")
        println(e.getMessage())
    }

    try {
        // iOS APP审核信息
        fastlaneIosReviewInfo = "" // 传递给fastlane参数
        def firstName = "${map.ios_review_first_name}"
        def lastName = "${map.ios_review_last_name}"
        def phoneNumber = "${map.ios_review_phone_number}"
        def emailAddress = "${map.ios_review_email_address}"
        fastlaneIosReviewInfo = " first_name:${firstName} last_name:${lastName} " +
                "phone_number:'${phoneNumber}' email_address:${emailAddress} "
        def iosReviewInfo = readJSON text: "${IOS_APP_REVIEW_INFO}"
        if (iosReviewInfo) {
            // 审核需要登录的账号密码
            def demoUser = iosReviewInfo.demoUser
            def demoPassword = iosReviewInfo.demoPassword
            fastlaneIosReviewInfo = fastlaneIosReviewInfo + " demo_user:${demoUser} demo_password:${demoPassword} "
        }
        //println("${fastlaneIosReviewInfo}")
    } catch (e) {
        //println("获取iOS APP审核信息失败")
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
}

/**
 * 初始化信息
 */
def initInfo(map) {
    // 判断平台信息
    if (!isUnix()) {
        error("当前脚本针对Unix(如Linux或MacOS)系统 脚本执行失败 ❌")
    }
    //echo sh(returnStdout: true, script: 'env')
    //sh 'printenv'
    //println "${env.PATH}"

    // 初始化Docker
    initDocker()
    // 初始化Fastlane
    initFastlane()

    // Android iOS打包模式类型
    androidBuildType = ""
    iosBuildType = ""
    // Android iOS 包后缀
    androidPackageSuffix = "${IS_ANDROID_AAB ? 'aab' : 'apk'}"
    iosPackageSuffix = "ipa"
    // Android iOS包输出目录路径名称
    androidPackagesOutputDirPrefix = "${IS_ANDROID_AAB ? 'app/build/outputs/bundle' : 'app/build/outputs/apk'}"
    iosPackagesOutputDir = "packages"

    // 全局常量
    packageOssUrl = "" // 包OSS上传分发响应url
    androidPackageOssUrl = "" // 跨平台情况区分系统
    qrCodeOssUrl = "" // OSS二维码url

    uploadResult = "" // 上传分发响应结果
    uploadResultBuildQRCodeURL = ""
    uploadResultBuildShortcutUrl = ""
    uploadResultBuildKey = ""
    androidUploadResult = "" // 跨平台情况区分系统
    androidUploadResultBuildKey = ""

    // 华为应用主页地址
    huaweiApplicationUrl = ""
    // 小米应用主页地址
    xiaomiMarketUrl = ""

    appInfoName = "" // 应用名称
    appInfoVersion = ""  // 版本号
    appInfoSize = ""  // 包大小
    androidAppInfoSize = "" // Android包大小

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
def pullProjectCode(map) {
    // 未获取到参数 兼容处理 因为参数配置从代码拉取 必须先执行jenkins任务才能生效
    if (!params.GIT_TAG) {
        params.GIT_TAG = GlobalVars.noGit
    }
    // 获取应用打包代码
    if (params.GIT_TAG == GlobalVars.noGit) { // 基于分支最新代码构建
        // git url: "${REPO_URL}", branch: "${BRANCH_NAME}", credentialsId: "${GIT_CREDENTIALS_ID}"
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
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
        // 报告输出位置 build/reports/lint-results.html
        sh "./gradlew lint"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {

    }
}

/**
 *  获取APP代码内的版本号
 */
def getCodeVersion(map) {
    try {
        // 获取APP代码内的版本号
        def outInfoFile = "app_version.txt"
        androidAppVersion = "${env.BUILD_NUMBER}"
        iOSAppVersion = "${env.BUILD_NUMBER}"
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
            // 支持多productFlavors获取版本号
            if (params.ANDROID_STORE_IDENTIFY != GlobalVars.defaultValue
                    && !"${map.android_store_identify}".contains(params.ANDROID_STORE_IDENTIFY)) {
                sh "fastlane app_version type:${SYSTEM_TYPE_NAME.toLowerCase()} " +
                        " flavor:${params.ANDROID_STORE_IDENTIFY} out_info_file:${outInfoFile}"
            } else {
                sh "fastlane app_version type:${SYSTEM_TYPE_NAME.toLowerCase()} " +
                        " gradle_config_path:${CUSTOM_ANDROID_GRADLE_CONFIG_PATH} out_info_file:${outInfoFile}"
            }
            androidAppVersion = readFile(file: "fastlane/${outInfoFile}")
            println("androidAppVersion=" + androidAppVersion)
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {
            dir("${env.WORKSPACE}/${IOS_PROJECT_LEVEL_DIR}") {
                sh "fastlane app_version type:${SYSTEM_TYPE_NAME.toLowerCase()} out_info_file:${outInfoFile}"
                iOSAppVersion = readFile(file: "fastlane/${outInfoFile}")
                println("iOSAppVersion=" + iOSAppVersion)
            }
        }
    } catch (e) {
        println("获取APP代码内的版本号失败")
        println(e.getMessage())
    }
}

/**
 * 获取android打包类型和打包存储目录
 */
def getAndroidBuildType() {
    def androidPackagingPositionPrefix = "${androidPackagesOutputDirPrefix}${params.ANDROID_STORE_IDENTIFY != GlobalVars.defaultValue && IS_ANDROID_AAB == false ? "/" + params.ANDROID_STORE_IDENTIFY : ""}"
    if (!params.ANDROID_PACKAGING_TYPE || params.ANDROID_PACKAGING_TYPE == GlobalVars.defaultValue) {
        // android打包位置
        androidPackagesOutputDir = "${androidPackagingPositionPrefix}/" +
                "${BRANCH_NAME == Constants.MASTER_BRANCH ? "${Constants.RELEASE_BUILD.toLowerCase()}" : "${Constants.DEBUG_BUILD.toLowerCase()}"}"
        // android aab新格式
        if ("${IS_ANDROID_AAB}" == 'true') {
            androidPackagesOutputDir = "${androidPackagingPositionPrefix}/${params.ANDROID_STORE_IDENTIFY}" +
                    "${BRANCH_NAME == Constants.MASTER_BRANCH ? "${Constants.RELEASE_BUILD}" : "${Constants.DEBUG_BUILD}"}"
        }
        switch ("${BRANCH_NAME}") {
            case Constants.MASTER_BRANCH:
                return Constants.RELEASE_BUILD
            default:
                return Constants.DEBUG_BUILD
        }
    } else { // 自定义类型
        def packagingFileName = ""
        switch (params.ANDROID_PACKAGING_TYPE) {
            case Constants.DEBUG_BUILD:
                packagingFileName = "${Constants.DEBUG_BUILD.toLowerCase()}"
                break
            case Constants.RELEASE_BUILD:
                packagingFileName = "${Constants.RELEASE_BUILD.toLowerCase()}"
                break
            default:
                packagingFileName = "${Utils.firstWordLowerCase(params.ANDROID_PACKAGING_TYPE)}"
        }
        // android aab新格式
        if ("${IS_ANDROID_AAB}" == 'true') {
            packagingFileName = "${params.ANDROID_STORE_IDENTIFY}" + "${Utils.firstWordUpperCase(packagingFileName)}"
        }
        // android打包位置
        androidPackagesOutputDir = "${androidPackagingPositionPrefix}/${packagingFileName}"
        return params.ANDROID_PACKAGING_TYPE
    }
}

/**
 * 获取iOS打包类型
 */
def getiOSBuildType() {
    // iOS多环境切换配置
    def configBuildType = ""
    if (!params.IOS_PACKAGING_TYPE || params.IOS_PACKAGING_TYPE == GlobalVars.defaultValue) {
        switch (params.IOS_SIGN_TYPE) {
            case Constants.IOS_SIGN_DEVELOPMENT:
                configBuildType = "${Constants.DEBUG_BUILD}"
                break
            case Constants.IOS_SIGN_AD_HOC:
                configBuildType = "${Constants.IOS_TEST_BUILD}"
                break
            case Constants.IOS_SIGN_APP_STORE:
                configBuildType = "${Constants.RELEASE_BUILD}"
                break
            case Constants.IOS_SIGN_ENTERPRISE:
                configBuildType = "${Constants.DEBUG_BUILD}"
                break
            default:
                configBuildType = "${Constants.DEBUG_BUILD}"
        }
    } else { // 自定义类型
        configBuildType = params.IOS_PACKAGING_TYPE
    }

    return configBuildType
}

/**
 * 匹配是否是指定分支
 */
def isDeployCandidate() {
    return ("${"${BRANCH_NAME}"}" =~ /(develop|master)/)
}

/**
 * 初始化docker环境变量
 */
def initDocker() {
    Docker.initEnv(this)
}

/**
 * 初始化fastlane
 */
def initFastlane() {
    // 初始化环境变量
    Fastlane.initEnv(this)
    // sh "pwd && fastlane --version " //  fastlane env
}

/**
 * 是否存在fastlane配置
 */
def isExistsFastlane() {
    if (!fileExists("fastlane/Fastfile")) {
        sh "rm -rf fastlane &&  mkdir fastlane "
    }
}

/**
 * 动态替换Android fastlane环境变量.env文件配置 实现更多差异性项目复用
 */
def replaceAndroidFastlaneEnvFile(Map map) {
    try {
        dir("${env.WORKSPACE}/fastlane") {
            def envFileName = ".env"
            if (fileExists("${envFileName}")) {
                envFileContent = readFile(file: "${envFileName}")
                //.replaceAll("com.app.identifier", "") // Android应用包名 AndroidManifest.xml 内
                writeFile file: "${envFileName}", text: "${envFileContent}"
                        .replaceAll("HUAWEI_APP_GALLERY_CLIENT_ID_KEY", "${map.huawei_app_gallery_client_id}")
                        .replaceAll("HUAWEI_APP_GALLERY_CLIENT_SECRET_KEY", "${map.huawei_app_gallery_client_secret}")
            }
        }
    } catch (e) {
        println("动态替换Android fastlane环境变量.env文件配置失败")
        println(e.getMessage())
    }
}

/**
 * 动态替换iOS fastlane环境变量.env文件配置 实现更多差异性项目复用
 */
def replaceiOSFastlaneEnvFile(Map map, String appIdentifier, String schemeName, String packagesOutputDir) {
    dir("${env.WORKSPACE}/${IOS_PROJECT_LEVEL_DIR}/fastlane") {
        def envFileName = ".env"
        def xcodeProjectName = schemeName  // .xcworkspace和.xcodeproj 文件夹名称
        iosAppIdentifier = appIdentifier
        if (fileExists("${envFileName}")) {
            def plistFileName = "Info"
            // iOS多Target打包
            if (params.IOS_MULTI_TARGET && params.IOS_MULTI_TARGET != GlobalVars.defaultValue) {
                println("👉 iOS多Target打包名称: " + params.IOS_MULTI_TARGET)
                def targetArray = "${IOS_MULTI_TARGET_NAMES}".replaceAll("\n", ",").split(",") as ArrayList
                def targetIndex = targetArray.indexOf(params.IOS_MULTI_TARGET)
                def targetParamsArray = "${IOS_MULTI_TARGET_PARAMS}".split(",") as ArrayList
                def targetParamStr = targetParamsArray[targetIndex]
                def targetParamArray = targetParamStr.trim().split("~")
                iosAppIdentifier = targetParamArray[0]
                schemeName = targetParamArray[1]
                plistFileName = schemeName
            }

            envFileContent = readFile(file: "${envFileName}")
            writeFile file: "${envFileName}", text: "${envFileContent}"
                    .replaceAll("com.app.identifier", "${iosAppIdentifier}") // 在Info.plist的包标识符
                    .replaceAll("APPLE_ID_KEY", "${map.apple_id}")
                    .replaceAll("FASTLANE_PASSWORD_KEY", "${map.apple_password}")
                    .replaceAll("TEAM_ID_KEY", "${map.apple_team_id}")
                    .replaceAll("CONNECT_API_KEY_ID_KEY", "${map.apple_store_connect_api_key_id}")
                    .replaceAll("CONNECT_API_ISSUER_ID_KEY", "${map.apple_store_connect_api_issuer_id}")
                    .replaceAll("CONNECT_API_KEY_FILE_PATH_KEY", "${map.apple_store_connect_api_key_file_path}")
                    .replaceAll("SCHEME_NAME_KEY", "${schemeName}")
                    .replaceAll("XCODE_PROJECT_NAME_KEY", "${xcodeProjectName}")
                    .replaceAll("PACKAGES_OUTPUT_DIRECTORY_KEY", "${packagesOutputDir}")
                    .replaceAll("PLIST_NAME_KEY", "${plistFileName}")
            //println(readFile(file: "${envFileName}"))
        }
    }
}

/**
 * 设置App的Icon图标徽章 易于区分环境版本
 */
def iconAddBadge(map, type) {
    try {
        if (params.IS_ICON_ADD_BADGE == true) {
            // 文档地址: https://github.com/HazAT/badge    https://shields.io/
            // 先初始化依赖 sudo apt update && sudo apt install -y ruby-full && ruby --version
            // 再安装 sudo gem install badge && apt install -y imagemagick && sudo gem install librsvg
            // 保证raster.shields.io地址可访问  如果无法访问配置地址映射

            if ((type == GlobalVars.android && !"${androidBuildType}".contains(Constants.RELEASE_BUILD)) ||
                    (type == GlobalVars.ios && "${params.IOS_SIGN_TYPE}" != Constants.IOS_SIGN_APP_STORE)) {
                // 获取APP代码内的版本号
                // getCodeVersion(map)

                def shield = ""
                def glob = ""
                if (type == GlobalVars.android) {
                    //shield = "${getAndroidBuildType()}-${androidAppVersion}".toUpperCase()
                    glob = "/app/src/*/res/*/*ic_launcher*.{png,PNG}"
                } else if (type == GlobalVars.ios) {
                    //shield = "${iosBuildType}-${iOSAppVersion}".toUpperCase()
                    glob = "/**/*.appiconset/*.{png,PNG}"
                }
                println("开始制作App启动图标徽章 🚀")
                // 简洁版图标 alpha 或 beta徽章
                if ((type == GlobalVars.android && "${getAndroidBuildType()}" == Constants.DEBUG_BUILD) ||
                        (type == GlobalVars.ios && "${params.IOS_SIGN_TYPE}" == Constants.IOS_SIGN_DEVELOPMENT)) {
                    sh "badge --alpha --glob ${glob}" // ALPHA
                }
                if ((type == GlobalVars.android && "${getAndroidBuildType()}" == Constants.ANDROID_TEST_BUILD) ||
                        (type == GlobalVars.ios && "${params.IOS_SIGN_TYPE}" == Constants.IOS_SIGN_AD_HOC)) {
                    sh "badge --glob ${glob}" // BETA
                }
                // 复杂版shield图标徽章 带环境和版本号等
                /*    sh "badge --shield \"${shield}-blueviolet\" " +
                            " --shield_geometry \"+0+62%\" --shield_scale \"0.70\"  " +
                            " --shield_parameters \"colorA=orange&style=flat-square\" --shield_io_timeout \"10\" " +
                            " --verbose " +  // --no_badge --dark
                            " --glob ${glob} "*/
            }
        }
    } catch (e) {
        println("设置App的Icon图标徽章失败")
        println(e.getMessage())
    }
}

/**
 * 编译打包签名
 */
def buildPackage(map) {
    // fastlane配置文件CI库位置前缀
    fastlaneConfigDir = "${env.WORKSPACE}/" + "ci/_jenkins/fastlane"
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
        androidBuildPackage(map)
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {
        iosBuildPackage(map)
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter) {
        flutterBuildPackage(map)
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative) {
        reactNativeBuildPackage(map)
    }
}

/**
 * Android编译打包
 */
def androidBuildPackage(map) {
    isExistsFastlane()
    // 自定义组合打包模式
    if (params.ANDROID_STORE_IDENTIFY != GlobalVars.defaultValue) {
        androidBuildType = Utils.firstWordUpperCase(params.ANDROID_STORE_IDENTIFY) + getAndroidBuildType()
    } else {
        androidBuildType = getAndroidBuildType()
    }
    println "Android打包模式: ${androidBuildType} 👈"

    // 初始化环境变量
    Android.initEnv(this)
    // 切换JDK版本
    Java.switchJDKByDocker(this)

    // gradle配置后可实现自动签名 打包签名一体化
    if (isUnix()) {
        // GeneralFastfile自定义通用Fastlane配置文件 其他特定Fastlane导入复用
        sh "cp -r ${fastlaneConfigDir}/GeneralFastfile ./ "
        // fastlane核心配置文件同步
        sh "cp -r ${fastlaneConfigDir}/android/. fastlane"
        // Gemfile文件目录调整
        sh "cp -r ${fastlaneConfigDir}/android/Gemfile ./ && cd fastlane && rm -f Gemfile"
        // 动态替换fastlane环境变量.env文件配置 实现更多差异性项目复用
        replaceAndroidFastlaneEnvFile(map)
        try {
            // gradle执行权限和版本信息
            // Java.switchJDKByJenv(this, "${JDK_VERSION}")
            sh "pwd && chmod +x ./gradlew && ./gradlew -v && java -version && echo $JAVA_HOME"
        } catch (error) {
            println error.getMessage()
            // 执行gradle wrapper命令 允许在没有安装gradle的情况下运行Gradle任务 解决gradlew is not found (No such file or directory)
            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter) {
                sh "flutter pub get"
            }
            // 初始化Gradle项目
            Gradle.initProject(this)
        }
        // 清空android打包输出目录
        sh "rm -rf  ${"${PROJECT_TYPE}".toInteger() == GlobalVars.flutter ? "../" + androidPackagesOutputDirPrefix : androidPackagesOutputDirPrefix}/*"
        // 删除android代码中的本地配置文件 可能影响CI打包
        sh "rm -f local.properties"
        // 设置App的Icon图标徽章
        iconAddBadge(map, GlobalVars.android)
        // 设置应用版本号
        if ("${params.APP_VERSION_NUM}".trim() != "") {
            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
                // 设置Android版本号
                Android.setVersion(this, "${params.APP_VERSION_NUM}")
            }
            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter) {
                // 设置Flutter版本号
                Flutter.setVersion(this, "${params.APP_VERSION_NUM}")
            }
        }

        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter) {
            println("执行Flutter打包原生Android应用 🚀")
            // Flutter使用自带flutter build命令实现多环境多产品构建
            Flutter.buildAndroidApp(this)
        } else {
            println("执行Fastlane打包原生Android应用 🚀")
            sh "fastlane package packaging_type:${androidBuildType}  is_aab:${IS_ANDROID_AAB} "
        }
        // sh "./gradlew clean --no-daemon assemble${androidBuildType}" // --no-daemon解决jenkins并发执行会将gradle杀掉
    } else {
        bat "gradlew clean assemble${androidBuildType}"
    }

    dir("${env.WORKSPACE}") {
        androidPackagesOutputDir = "${"${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative ? "android/${androidPackagesOutputDir}" : "${androidPackagesOutputDir}"}"
        /*   // 重命名APK
          def newAndroidApkName = "${androidBuildType}-${Utils.formatDate("yyyy-MM-dd_HH:mm")}"
           if ("${IS_ANDROID_STORE_IDENTIFY}" == 'true') {
               // 包名添加渠道号用于区分
               newAndroidApkName = newAndroidApkName + "-${params.ANDROID_STORE_IDENTIFY}"
           }
           sh "mv ${androidPackagesOutputDir}/*.${androidPackageSuffix} ${androidPackagesOutputDir}/${newAndroidApkName}.${androidPackageSuffix}" */

        // apk包路径和名称
        apkPackagePath = Utils.getShEchoResult(this, "find ${androidPackagesOutputDir}/*.${androidPackageSuffix}")
        androidApkName = "${apkPackagePath}".replace("${androidPackagesOutputDir}/", "")
    }
    println("原生Android打包产出位置: ${apkPackagePath}")
    println("原生Android应用打包成功 ✅")
}

/**
 * iOS编译打包
 */
def iosBuildPackage(map) {
    fastlaneConfigDir = "${env.WORKSPACE}/" + "ci/_jenkins/fastlane"
    sh "chmod -R 777 ./"
    // 判断是否存在二级目录 *.xcodeproj
    dir("${env.WORKSPACE}/${IOS_PROJECT_LEVEL_DIR}") {
        isExistsFastlane()
        /* sh "#!/bin/bash -l  rvm use 3.0.3"
           sh "rvm info" */
        // sh "ruby -v && gem -v"
        // sh "pod --version"
        def podFileName = "Podfile"
        if (!fileExists(podFileName) && "${PROJECT_TYPE}".toInteger() == GlobalVars.unity) {
            // 初始化Podfile文件
            sh "pod init"
        }
        // 设置指定Cocoapods版本  多版本并存切换
        /*   if ("${COCOAPODS_VERSION}".trim() != "") {
               Apple.setCocoaPodsVersion(this, "${COCOAPODS_VERSION}")
           } else {
               Apple.setCocoaPodsVersion(this)
           }*/
        if (fileExists(podFileName)) {
            retry(12) { // pod install下载依赖可能因为网络等失败 自动重试几次
                println("下载更新CocoaPods依赖资源 📥")
                // 镜像源 工程的Podfile文件内第一行加上
                // source 'https://mirrors.tuna.tsinghua.edu.cn/git/CocoaPods/Specs.git'
                /* def podFileContent = readFile(file: "${podFileName}")
                 writeFile file: "${podFileName}",
                         text: "source 'https://mirrors.tuna.tsinghua.edu.cn/git/CocoaPods/Specs.git'\n${podFileContent}"*/
                // sh "rm -f *.lock" // Podfile.lock文件更新 会导致找不到新包等问题 检测GitHub访问速度 hosts文件映射
                if ("${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative) {
                    // 适配Apple Silicon芯片 命令前添加 arch -x86_64 前缀 sudo gem install ffi
                    sh "arch -x86_64 pod install"
                    // sh "cd ${env.WORKSPACE} && npx react-native run-ios"
                } else {
                    // sh "pod repo update" // 更新整个.cocoapods下的所有库 防止新包无法下载的情况
                    sh "pod install --repo-update" //  --repo-update如果Podfile有更新 则下载最新版本
                }
            }
        }
        if ("${PROJECT_TYPE}".toInteger() != GlobalVars.flutter) {
            // iOS多环境模式切换配置
            iosBuildType = getiOSBuildType()
        }
        println "iOS打包模式: ${iosBuildType} ${params.IOS_SIGN_TYPE} 👈"
        // GeneralFastfile自定义通用Fastlane配置文件 其他特定Fastlane导入复用
        sh "cp -r ${fastlaneConfigDir}/GeneralFastfile ./ "
        // fastlane核心配置文件同步
        sh "rm -rf fastlane && cp -r ${fastlaneConfigDir}/ios/. fastlane && chmod +x fastlane"
        // Gemfile文件目录调整
        sh "cp -r ${fastlaneConfigDir}/ios/Gemfile ./ && cd fastlane && rm -f Gemfile"
        // 动态替换fastlane环境变量.env文件配置 实现更多差异性项目复用
        replaceiOSFastlaneEnvFile(map, "${IOS_APP_IDENTIFIER}", "${IOS_SCHEME_NAME}", "${iosPackagesOutputDir}")
        // 清空ios打包输出目录
        sh "rm -rf  ${iosPackagesOutputDir} && rm -rf output "
        // 设置App的Icon图标徽章
        iconAddBadge(map, GlobalVars.ios)

        println("执行Fastlane打包原生iOS应用 🚀")
        /*  if ("${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative) {
          // Apple Silicon (ARM64)系统芯片如果有兼容性问题 fastlane命令前添加 arch -x86_64 前缀 依赖sudo gem install ffi
      } else {*/
        sh "fastlane package packaging_type:${iosBuildType} sign_type:${params.IOS_SIGN_TYPE} " +
                "is_icon_add_badge:${params.IS_ICON_ADD_BADGE} build_num:${env.BUILD_NUMBER} version_num:${params.APP_VERSION_NUM}"
        // }

        // ipa包路径和名称
        ipaPackagePath = Utils.getShEchoResult(this, "find ${iosPackagesOutputDir}/*.${iosPackageSuffix}")
        iosIpaName = "${ipaPackagePath}".replace("${iosPackagesOutputDir}/", "")
    }
    println("原生iOS打包产出位置: ${ipaPackagePath}")
    println("原生iOS应用打包成功 ✅")
}

/**
 * Flutter编译打包
 */
def flutterBuildPackage(map) {
    // 初始化环境变量
    Flutter.initEnv(this)

    // 构建应用 前置条件
    Flutter.buildPrecondition(this)

    // 构建Android apk包
    if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.ANDROID}")) {
        // Flutter重新Android定义包路径  区分aab和apk格式
        androidPackagesOutputDirPrefix = "${IS_ANDROID_AAB ? 'build/app/outputs/bundle' : 'build/app/outputs/apk'}"
        dir("android") {
            androidBuildPackage(map)
        }
    }

    // 构建iOS ipa包
    if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.IOS}")) {
        IOS_PROJECT_LEVEL_DIR = "ios"
        // Flutter编译iOS后的Xcode工程目录
        def iosCodePath = "${env.WORKSPACE}/build/ios/iphoneos/Runner.app"
        // sh " rm -rf ${iosCodePath} "

        dir("ios") {
            println("执行Flutter打包原生iOS应用 🚀")
            iosBuildType = getiOSBuildType()
            // Flutter使用自带flutter build命令实现多环境多产品构建
            retry(12) { // pod install下载依赖可能因为网络等失败 自动重试几次
                // 命令编译出Xcode .app工程目录
                Flutter.buildiOSApp(this)
            }
        }

        // 进入Flutter打包编译的Xcode .app工程目录 再执行真正的打包签名
        dir("${iosCodePath}") {
            // 在debug模式下，Flutter的热重载是把默认编译方式改为JIT，但是在iOS 14系统以后，苹果系统对JIT的编译模式进行了限制，造成在debug模式下基于Flutter的App运行不起来
            iosBuildType = "${Constants.RELEASE_BUILD}" // Flutter固定release模式或profile解决启动失败问题
            iosBuildPackage(map)
        }
    }

}

/**
 * React Native编译打包
 */
def reactNativeBuildPackage(map) {
    // 初始化Node环境变量
    // Node.initEnv(this)

    // Node环境设置镜像
    Node.setMirror(this)

    // 安装依赖
    sh 'yarn'

    // 构建Android apk包
    if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.ANDROID}")) {
        dir("android") {
            androidBuildPackage(map)
        }
    }

    // 构建iOS ipa包
    if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.IOS}")) {
        dir("ios") {
            IOS_PROJECT_LEVEL_DIR = "ios"
            iosBuildPackage(map)
        }
    }
}

/**
 * Unity编译打包
 */
def unityBuildPackage(map, projectType) {
    // 初始化环境变量
    Unity.initEnv(this)

    jenkinsConfigDir = "${env.WORKSPACE}/ci/_jenkins"
    // fastlane配置文件CI库位置前缀
    fastlaneConfigDir = "${jenkinsConfigDir}/fastlane"
    if (projectType == GlobalVars.android) {
        // 自定义组合打包模式
        if (params.ANDROID_STORE_IDENTIFY != GlobalVars.defaultValue) {
            androidBuildType = Utils.firstWordUpperCase(params.ANDROID_STORE_IDENTIFY) + getAndroidBuildType()
        } else {
            androidBuildType = getAndroidBuildType()
        }
    }
    // unity命令构建的目标目录
    androidPackagesOutputDir = "android"
    unityIosPackagesOutputDir = "ios"
    unityWebGLPackagesOutputDir = "webgl"

    // 同步打包执行的构建文件
    Unity.syncBuildFile(this)

    if (projectType == GlobalVars.android) {
        // 删除Unity构建产物
        sh "rm -rf ./${androidPackagesOutputDir} "
        // Unity构建打包
        Unity.build(this, "Android")
        // apk包路径和名称
        apkPackagePath = Utils.getShEchoResult(this, "find ${androidPackagesOutputDir}/*.${androidPackageSuffix}")
        androidApkName = "${apkPackagePath}".replace("${androidPackagesOutputDir}/", "")
        println("Unity For Android打包成功 ✅")
    } else if (projectType == GlobalVars.ios) {
        // 删除Unity构建产物
        sh "rm -rf ./${unityIosPackagesOutputDir} "
        // Unity构建打包
        iosAppIdentifier = "${IOS_APP_IDENTIFIER}"
        Unity.build(this, "iOS")
        // iOS只打包出XCode工程代码 再用Xcode工具进行打包 , 当您构Unity iOS时，会生成一个XCode项目。该项目需要签名、编译和分发!!!
        println("Unity For iOS编译成Xcode工程目录成功 ✅")
    } else if (projectType == GlobalVars.webGl) {
        // 删除Unity构建产物
        sh "rm -rf ./${unityWebGLPackagesOutputDir} "
        // Unity构建打包
        Unity.build(this, "WebGL")
        println("Unity For WebGL打包成功 ✅")
    }

}

/**
 * Unity编译Xcode打包For iOS
 */
def unityBuildForiOS(map) {
    // iOS只打包出XCode工程代码 再用Xcode工具进行打包 , 当您构Unity iOS时，会生成一个XCode项目。该项目需要签名、编译和分发!!!
    Unity.pullCodeFromRemote(this)
    // 调用Fastlane打包iOS包
    iosBuildPackage(map)
    // 推送打包产物到远程服务器
    Unity.pushPackageToRemote(this)
}

/**
 * 获取APP信息
 */
def getAppInfo() {
    try {
        def outInfoFile = "app_info.txt"
        def appInfo = ""

        //def appInfoRuby = "${env.WORKSPACE}/ci/_jenkins/fastlane/actions/app_info.rb" // Ruby语言实现
        def appInfoJavaScript = "${env.WORKSPACE}/ci/_jenkins/web/app-info.js"  // JavaScript语言实现
        // sh "chmod +x ${appInfoRuby}"
        sh "chmod +x ${appInfoJavaScript}"
        App.getAppInfoPackageInit(this)

        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
            if (!fileExists("${androidPackagesOutputDir}/")) {
                println("获取APP信息Android包路径${androidPackagesOutputDir}不存在")
                if (apkFlavorNames) { // 多apk包方式或自定义Flavor方式
                    println("尝试自定义Flavor多包匹配方式")
                    apkFlavorNames.split(",").each { flavor ->
                        def androidFlavorPackagesOutputDir = "${androidPackagesOutputDir}".replaceAll("${androidPackagesOutputDirPrefix}",
                                "${androidPackagesOutputDirPrefix}/${flavor.toString().trim()}")
                        androidApkName = Utils.getShEchoResult(this, "find ${androidFlavorPackagesOutputDir}/*.${androidPackageSuffix}")
                                .replace("${androidFlavorPackagesOutputDir}/", "")
                        // sh "ruby ${appInfoRuby} ${androidFlavorPackagesOutputDir}/${androidApkName} ${outInfoFile}"
                        sh "node ${appInfoJavaScript} --appFilePath='${androidFlavorPackagesOutputDir}/${androidApkName}' --outInfoFile='${outInfoFile}' "
                    }
                }
            } else {
                // sh "ruby ${appInfoRuby} ${apkPackagePath} ${outInfoFile}"
                sh "node ${appInfoJavaScript} --appFilePath='${apkPackagePath}' --outInfoFile='${outInfoFile}' "
            }

            if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
                appInfo = readFile(file: "${outInfoFile}")
            }
        }

        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {
            dir("${env.WORKSPACE}/${IOS_PROJECT_LEVEL_DIR}") {
                // sh "ruby ${appInfoRuby} ${ipaPackagePath} ${outInfoFile}"
                sh "node ${appInfoJavaScript} --appFilePath='${ipaPackagePath}' --outInfoFile='${outInfoFile}' "
                appInfo = readFile(file: "${outInfoFile}")
            }
        }

        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter || "${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative || "${PROJECT_TYPE}".toInteger() == GlobalVars.unity) {
            if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.ANDROID}")) {
                dir("${env.WORKSPACE}/${androidPackagesOutputDir}") {
                    // sh "ruby ${appInfoRuby} ${env.WORKSPACE}/${apkPackagePath} ${outInfoFile}"
                    sh "node ${appInfoJavaScript} --appFilePath='${env.WORKSPACE}/${apkPackagePath}' --outInfoFile='${outInfoFile}' "
                    appInfo = readFile(file: "${outInfoFile}")
                    def androidAppInfo = appInfo.split(",") as ArrayList
                    androidAppInfoSize = androidAppInfo[2] // 包大小
                }
            }

            if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.IOS}")) {
                dir("${env.WORKSPACE}/${IOS_PROJECT_LEVEL_DIR}") {
                    // sh "ruby ${appInfoRuby} ${ipaPackagePath} ${outInfoFile}"
                    sh "node ${appInfoJavaScript} --appFilePath='${ipaPackagePath}' --outInfoFile='${outInfoFile}' "
                    appInfo = readFile(file: "${outInfoFile}")
                }
            }
        }
        appInfo = appInfo.split(",") as ArrayList
        appInfoName = appInfo[0] // 应用名称
        appInfoVersion = appInfo[1] // 版本号
        println(appInfoName)
        println(appInfoVersion)
        appInfoSize = appInfo[2] // 包大小
        appInfoBuildVersion = appInfo[3] // 构建号
        appInfoIdentifier = appInfo[4] // app唯一标识
        appInfoOS = appInfo[5] // app系统
    } catch (e) {
        appInfoName = "应用名称" // 应用名称
        appInfoVersion = "1.0.0" // 版本号
        appInfoSize = "未知" // 包大小
        println("获取APP信息失败 ❌")
        println(e.getMessage())
    }
}

/**
 * 热修复
 */
def hotFix() {
    // Sophix或Tinker热修复
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
        // 腾讯微信团队Tinker热修复+Bugly管理分发平台方案 https://github.com/Tencent/tinker
        // 执行tinkerPatchRelease打基准补丁包并上报联网  app/build/outputs/patch目录下的补丁包并上传到Bugly
        // sh "java -jar tinker-patch-cli.jar -old old.apk -new new.apk -config tinker_config.xml -out app/build/outputs/patch"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {

    }
}

/**
 * 加固和多渠道
 */
def reinforcedAndMultiChannel() {
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {

    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {

    }
}

/**
 * 单元测试
 */
def runTests() {
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
        // 报告目录app/build/reports/tests/testDebugUnitTest/index.html
        sh "./gradlew test${getAndroidBuildType()}UnitTest"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {
        dir("${env.WORKSPACE}/${IOS_PROJECT_LEVEL_DIR}") {
            sh "fastlane test"
        }
    }
}

/**
 * UI测试
 */
def uiTests() {
    // Appium自动录制 生成回归和冒烟等测试脚本并截图
    // 自动截屏所有应用不同尺寸的页面给UI设计师审核
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {

    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {

    }
}

/**
 * Firebase Test Lab进行自动化测试 同时支持AndroidS、iOS、游戏等项目
 */
def firebaseTestLab(map) {

}

/**
 * 上传分发
 */
def uploadDistribution(map) {
    try {
        //变更日志
        changelog = "\n备注: Git构建分支: ${BRANCH_NAME}," +
                " Android打包模式: ${androidBuildType}," +
                " iOS打包模式: ${iosBuildType} ${params.IOS_SIGN_TYPE}," +
                " 发布人: ${BUILD_USER} \nRelease By Jenkins And Fastlane"

        if (params.APP_VERSION_DESCRIPTION) {
            changelog = "${params.APP_VERSION_DESCRIPTION}" + "${changelog}"
        }
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
            if (!fileExists("${androidPackagesOutputDir}/")) {
                println("上传分发Android包路径${androidPackagesOutputDir}不存在")
                if (apkFlavorNames) { // 多apk包方式或自定义Flavor方式
                    println("尝试自定义Flavor多包匹配方式")
                    apkFlavorNames.split(",").each { flavor ->
                        def androidFlavorPackagesOutputDir = "${androidPackagesOutputDir}".replaceAll("${androidPackagesOutputDirPrefix}",
                                "${androidPackagesOutputDirPrefix}/${flavor.toString().trim()}")
                        androidApkName = Utils.getShEchoResult(this, "find ${androidFlavorPackagesOutputDir}/*.${androidPackageSuffix}")
                                .replace("${androidFlavorPackagesOutputDir}/", "")
                        uploadResult = DistributionPlatform.uploadPgyer(this, "${androidApkName}", "${androidFlavorPackagesOutputDir}",
                                "APK包Flavor类型: ${flavor}\n" + "${changelog}", "${PGYER_API_KEY}")
                    }
                }
            } else { // 单apk包方式
                // 上传蒲公英分发平台
                uploadResult = DistributionPlatform.uploadPgyer(this, "${androidApkName}",
                        "${androidPackagesOutputDir}", "${changelog}", "${PGYER_API_KEY}")
                androidUploadResult = uploadResult
                // 上传Fir分发平台
                // firUploadResult = DistributionPlatform.uploadFir(this, "${androidPackagesOutputDir}/${androidApkName}", "${changelog}")
            }
        }

        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {
            dir("${env.WORKSPACE}/${IOS_PROJECT_LEVEL_DIR}") {
                // 上传蒲公英分发平台
                uploadResult = DistributionPlatform.uploadPgyer(this, "${iosIpaName}",
                        "${iosPackagesOutputDir}", "${changelog}", "${PGYER_API_KEY}")
                // 上传Fir分发平台
                // firUploadResult = DistributionPlatform.uploadFir(this, "${iosPackagesOutputDir}/${iosIpaName}", "${changelog}")
            }
        }

        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter || "${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative
                || "${PROJECT_TYPE}".toInteger() == GlobalVars.unity) {
            if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.ANDROID}")) {
                uploadResult = DistributionPlatform.uploadPgyer(this, "${androidApkName}",
                        "${androidPackagesOutputDir}", "${changelog}", "${PGYER_API_KEY}")
                androidUploadResult = uploadResult
            }
            if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.IOS}")) {
                uploadResult = DistributionPlatform.uploadPgyer(this, "${iosIpaName}",
                        "${IOS_PROJECT_LEVEL_DIR}/${iosPackagesOutputDir}", "${changelog}", "${PGYER_API_KEY}")
            }
        }

        if ("${androidUploadResult}" != "") {
            androidUploadResult = readJSON text: "${androidUploadResult}"
            androidUploadResultBuildKey = androidUploadResult.data.buildKey
        }
        //println(uploadResult)
        uploadResult = readJSON text: "${uploadResult}"
        uploadResultBuildQRCodeURL = uploadResult.data.buildQRCodeURL
        uploadResultBuildShortcutUrl = uploadResult.data.buildShortcutUrl
        uploadResultBuildKey = uploadResult.data.buildKey
    } catch (e) {
        println(e.getMessage())
        println("第三方分发平台上传失败, 自动切换上传到自建的分发OSS平台 保证流水线高用性  ❌")
        uploadDistributionOss(map)
    }

    println("分发平台上传成功 ✅")
}

/**
 * 上传自建分发OSS
 */
def uploadDistributionOss(map) {
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
        packageOssUrl = DistributionPlatform.uploadOss(this, map, "${androidApkName}", "${androidPackagesOutputDir}")
        androidPackageOssUrl = packageOssUrl
        genQRCode(map, "${androidPackageOssUrl}", GlobalVars.android)
    }

    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {
        packageOssUrl = DistributionPlatform.uploadOss(this, map, "${iosIpaName}", "${IOS_PROJECT_LEVEL_DIR}/${iosPackagesOutputDir}")
    }

    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter || "${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative
            || "${PROJECT_TYPE}".toInteger() == GlobalVars.unity) {
        if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.ANDROID}")) {
            packageOssUrl = DistributionPlatform.uploadOss(this, map, "${androidApkName}", "${androidPackagesOutputDir}")
            androidPackageOssUrl = packageOssUrl
            genQRCode(map, "${androidPackageOssUrl}", GlobalVars.android)
        }

        if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.IOS}")) {
            packageOssUrl = DistributionPlatform.uploadOss(this, map, "${iosIpaName}", "${IOS_PROJECT_LEVEL_DIR}/${iosPackagesOutputDir}")
        }

    }
    println("${packageOssUrl}")
    println("上传自建分发OSS成功 ✅")
    // 生成二维码
    genQRCode(map, "${packageOssUrl}")
}

/**
 * 生成二维码
 */
def genQRCode(map, url, projectType = GlobalVars.ios) {
    imageSuffixName = "png"
    sh "rm -f *.${imageSuffixName}"
    def imageFileName = "${SYSTEM_TYPE_NAME}-${env.BUILD_NUMBER}"
    QRCode.generate(this, "${url}", "${imageFileName}")
    def sourceFile = "${env.WORKSPACE}/${imageFileName}.${imageSuffixName}" // 源文件
    def targetFile = "${SYSTEM_TYPE_NAME.toLowerCase()}/${env.JOB_NAME}/${imageFileName}.${imageSuffixName}" // 目标文件
    qrCodeOssUrl = AliYunOSS.upload(this, map, sourceFile, targetFile)
    if (projectType == GlobalVars.android) {
        androidQrCodeOssUrl = qrCodeOssUrl
    }
    println "${qrCodeOssUrl}"
}

/**
 * 上传Android应用市场
 */
def uploadAndroidMarket(map) {
    // 多渠道打包上架 可同时上架多个Android应用市场
    if (params.ANDROID_STORE_IDENTIFY == "huawei" || !"${map.android_store_identify}".contains(params.ANDROID_STORE_IDENTIFY)) {
        try {
            // 获取具体android应用市场的唯一标识id   单个jenkins job配置多个Saas项目情况
            huaweiAppGalleryAppId = Android.getAndroidMarketId(this, "${huaweiAppGalleryAppId}")

            // 审核上架华为应用商店
            Android.huaweiMarket(this)
            println("Huawei App Gallery商店提交审核成功, 等待人工审核通过后上架 ✅")
            huaweiApplicationUrl = "https://appgallery.huawei.com/#/app/C${huaweiAppGalleryAppId}"
            println("华为商店应用主页: ${huaweiApplicationUrl}")
        } catch (e) {
            println("自动提审上架华为应用商店失败 ❌")
            println(" 🚨 请先确保在华为App Gallery商店创建应用并在Jenkins配置应用的huaweiAppGalleryAppId参数、版本号正确与线上无在审核的版本, 查看具体错误日志分析原因")
            println(e.getMessage())
            sh "exit 1"
        }
    } else if (params.ANDROID_STORE_IDENTIFY == "xiaomi" || !"${map.android_store_identify}".contains(params.ANDROID_STORE_IDENTIFY)) {
        try {
            // 获取具体android应用市场的唯一标识id   单个jenkins job配置多个Saas项目情况
            xiaomiMarketPrivateKey = Android.getAndroidMarketId(this, "${xiaomiMarketPrivateKey}")

            // 审核上架小米应用商店
            Android.xiaomiMarket(this, map, "${xiaomiMarketPrivateKey}")
            println("小米商店提交审核成功, 等待人工审核通过后上架 ✅")
            xiaomiMarketUrl = "https://app.mi.com/details?id=${appInfoIdentifier}&ref=search"
            println("小米商店应用主页: ${xiaomiMarketUrl}")
        } catch (e) {
            println("自动提审上架小米应用商店失败 ❌")
            println(" 🚨 请先确保在小米商店创建应用并在Jenkins配置应用的xiaomiMarketPrivateKey参数和cer证书、版本号正确与线上无在审核的版本, 查看具体错误日志分析原因")
            println(e.getMessage())
            sh "exit 1"
        }
    }
}

/**
 * App Store审核上架
 */
def addedAppStore() {
    dir("${env.WORKSPACE}/${IOS_PROJECT_LEVEL_DIR}") {
        // 默认设置二维码url https://www.apple.com/app-store/
        qrCodeOssUrl = "https://www.apple.com.cn/v/app-store/a/images/overview/icon_appstore__ev0z770zyxoy_large_2x.png"
        // App Store审核上架功能是完整的  调用fastlane编写的脚本实现
        Apple.reviewOn(this)
        println("App Store Connect商店提交审核成功, 等待人工审核通过后上架 ✅")
    }
}

/**
 * App Store提交审核后创建定时检测任务 审核状态的变化后通知
 */
def appStoreCheckState(map) {
    Apple.appStoreCheckState(this, map)
}

/**
 * 归档文件
 */
def archive() {
    try {
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
            archiveArtifacts artifacts: "${androidPackagesOutputDir}/*.${androidPackageSuffix}", fingerprint: true
        }
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {
            dir("${env.WORKSPACE}/${IOS_PROJECT_LEVEL_DIR}") {
                archiveArtifacts artifacts: "${iosPackagesOutputDir}/*.${iosPackageSuffix}", fingerprint: true
            }
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
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android
                || ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter && "${BUILD_SYSTEM_TYPES}".contains("${Constants.ANDROID}"))) {
            sh " rm -f ${androidPackagesOutputDir}/*.${androidPackageSuffix}"
        }
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios
                || ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter && "${BUILD_SYSTEM_TYPES}".contains("${Constants.IOS}"))) {
            dir("${env.WORKSPACE}/${IOS_PROJECT_LEVEL_DIR}") {
                // 清空iOS打包产物
                sh "rm -rf  ${iosPackagesOutputDir} && rm -rf output"
                sh "fastlane clean_build_artifact"
            }
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
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android) {
            currentBuild.description = "<img src=${qrCodeOssUrl == "" ? uploadResultBuildQRCodeURL : qrCodeOssUrl}  width=250 height=250> " +
                    "<br/> <a href='${androidPackageOssUrl == "" ? "${Constants.PGYER_INSTALL_URL}?_api_key=${PGYER_API_KEY}&buildKey=${androidUploadResultBuildKey}" : "${androidPackageOssUrl}"}'> 👉直接下载Android包</a> " +
                    "<br/> ${appInfoName} ${SYSTEM_TYPE_NAME} v${appInfoVersion}  <br/> 大小: ${appInfoSize} <br/> 分支: ${BRANCH_NAME} " +
                    "<br/> 模式: ${androidBuildType}  构建版本: JDK${JDK_VERSION}<br/> 发布人: ${BUILD_USER}"
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios) {
            currentBuild.description = "<img src=${qrCodeOssUrl == "" ? uploadResultBuildQRCodeURL : qrCodeOssUrl} width=250 height=250>  " +
                    "<br/> 名称: ${appInfoName} ${SYSTEM_TYPE_NAME} <br/> 版本: ${appInfoVersion} <br/> 大小: ${appInfoSize} <br/> 分支: ${BRANCH_NAME} " +
                    "<br/> 模式: ${iosBuildType} ${params.IOS_SIGN_TYPE} <br/> 发布人: ${BUILD_USER}"
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter || "${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative || "${PROJECT_TYPE}".toInteger() == GlobalVars.unity) {
            currentBuild.description = "<img src=${qrCodeOssUrl == "" ? uploadResultBuildQRCodeURL : qrCodeOssUrl} width=250 height=250>  " +
                    "${"${BUILD_SYSTEM_TYPES}".contains("${Constants.ANDROID}") ? "<br/> <a href='${androidPackageOssUrl == "" ? "${Constants.PGYER_INSTALL_URL}?_api_key=${PGYER_API_KEY}&buildKey=${androidUploadResultBuildKey}" : "${androidPackageOssUrl}"}'> 👉直接下载Android包</a> " : ""} " +
                    "<br/> ${appInfoName} ${SYSTEM_TYPE_NAME} For ${BUILD_SYSTEM_TYPES} v${appInfoVersion}   <br/> 分支: ${BRANCH_NAME} " +
                    "<br/> Android大小: ${androidAppInfoSize} 模式: ${androidBuildType} 构建版本: JDK${JDK_VERSION}<br/> iOS大小: ${appInfoSize} 模式: ${iosBuildType} ${params.IOS_SIGN_TYPE} " +
                    "<br/> 发布人: ${BUILD_USER}"
        }
    } catch (e) {
        println e.getMessage()
    }
}

/**
 * 生成tag和变更日志
 */
def gitTagLog(map) {
    // 未获取到参数 兼容处理 因为参数配置从代码拉取 必须先执行jenkins任务才能生效
    if (!params.IS_GIT_TAG && params.IS_GIT_TAG != false) {
        params.IS_GIT_TAG = true
    }
    // 构建成功后生产环境并发布类型自动打tag和变更记录 指定tag方式不再重新打tag
    if (params.IS_GIT_TAG == true
            && (("${BRANCH_NAME}" == Constants.MASTER_BRANCH && "${androidBuildType}".contains(Constants.RELEASE_BUILD))
            || "${params.IOS_SIGN_TYPE}" == Constants.IOS_SIGN_APP_STORE) && params.GIT_TAG == GlobalVars.noGit) {
        // 获取变更记录
        def gitChangeLog = ""
        if ("${Constants.APP_DEFAULT_VERSION_COPYWRITING}" == params.APP_VERSION_DESCRIPTION) {
            gitChangeLog = changeLog.genChangeLog(this, 100).replaceAll("\\;", "\n")
        } else {
            // 使用自定义文案
            gitChangeLog = "${params.APP_VERSION_DESCRIPTION}"
        }
        // 获取版本号
        def tagVersion = "${appInfoVersion}"
        // monorepo单体式仓库 独立版本号Tag重复处理
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android && params.ANDROID_STORE_IDENTIFY != GlobalVars.defaultValue
                && !"${map.android_store_identify}".contains(params.ANDROID_STORE_IDENTIFY)) {
            tagVersion = tagVersion + "-" + params.ANDROID_STORE_IDENTIFY
        }
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios && params.IOS_MULTI_TARGET != GlobalVars.defaultValue) {
            tagVersion = tagVersion + "-" + params.IOS_MULTI_TARGET
        }
        // 生成tag和变更日志
        gitTagLog.genTagAndLog(this, tagVersion, gitChangeLog, "${REPO_URL}", "${GIT_CREDENTIALS_ID}")
    }
}

/**
 * 钉钉通知
 * @type 0 构建失败 1 构建完成  2 审核上架  3 变更记录
 * @msg 自定义消息* @atMobiles 要@的手机号
 */
def dingNotice(int type, msg = '', atMobiles = '') {
    if ("${params.IS_DING_NOTICE}" == 'true') { // 是否钉钉通知
        println("钉钉通知: " + params.NOTIFIER_PHONES)
        def rollbackTag = ""
        if (params.GIT_TAG != GlobalVars.noGit) {
            rollbackTag = "**Git Tag构建版本: ${params.GIT_TAG}**" // Git Tag版本添加标识
        }
        def androidEnvTypeMark = "内测版"  // android环境类型标志
        def iosEnvTypeMark = "内测版"  // ios环境类型标志
        if ("${androidBuildType}".contains(Constants.RELEASE_BUILD)) {
            androidEnvTypeMark = "正式版"
        }
        if ("${params.IOS_SIGN_TYPE}" == Constants.IOS_SIGN_APP_STORE) {
            iosEnvTypeMark = "正式版"
        }

        def crossPlatformTitle = "For"  // 跨平台同时产出多平台包
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter || "${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative || "${PROJECT_TYPE}".toInteger() == GlobalVars.unity) {
            if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.ANDROID}")) {
                crossPlatformTitle += " ${androidEnvTypeMark}Android 🤖 "
            }
            if ("${BUILD_SYSTEM_TYPES}".contains("${Constants.IOS}")) {
                crossPlatformTitle += " ${iosEnvTypeMark}iOS ‍🍏️"
            }
        }

        // 支持多个钉钉群通知
        dingTalkIds.each { dingId ->
            def durationTimeString = "${currentBuild.durationString.replace(' and counting', '').replace('sec', 's')}".replace(' ', '')
            def notifierPhone = params.NOTIFIER_PHONES.split("-")[1].trim()
            if (notifierPhone == "oneself") { // 通知自己
                notifierPhone = "${BUILD_USER_MOBILE}"
            }
            if (type == 0) { // 构建失败
                // 失败信息简介 方面消息通知直接查看 详细失败信息查看Jenkins运行日志
                def failInfo = "" // **失败信息简介: **

                dingtalk(
                        robot: "${dingId}",
                        type: 'MARKDOWN',
                        title: 'CI/CD APP失败通知',
                        text: [
                                "### [${env.JOB_NAME}#${env.BUILD_NUMBER} ${PROJECT_TAG}](${env.BUILD_URL})项目${msg}",
                                "#### 请及时处理 🏃",
                                "#### 打包模式: ${"${PROJECT_TYPE}".toInteger() == GlobalVars.ios ? "${iosBuildType} ${params.IOS_SIGN_TYPE}" : "${androidBuildType}"}",
                                "###### ** 流水线失败原因: [运行日志](${env.BUILD_URL}console) 👈 **",
                                "###### ${failInfo}",
                                "###### Jenkins地址  [查看](${env.JENKINS_URL})   源码地址  [查看](${REPO_URL})",
                                "###### 发布人: ${BUILD_USER}   持续时间: ${durationTimeString}",
                                "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})"
                        ],
                        at: ["${BUILD_USER_MOBILE}"]
                )
            } else if (type == 1) { // 构建完成
                if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter || "${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative || "${PROJECT_TYPE}".toInteger() == GlobalVars.unity) {
                    dingtalk(
                            robot: "${dingId}",
                            type: 'ACTION_CARD',
                            title: "${appInfoName} ${SYSTEM_TYPE_NAME} v${appInfoVersion} 发布通知",
                            text: [
                                    "![screenshot](${qrCodeOssUrl == "" ? uploadResultBuildQRCodeURL : qrCodeOssUrl})",
                                    "### [${appInfoName}${PROJECT_TAG} ${SYSTEM_TYPE_NAME} ${crossPlatformTitle} v${appInfoVersion} #${env.BUILD_NUMBER}](${env.JOB_URL})",
                                    "###### ${rollbackTag}",
                                    "##### 版本信息",
                                    "- 构建分支: ${BRANCH_NAME}",
                                    "- Android 大小: ${androidAppInfoSize}    模式: ${androidBuildType}    构建版本: JDK${JDK_VERSION}",
                                    "- iOS 大小: ${appInfoSize}    模式:  ${iosBuildType} ${params.IOS_SIGN_TYPE}",
                                    "- 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                                    "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL})",
                                    "###### 发布人: ${BUILD_USER}   持续时间: ${durationTimeString}"
                            ],
                            btnLayout: 'V',
                            btns: [
                                    [
                                            title    : "${SYSTEM_TYPE_NAME}应用主页",
                                            actionUrl: "${packageOssUrl == "" ? "${Constants.PGYER_URL}/${uploadResultBuildShortcutUrl}" : "${packageOssUrl}"}"
                                    ],
                                    [
                                            title    : "${BUILD_SYSTEM_TYPES.contains(Constants.IOS) ? "iOS版直接下载安装 📥" : "无iOS版本包 🈳 "}",
                                            actionUrl: "${packageOssUrl == "" ? "${Constants.PGYER_INSTALL_URL}?_api_key=${PGYER_API_KEY}&buildKey=${uploadResultBuildKey}" : "${packageOssUrl}"}"
                                    ],
                                    [
                                            title    : "${BUILD_SYSTEM_TYPES.contains(Constants.ANDROID) ? "Android版直接下载安装 📥" : "无Android版本包 🈳 "}",
                                            actionUrl: "${androidPackageOssUrl == "" ? "${Constants.PGYER_INSTALL_URL}?_api_key=${PGYER_API_KEY}&buildKey=${androidUploadResultBuildKey}" : "${androidPackageOssUrl}"}"
                                    ]
                            ],
                            at: [notifierPhone == '110' ? '' : notifierPhone]
                    )
                } else {
                    dingtalk(
                            robot: "${dingId}",
                            type: 'ACTION_CARD',
                            title: "${appInfoName} ${SYSTEM_TYPE_NAME} v${appInfoVersion} 发布通知",
                            text: [
                                    "![screenshot](${qrCodeOssUrl == "" ? uploadResultBuildQRCodeURL : qrCodeOssUrl})",
                                    "### [${appInfoName}${PROJECT_TAG}${"${PROJECT_TYPE}".toInteger() == GlobalVars.ios ? "${iosEnvTypeMark}iOS ‍🍏️" : "${androidEnvTypeMark}Android 🤖"} v${appInfoVersion} #${env.BUILD_NUMBER}](${env.JOB_URL})",
                                    "###### ${rollbackTag}",
                                    "##### 版本信息",
                                    "- 构建分支: ${BRANCH_NAME}",
                                    "- 大小: ${appInfoSize}   模式: ${"${PROJECT_TYPE}".toInteger() == GlobalVars.ios ? "${iosBuildType} ${params.IOS_SIGN_TYPE}" : "${androidBuildType}    构建版本: JDK${JDK_VERSION}"}",
                                    "- 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                                    "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL})",
                                    "###### 发布人: ${BUILD_USER}   持续时间: ${durationTimeString}"
                            ],
                            btnLayout: 'V',
                            btns: [
                                    [
                                            title    : "APP应用主页",
                                            actionUrl: "${packageOssUrl == "" ? "${Constants.PGYER_URL}/${uploadResultBuildShortcutUrl}" : "${packageOssUrl}"}"
                                    ],
                                    [
                                            title    : "${SYSTEM_TYPE_NAME}版直接下载安装 📥",
                                            actionUrl: "${packageOssUrl == "" ? "${Constants.PGYER_INSTALL_URL}?_api_key=${PGYER_API_KEY}&buildKey=${uploadResultBuildKey}" : "${packageOssUrl}"}"
                                    ]
                            ],
                            at: [notifierPhone == '110' ? '' : notifierPhone]
                    )
                }
            } else if (type == 2) { // Android应用商店和App Store审核上架通知
                if ("${PROJECT_TYPE}".toInteger() == GlobalVars.android || "${PROJECT_TYPE}".toInteger() == GlobalVars.flutter) {
                    def failNoticePhone = "" // 失败通知发布人
                    def huaweiAppGalleryMsg = ""  // 华为应用商店信息
                    // 应用商店封面图
                    def huaweiMarketImage = "https://static.cnbetacdn.com/article/2020/0224/fcb057e721188b4.png"
                    if ("${huaweiApplicationUrl}" != "") {
                        huaweiAppGalleryMsg = "**自动提交[华为商店审核](https://developer.huawei.com/consumer/cn/service/josp/agc/index.html#/myApp)成功 ✅**"
                    } else if (params.ANDROID_STORE_IDENTIFY == "huawei") {
                        huaweiAppGalleryMsg = "**自动提审上架华为应用商店失败 🚨**"
                        failNoticePhone = "${BUILD_USER_MOBILE}"
                    }
                    def xiaomiMarketMsg = ""  // 小米应用商店信息
                    // 应用商店封面图
                    def xiaomiMarketImage = "https://www.hp.com/content/dam/sites/worldwide/personal-computers/consumer/quickdrop/android-stores/Mi_logo.png"
                    // 小米商店图片
                    if ("${xiaomiMarketUrl}" != "") {
                        xiaomiMarketMsg = "**自动提交[小米商店审核](https://dev.mi.com/)成功 ✅**"
                    } else if (params.ANDROID_STORE_IDENTIFY == "xiaomi") {
                        xiaomiMarketMsg = "**自动提审上架小米应用商店失败 🚨**"
                        failNoticePhone = "${BUILD_USER_MOBILE}"
                    }
                    dingtalk(
                            robot: "${dingId}",
                            type: 'ACTION_CARD',
                            title: "${appInfoName} Android v${appInfoVersion}商店审核上架通知",
                            text: [
                                    "![screenshot](${params.ANDROID_STORE_IDENTIFY == "xiaomi" ? xiaomiMarketImage : huaweiMarketImage})",
                                    "### [${appInfoName}${PROJECT_TAG}${"审核上架${androidEnvTypeMark}Android 🤖"} v${appInfoVersion} #${env.BUILD_NUMBER}](${env.JOB_URL})",
                                    "###### ${rollbackTag}",
                                    "##### 版本信息",
                                    "- 构建分支: ${BRANCH_NAME}",
                                    "- 大小: ${appInfoSize}   模式:  ${androidBuildType}",
                                    "- 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                                    "##### ${huaweiAppGalleryMsg}",
                                    "##### ${xiaomiMarketMsg}",
                                    "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL})",
                                    "###### 发布人: ${BUILD_USER}   持续时间: ${durationTimeString}"
                            ],
                            btns: [
                                    [
                                            title    : "APP应用主页",
                                            actionUrl: "${packageOssUrl == "" ? "${Constants.PGYER_URL}/${uploadResultBuildShortcutUrl}" : "${packageOssUrl}"}"
                                    ],
                                    [
                                            title    : "${params.ANDROID_STORE_IDENTIFY == "xiaomi" ? "小米" : "华为"}商店主页",
                                            actionUrl: "${params.ANDROID_STORE_IDENTIFY == "xiaomi" ? xiaomiMarketUrl : huaweiApplicationUrl}"
                                    ],
                                    [
                                            title    : "${SYSTEM_TYPE_NAME}版直接下载安装 📥",
                                            actionUrl: "${packageOssUrl == "" ? "${Constants.PGYER_INSTALL_URL}?_api_key=${PGYER_API_KEY}&buildKey=${uploadResultBuildKey}" : "${packageOssUrl}"}"
                                    ]
                            ],
                            at: [notifierPhone == '110' ? "${failNoticePhone}" : notifierPhone]
                    )
                }
                if ("${PROJECT_TYPE}".toInteger() == GlobalVars.ios || "${PROJECT_TYPE}".toInteger() == GlobalVars.flutter) {
                    dingtalk(
                            robot: "${dingId}",
                            type: 'ACTION_CARD',
                            title: "${appInfoName} iOS v${appInfoVersion} App Store审核上架通知",
                            text: [
                                    "![screenshot](@lADOpwk3K80C0M0FoA)",
                                    "### [${appInfoName}${PROJECT_TAG}${"审核上架${iosEnvTypeMark}iOS ‍🍏️"} v${appInfoVersion} #${env.BUILD_NUMBER}](${env.JOB_URL})",
                                    "###### ${rollbackTag}",
                                    "##### 版本信息",
                                    "- 构建分支: ${BRANCH_NAME}",
                                    "- 大小: ${appInfoSize}   模式: ${iosBuildType} ${params.IOS_SIGN_TYPE}",
                                    "- 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                                    "##### 发布类型: ${params.IS_AUTO_SUBMIT_FOR_REVIEW == true ? "自动" : "手动"}提审 ${params.IS_AUTO_RELEASE_APP_STORE == true ? "自动" : "手动"}上架",
                                    "##### 自动提交App Store Connect成功 ✅",
                                    "###### Jenkins  [运行日志](${env.BUILD_URL}console)   Git源码  [查看](${REPO_URL})",
                                    "###### 发布人: ${BUILD_USER}   持续时间: ${durationTimeString}"
                            ],
                            btns: [
                                    [
                                            title    : 'App Store Connect',
                                            actionUrl: "https://appstoreconnect.apple.com/apps/"
                                    ]
                            ],
                            at: [notifierPhone == '110' ? "${BUILD_USER_MOBILE}" : notifierPhone]
                    )
                }
            } else if (type == 3) { // 变更记录
                if ("${IS_NOTICE_CHANGE_LOG}" == 'true') {
                    def gitChangeLog = ""
                    if ("${Constants.APP_DEFAULT_VERSION_COPYWRITING}" == params.APP_VERSION_DESCRIPTION) {
                        gitChangeLog = changeLog.genChangeLog(this, 10).replaceAll("\\;", "\n")
                    } else {
                        // 使用自定义文案
                        gitChangeLog = "${params.APP_VERSION_DESCRIPTION}".replace("\\n", "\\n ##### ")
                    }

                    if ("${gitChangeLog}" != GlobalVars.noChangeLog) {
                        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.flutter || "${PROJECT_TYPE}".toInteger() == GlobalVars.reactNative || "${PROJECT_TYPE}".toInteger() == GlobalVars.unity) {
                            dingtalk(
                                    robot: "${dingId}",
                                    type: 'MARKDOWN',
                                    title: "${appInfoName} ${SYSTEM_TYPE_NAME} v${appInfoVersion} 发布日志",
                                    text: [
                                            "### ${appInfoName}${PROJECT_TAG} ${SYSTEM_TYPE_NAME} ${crossPlatformTitle} v${appInfoVersion} 发布日志 🎉",
                                            "#### Android模式: ${androidBuildType}",
                                            "#### iOS模式:  ${iosBuildType} ${params.IOS_SIGN_TYPE}",
                                            "${gitChangeLog}",
                                            ">  👉  前往 [变更日志](${REPO_URL.replace('.git', '')}/blob/${BRANCH_NAME}/CHANGELOG.md) 查看",
                                            "###### 发布人: ${BUILD_USER}",
                                            "###### 发布时间: ${Utils.formatDate()} (${Utils.getWeek(this)})"
                                    ],
                                    at: []
                            )
                        } else {
                            dingtalk(
                                    robot: "${dingId}",
                                    type: 'MARKDOWN',
                                    title: "${appInfoName} ${SYSTEM_TYPE_NAME} v${appInfoVersion} 发布日志",
                                    text: [
                                            "### ${appInfoName}${PROJECT_TAG}${"${PROJECT_TYPE}".toInteger() == GlobalVars.ios ? "${iosEnvTypeMark}iOS" : "${androidEnvTypeMark}Android"} v${appInfoVersion} 发布日志 🎉",
                                            "#### 打包模式: ${"${PROJECT_TYPE}".toInteger() == GlobalVars.ios ? "${iosBuildType} ${params.IOS_SIGN_TYPE}" : "${androidBuildType}"}",
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
    }
}
