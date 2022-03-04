package shared.library.common

import shared.library.GlobalVars
import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2021/9/29 12:29
 * @email 406798106@qq.com
 * @description Flutter跨平台相关
 */
class Flutter implements Serializable {

    /**
     *  初始化环境变量
     *  官网安装文档: https://docs.flutter.dev/get-started/install
     */
    static def initEnv(ctx) {
        try {
            // flutter: command not found 执行加入环境变量
            // export PATH="$PATH:`pwd`/flutter/bin"
            ctx.env.PATH = "${ctx.env.PATH}:${ctx.SYSTEM_HOME}/Downloads/flutter/bin" //添加了系统环境变量上
            ctx.sh " flutter --version "
        } catch (e) {
            ctx.println("初始化Flutter环境变量失败")
            ctx.println(e.getMessage())
        }
    }

    /**
     * Flutter构建应用 前置条件
     */
    static def buildPrecondition(ctx) {
        // flutter 版本和依赖检测
        // sh "flutter --version && flutter doctor"
        // flutter 版本升级
        // sh "flutter channel stable && flutter upgrade"
        // 第一次初始化Flutter 项目android目录下手动执行gradle wrapper命令 因为CI没有权限 允许在没有安装gradle的情况下运行Gradle任务 解决gradlew is not found (No such file or directory)

        ctx.println("Flutter构建依赖下载更新 📥 ")
        // 使用官方国内镜像加速下载
        ctx.sh " export PUB_HOSTED_URL=https://pub.flutter-io.cn "
        ctx.sh " export FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn "
        // Flutter的 pubspec.yaml内直接引用代码库情况 新增仓库账号信息下载pub源码仓库包方式
        // setPubspecGitAccount(ctx)
        // 清除修复缓存 缓存导致构建失败等
        // ctx.sh "rm -rf ${ctx.env.WORKSPACE}/build"
        ctx.sh "flutter clean"
        // ctx.sh "flutter pub cache repair"
        // 下载仓库依赖
        ctx.sh "flutter pub get"
        // 更新包依赖 解决缓存机制可能导致依赖不能更新
        ctx.sh "flutter packages upgrade"
        // Flutter json_serializable自动生成.g.dart文件   --delete-conflicting-outputs 解决 pub finished with exit code 78 错误
        ctx.sh "flutter packages pub run build_runner build --delete-conflicting-outputs || true"

        if (!ctx.fileExists("${ctx.env.WORKSPACE}/lib/main.dart")) {
            ctx.println("Flutter源码中/lib/main.dart主文件不存在或不在标准目录下 ❌")
            // 兼容main.dart可能在不同目录的情况
            def mainDart = Utils.getShEchoResult(ctx, " find . -name \"main.dart\" ")
            ctx.sh "mv ${mainDart} ${ctx.env.WORKSPACE}/lib/main.dart"
        }

    }

    /**
     * 设置Flutter应用版本号和构建号
     */
    static def setVersion(ctx, version) {
        ctx.println("设置Flutter版本号: " + version)
        try {
            def yamlFile = "${ctx.env.WORKSPACE}/pubspec.yaml"
            def pubspecData = ctx.readYaml file: yamlFile
            // 根据版本号自动生成构建号
            def buildCode = version.replaceAll("\\.", "")
            // pubspec.yaml文件内version 使用+加号连接组合 前面是版本号后面是构建号
            pubspecData.version = "${version}" + "+" + "${buildCode}"
            // 写入本地版本文件
            // pubspecData = pubspecData.substring(1, pubspecData.length() - 1) // 去掉首尾字符
            ctx.sh " rm -f ${yamlFile} " // 如果写入失败 删除源文件重新写入
            ctx.writeYaml file: yamlFile, data: pubspecData
        } catch (e) {
            ctx.println(e.getMessage())
            ctx.error("手动设置Flutter版本号失败 ❌")
        }
    }

    /**
     * Flutter构建Android APP应用
     */
    static def buildAndroidApp(ctx) {
        def dynamicParams = "" // 动态参数
        def flutterEnvType = "${ctx.androidBuildType.toLowerCase()}"
        def flutterFlavor = "${ctx.params.ANDROID_STORE_IDENTIFY}"
        if ("${flutterFlavor}" != "${GlobalVars.defaultValue}") {
            // 多产品多ProductFlavor动态参数组合
            dynamicParams = " --flavor ${flutterFlavor} -t ${ctx.env.WORKSPACE}/lib/targets/main_${flutterFlavor}.dart "
            ctx.println("Flutter打包动态参数: " + dynamicParams)
        }
        // Flutter命令打包并动态配置多环境
        // --dart-define 构建应用程序时传递环境变量 --flavor区分不同产品  指定不同的主要入口点文件 默认-t lib/main.dart
        ctx.sh " flutter build apk --${flutterEnvType}  ${dynamicParams} --dart-define=API_ENV=${flutterEnvType}  "
    }

