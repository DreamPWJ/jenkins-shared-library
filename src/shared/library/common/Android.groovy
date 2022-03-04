package shared.library.common

import shared.library.Utils
import shared.library.common.*

/**
 * @author 潘维吉
 * @date 2021/9/29 12:29
 * @email 406798106@qq.com
 * @description Android相关
 * Android应用商店自动化发布等
 */
class Android implements Serializable {

    /**
     *  初始化环境变量
     */
    static def initEnv(ctx) {
        try {
            // 动态获取环境变量使用  which gradle 命令响应结果
            /*  def path = Utils.getShEchoResult(ctx, "which gradle")
                if (path?.trim()) {*/
            def path = "/opt/homebrew/bin/gradle"
            // }
            ctx.env.PATH = "${ctx.env.PATH}:${path}" //添加了系统环境变量上
        } catch (e) {
            ctx.println("初始化Android环境变量失败")
            ctx.println(e.getMessage())
        }
    }

    /**
     *  设置Android版本号 不从代码内获取情況
     */
    static def setVersion(ctx, version) {
        ctx.println("设置Android版本号: " + version)
        try {
            def gradleBuildFile = "${ctx.env.WORKSPACE}/app/build.gradle"
            def gradleInfo = ctx.readFile(file: gradleBuildFile)
            // 根据版本号自动生成构建号
            def versionCode = version.replaceAll("\\.", "") as int
            ctx.writeFile file: "${gradleBuildFile}", text: "${gradleInfo}"
                    .replaceAll('rootProject\\.ext\\.android\\["versionName"\\]', "\"${version}\"")
                    .replaceAll('rootProject\\.ext\\.android\\["versionCode"\\]', "${versionCode}")
        } catch (e) {
            ctx.println(e.getMessage())
            ctx.error("手动设置Android版本号失败 ❌")
        }
    }


    /**
     * 国内除了华为、小米、vivo、oppo其他主流国内Android应用商店暂时没有提供API方式上传
     * 可以考虑Playwright或Puppeteer模拟用户行为自动完成上传审核上架等
     */

    /**
     *  自动提审上架谷歌Play Store应用商店
     */
    static def googlePlayStore(ctx) {
        // 基于fastlane自带的行为上传
        // 文档地址: https://docs.fastlane.tools/actions/upload_to_play_store/
        ctx.sh "fastlane google_play_store"
    }

    /**
     *  华为应用商店
     *  AppGallery Connect API: https://developer.huawei.com/consumer/cn/doc/development/AppGallery-connect-Guides/agcapi-overview
     */
    static def huaweiMarket(ctx) {
        // 通过fastlane插件huawei_appgallery_connect实现自动化发布
        // https://github.com/shr3jn/fastlane-plugin-huawei_appgallery_connect
        ctx.sh "fastlane huawei_app_gallery apk_path:${ctx.androidPackagesOutputDir}/${ctx.androidApkName} " +
                "app_id:${ctx.huaweiAppGalleryAppId} is_aab:${ctx.IS_ANDROID_AAB} release_note:'${ctx.params.APP_VERSION_DESCRIPTION}'"
    }

    /**
     *  小米应用商店: https://dev.mi.com/console/
     *  应用自动发布接口: https://dev.mi.com/console/doc/detail?pId=33
     */
    static def xiaomiMarket(ctx, map, privateKey) {
        // fastlane插件: https://github.com/istobran/fastlane-plugin-xiaomi_devupload
        // 复制证书到指定目录
        ctx.sh "cp ${ctx.env.WORKSPACE}/ci/_jenkins/android/xiaomi.${ctx.appInfoIdentifier}.cer  ${ctx.env.WORKSPACE}/fastlane/"
        // 执行应用上传应用小米商店
        ctx.sh "fastlane xiaomi_market apk_path:${ctx.env.WORKSPACE}/${ctx.androidPackagesOutputDir}/${ctx.androidApkName} " +
                "user_name:${map.xiaomi_market_user_name} app_name:${ctx.appInfoName} package_name:${ctx.appInfoIdentifier} " +
                "desc:'${ctx.params.APP_VERSION_DESCRIPTION}' private_key:${privateKey}"
    }

    /**
     *  Oppo应用商店
     *  登录地址: https://open.oppomobile.com/
     *  应用自动发布接口: https://open.oppomobile.com/wiki/doc#id=10998
     */
    static def oppoMarket(ctx) {

    }

    /**
     *  Vivo应用商店
     *  登录地址: https://id.vivo.com.cn/?callback=https://dev.vivo.com.cn
     *  应用自动发布接口: https://dev.vivo.com.cn/documentCenter/doc/326
     */
    static def vivoMarket(ctx) {

    }

    /**
     *  Meizu应用商店
     *  登录地址: http://open.flyme.cn/
     *  应用自动发布接口:  暂无
     */
    static def meizuMarket(ctx) {

    }

    /**
     *  Android应用市场提交审核后创建定时检测任务 审核状态的变化后通知
     */
    static def androidMarketCheckState(ctx, map) {

    }

    /**
     *  获取具体android应用市场的唯一标识id
     *  Android应用商店  单个jenkins job配置多个Saas项目情况
     */
    static def getAndroidMarketId(ctx, androidMarketIds) {
        if (androidMarketIds.contains(",")) {
            def androidStoreIdentifyArray = "${ctx.CUSTOM_ANDROID_FLAVOR}".split("\n") as ArrayList
            def androidStoreIdentifyIndex = androidStoreIdentifyArray.indexOf(ctx.params.ANDROID_STORE_IDENTIFY)

            def androidMarketIdArray = "${androidMarketIds}".split(",") as ArrayList
            androidMarketIds = androidMarketIdArray[androidStoreIdentifyIndex]
        }
        return androidMarketIds
    }

    /**
     *  aab格式转成可安装的apk  因为aab格式是无法直接安装的 也可用fastlane插件实现
     *  bundletool文档: https://developer.android.com/studio/command-line/bundletool
     */
    static def aabToApk(ctx, aabFilePath) {
        if ("${ctx.IS_ANDROID_AAB}" == 'true') {
            // --mode=universal生成单个通用 APK 文件
            def apksFile = "app.apks"
            ctx.sh "java -jar ${ctx.SYSTEM_HOME}/Library/bundletool.jar build-apks " +
                    "--bundle=$aabFilePath --output=$apksFile --mode=universal"
            ctx.sh "mv $apksFile  app.zip"
            ctx.zip(dir: "app.zip", glob: '', zipFile: "universal-apk")
            ctx.sh "cd universal-apk && ls"
        }
    }

}
