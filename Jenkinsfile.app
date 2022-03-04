#!groovy
@Library('jenkins-shared-library@dev') _

/**
 * @author 潘维吉
 * @description 核心Pipeline代码 针对Android和iOS项目CI/CD的脚本
 * 注意 本文件在Git位置和名称不能随便改动 配置在jenkins里
 */
// Pipeline需要安装的插件

// 根据不同环境项目配置不同参数
def map = [:]

// 保持构建的最大个数
map.put('build_num_keep', 1)

// 默认要构建的Git分支
map.put('default_git_branch', 'master')

// 是否非正式环境设置App的icon图标徽章 易于区分环境和版本
map.put('is_icon_add_badge', true)
// 是否Pipeline内脚本钉钉通知  总开关
map.put('is_ding_notice', false)
// 是否通知变更记录
map.put('is_notice_change_log', false)
// 是否在生产环境发布成功后自动给Git仓库打Tag版本和生成变更记录
map.put('is_git_tag', false)

// 项目标签或项目简称
map.put('project_tag', ' ')

// 分发平台账号
map.put('pgyer_api_key', "a06ee74d4063eb58b7fed20e7fde6306")

// jenkins分布式构建节点名称
map.put('jenkins_node', 'macos-test')

// 构建环境变量
map.put('jdk', '8')

// 相关信任标识
map.put('ci_git_credentials_id', '45392b97-5c21-4451-b323-bbf104f70e51')
map.put('git_credentials_id', '45392b97-5c21-4451-b323-bbf104f70e51')
map.put('ding_talk_credentials_id', 'ba0ebec7-73ad-4a26-af8b-d15c470b1328') // 支持多个群通知 逗号,分割 不要空格

// Android应用商店渠道号
map.put('android_store_identify', "lexiang\nhuawei\nxiaomi\noppo\nvivo\nsansumg\nmeizu")

// Android应用商店自动化审核上架
map.put('huawei_app_gallery_client_id', "")
map.put('huawei_app_gallery_client_secret', "")
map.put('xiaomi_market_user_name', "406798106@qq.com")

// Apple Store相关信息
map.put('apple_id', "panweiji@163.com")
map.put('apple_password', "panweiji2022")
map.put('apple_team_id', "KTURYE6Q79")

// App Store Connect API相关信息
map.put('apple_store_connect_api_key_id', "RK28R5AN27")
map.put('apple_store_connect_api_issuer_id', "")
map.put('apple_store_connect_api_key_file_path', "/Library/AuthKey_RK28R5AN27.p8")

// iOS审核信息
map.put('ios_review_first_name', "潘")
map.put('ios_review_last_name', "维吉")
map.put('ios_review_phone_number', "+86 18863302302")
map.put('ios_review_email_address', "406798106@qq.com")

// 调用核心通用Pipeline
appSharedLibrary(map)


// ---------------------------------------------------------------------------------------------------------------------

// https://github.com/DreamPWJ/jenkins-shared-library.git  Jenkinsfile.app

/*
test-android-app
测试Android APP流水线
JSON_PARAMS:
{
    "PROJECT_TYPE" : "1",
    "REPO_URL" : "https://github.com/DreamPWJ/member/member-android.git",
    "CUSTOM_ANDROID_BUILD_TYPE" : "DebugT,PreRelease",
    "IS_ANDROID_STORE_IDENTIFY" : true,
    "IS_ANDROID_AAB" : false
}
ANDROID_APP_INFO:
{
   "huaweiAppGalleryAppId" : "100324957",
   "xiaomiMarketPrivateKey" : ""
}

test-ios-app
测试IOS APP流水线
JSON_PARAMS:
{
    "PROJECT_TYPE" : "2",
    "REPO_URL" : "https://github.com/DreamPWJ/panweiji-mobile/ios-anhomemanager.git",
    "DEFAULT_GIT_BRANCH" : "dev",
    "CUSTOM_IOS_BUILD_TYPE" : "Test,PreRelease",
    "IOS_APP_IDENTIFIER" : "com.panweiji.propertyservice.store",
    "IOS_PROJECT_LEVEL_DIR" : "panweijiApp"
}
IOS_APP_REVIEW_INFO:
{
   "demoUser" : "19912522910",
   "demoPassword" : "123"
}

test-flutter-app
测试Flutter APP流水线
JSON_PARAMS:
{
    "PROJECT_TYPE" : "3",
    "REPO_URL" : "https://github.com/DreamPWJ/panweiji-iot/flutter-demo.git",
    "BUILD_SYSTEM_TYPES" : "Android,iOS",
    "IOS_APP_IDENTIFIER" : "com.flutter.panweiji",
    "IOS_SCHEME_NAME": "Runner"
}

test-react-native-app
测试React Native APP流水线
JSON_PARAMS:
{
    "PROJECT_TYPE" : "4",
    "REPO_URL" : "https://github.com/DreamPWJ/react-native-demo.git",
    "BUILD_SYSTEM_TYPES" : "Android,iOS",
    "IOS_APP_IDENTIFIER" : "com.reactnative.panweiji",
    "IOS_SCHEME_NAME": "ReactNativeDemo"
}

*/