    /**
     * Flutter构建iOS APP应用
     */
    static def buildiOSApp(ctx) {
        // flutter build ios, That way flutter will generate the new Podfile for flutter 2.0
        // ctx.sh "find . -name \"Podfile\" -execdir pod install \\;"  // Install CocoaPods dependencies
        // ctx.sh "rm -f Podfile && rm -f Podfile.lock"
        def dynamicParams = "" // 动态参数
        def flutterEnvType = "${ctx.iosBuildType.toLowerCase()}"
        def flutterTarget = "${ctx.params.IOS_MULTI_TARGET}"
        if ("${flutterTarget}" != "${GlobalVars.defaultValue}") {
            // 多产品多Target动态参数组合
            dynamicParams = " --flavor ${flutterTarget}  -t ${ctx.env.WORKSPACE}/lib/targets/main_${flutterTarget}.dart "
            ctx.println("Flutter打包动态参数: " + dynamicParams)
        }
        // Flutter命令打包并动态配置多环境
        // --dart-define 构建应用程序时传递环境变量 --flavor区分不同产品  指定不同的主要入口点文件 默认-t lib/main.dart
        // flutter build ios命令编译出Xcode .app工程目录  --no-codesign现在不需要签名，因为fastlane会在构建归档时签名 --release解决iOS14 debug模式app只能从工具启动问题
        ctx.sh " flutter build ios --release --no-codesign  ${dynamicParams} --dart-define=API_ENV=${flutterEnvType}  "
    }

    /**
     * Flutter构建Web应用
     */
    static def buildWeb(ctx) {
        if (!ctx.fileExists("${ctx.env.WORKSPACE}/web")) {
            // ctx.sh "flutter create ."
            ctx.sh "flutter create --org com.example ." // 将web支持添加到已有的项目中 --org 指定包名
        }

        // 构建Flutter For Web   构建产物静态文件在 build/web/
/*        if (!ctx.fileExists("${ctx.env.WORKSPACE}/build/web")) {
            // ctx.sh "flutter run -d chrome"
            ctx.sh "flutter clean" // 如果build文件下没有构建出web产物 先清空重试
        }*/

        ctx.println("执行Flutter打包Web应用 🚀")
        ctx.sh "flutter config --enable-web" // 开启web配置
        // 构建使用 dart2js 方案  --dart-define 构建应用程序时传递环境变量  指定不同的dart文件 -t lib/main.dart
        // 可以分别包含--web-renderer html 或--web-renderer canvaskit在 HTML 或 CanvasKit 渲染器之间进行选择 auto（默认）- 自动选择要使用的渲染器。在应用程序在移动浏览器中运行时选择 HTML 渲染器，在应用程序在桌面浏览器中运行时选择 CanvasKit 渲染器
        // 解决Flutter Web首屏白屏过慢 CDN静态文件 需要加载canvaskit.wasm和canvaskit.js资源过大和国外存储导致 编译的时候使用--dart-define=FLUTTER_WEB_CANVASKIT_URL=https://cdn.jsdelivr.net/npm/canvaskit-wasm@0.32.0/bin/
        // Web首屏性能体验优化参考文章: https://www.jianshu.com/p/e61165cde5ab
        ctx.sh "flutter build web --dart-define=FLUTTER_WEB_CANVASKIT_URL=https://unpkg.zhimg.com/canvaskit-wasm@0.32.0/bin/"

    }

    /**
     * Flutter构建Windows桌面端应用
     */
    static def buildWindowsDesktop(ctx) {
        if (!ctx.isUnix()) {
            // Flutter For Desktop同时构建Windows、MacOS、Linux系统安装包
            // !!!注意不同平台的包需要在在不同平台上构建 在 Windows 上构建 Windows 应用程序，在 macOS 上构建 macOS 应用程序，在 Linux 上构建 Linux 应用程序
            // 为已有的应用添加桌面支持命令 flutter create --platforms=windows,macos,linux .

            // 使用Docker镜像方式构建Windows桌面端, Docker镜像也需要安装在各自平台上构建: https://hub.docker.com/r/openpriv/flutter-desktop
            if (!ctx.fileExists("${ctx.env.WORKSPACE}/windows")) {
                // 为已有的应用添加桌面支持
                ctx.sh "flutter create --org com.example --platforms=windows ."
            }
            ctx.dir("windows") {
                // flutter config --enable-windows-uwp-desktop
                // flutter run -d windows  // 需要安装Visual Studio工具开发环境并选择"Desktop development with C++"插件后启动
                ctx.sh """
                 flutter config --enable-windows-desktop     
                 flutter build windows --release
                """
            }
        }
    }

    /**
     * Flutter构建MacOS桌面端应用
     */
    static def buildMacOSDesktop(ctx) {
        if (ctx.isUnix()) {
            if (!ctx.fileExists("${ctx.env.WORKSPACE}/macos")) {
                // 为已有的应用添加桌面支持
                ctx.sh "flutter create --org com.example --platforms=macos ."
            }
            ctx.dir("macos") {
                ctx.env.PATH = "${ctx.env.PATH}:/usr/local/bin:/opt/homebrew/lib/ruby/gems/3.0.0/bin:${ctx.GEM_HOME}"
                ctx.sh "pod --version"
                ctx.retry(12) { // pod install下载依赖可能因为网络等失败 自动重试几次
                    // --repo-update如果Podfile有更新 则下载最新版本
                    ctx.sh "find . -name \"Podfile\" -execdir pod install --repo-update \\;"
                }
                // 要发布你的 macOS 软件，可选择 将 app 提交至 Mac App Store，或者直接生成 .app 文件，并在自己的网站上发布。
                // 你需要对自己的 macOS 软件进行公证，然后才能在 macOS App Store 之外的渠道发布
                // Flutter打包MacOS系统文档: https://docs.flutter.dev/deployment/macos
                // 命令编译出Xcode .app工程目录 再执行Fastlane或Codemagic CLI签名打包
                ctx.sh """
             flutter config --enable-macos-desktop
             flutter build macos --release
                """
            }
        }
    }

    /**
     * Flutter构建Linux桌面端应用
     */
    static def buildLinuxDesktop(ctx) {
        if (ctx.isUnix()) {
            if (!ctx.fileExists("${ctx.env.WORKSPACE}/linux")) {
                // 为已有的应用添加桌面支持
                ctx.sh "flutter create --org com.example --platforms=linux ."
            }
            ctx.dir("linux") {
                ctx.sh """
               flutter config --enable-linux-desktop
               flutter build linux --release
                  """
            }
        }
    }

    /**
     * Flutter构建嵌入式应用
     */
    static def buildEmbedded(ctx) {
        ctx.sh ""
    }

    /**
     * Flutter混淆 Dart 代码
     */
    static def obfuscationCode(ctx) {
        // 代码混淆是修改应用程序的二进制文件以使其更难被人类理解的过程。混淆会在您编译的 Dart 代码中隐藏函数和类名称，使攻击者难以对您的专有应用程序进行逆向工程
        // 目前支持的目标apk，appbundle，ipa，ios   该--split-debug-info标志指定了 Flutter 可以输出调试文件的目录
        // 文档地址: https://docs.flutter.dev/deployment/obfuscate
        ctx.sh "flutter build apk --obfuscate --split-debug-info=/<project-name>/<directory>"
    }

    /**
     * Flutter的 pubspec.yaml内直接引用代码库情况 新增仓库账号信息下载pub源码仓库包方式
     */
    static def setPubspecGitAccount(ctx) {
        try {
            //仓库直接添加授权用户和密码示例 http://panweiji:panweiji666@git.panweiji.com/panweiji-mobile/flutter-jpush.git
            def yamlFile = "${ctx.env.WORKSPACE}/pubspec.yaml"
            def pubspecData = ctx.readYaml file: yamlFile
            def httpGitUrl = "https://github.com/DreamPWJ/" // 可动态传入Git Http仓库地址
            // Git SSH仓库地址
            def sshGitUrl = httpGitUrl.replace("http://", "git@").replace("https://", "git@").replaceFirst("/", ":")
/*           ctx.withCredentials([ctx.usernamePassword(credentialsId: ctx.GIT_CREDENTIALS_ID,
                    usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                ctx.script {
                    ctx.env.ENCODED_GIT_PASSWORD = URLEncoder.encode(ctx.GIT_PASSWORD, "UTF-8")
                }
                  def userPassWordUrl = " http://${ctx.GIT_USERNAME}:${ctx.ENCODED_GIT_PASSWORD}" +
                      "@${ctx.REPO_URL.toString().replace("http://", "").replace("https://", "")} "
            }*/
            pubspecData = pubspecData.replace(httpGitUrl, sshGitUrl)
            ctx.sh " rm -f ${yamlFile} " // 如果写入失败 删除源文件重新写入
            ctx.writeYaml file: yamlFile, data: pubspecData
        } catch (e) {
            ctx.println(e.getMessage())
            ctx.println("新增仓库账号信息下载pub源码仓库包方式失败 ❌")
        }
    }

}
