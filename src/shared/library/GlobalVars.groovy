#!/usr/bin/env groovy
package shared.library;

/**
 * @author 潘维吉
 * @date 2020/10/9 9:20
 * @email 406798106@qq.com
 * @description 静态常量
 */
class GlobalVars implements Serializable {
    // 在 pipeline 引用方式如下:
    // import shared.library.GlobalVars
    // println GlobalVars.release

    /**
     * 服务部署方式
     */
    static String release = "release"  // 发布
    static String rollback = "rollback" // 回滚
    static String stop = "stop" // 停机下线

    /**
     * 前后端项目类型
     */
    static int frontEnd = 1  // Web前端
    static int backEnd = 2 // 后端

    /**
     * 服务端语言类型
     */
    static int Java = 1  // Java语言
    static int Go = 2 // Go语言
    static int Python = 3 // Python语言
    static int Php = 4 // PHP语言
    static int Cpp = 5 // C++语言
    static int Kotlin = 6 // Kotlin语言
    static int Cs = 7 // C#语言 DoNet框架
    static int Node = 8 // Node语言
    static int Rust = 9 // Rust语言
    static int Dart = 10 // Dart语言
    static int Ruby = 11 // Ruby语言
    static int C = 12 // C语言

    /**
     * 小程序项目类型
     */
    static int miniNativeCode = 1  // 原生小程序
    static int taro = 2 // Taro跨端小程序
    static int uniApp = 3 // uni-app跨端小程序
    static int mpvue = 4 // mpvue跨端小程序

    /**
     * APP客户端项目类型
     */
    static int android = 1  // Android原生项目
    static int ios = 2 // iOS原生项目
    static int flutter = 3 // Flutter跨平台项目
    static int reactNative = 4 // ReactNative跨平台项目
    static int unity = 5 // Unity跨平台项目
    static int webView = 6 // WebView项目
    static int webGl = 7 // WebGL项目
    static int kotlinMultiPlatformMobile = 8 // Kotlin  MultiPlatform Mobile跨平台项目
    static int ionic = 9 // Ionic跨平台项目
    static int nativeScript = 10 // NativeScript跨平台项目
    static int xamarin = 11 // Xamarin跨平台项目

    /**
     * Web端技术实现类型
     */
    static int npmWeb = 1 // Npm生态与静态Web项目
    static int flutterWeb = 2 // Flutter生态Web项目
    static int reactNativeWeb = 3 // ReactNative生态Web项目
    static int unityWeb = 4 // Unity生态Web项目
    static int webAssembly = 5 // WebAssembly生态Web项目

    /**
     * 桌面客户端项目类型
     */
    static int AllDesktop = 0 // Windows、MacOS、Linux全部系統
    static int Windows = 1 // Windows系統
    static int MacOS = 2 // MacOS系統
    static int Linux = 3 // Linux系統

    /**
     * 桌面客户端技术实现类型
     */
    static int desktopNative = 0 // 原生桌面技术(如C++ For Windows、Linux, Swift For MacOS)
    static int electron = 1 // Electron跨平台桌面项目
    static int desktopFlutter = 2 // Flutter跨平台桌面项目
    static int desktopUnity = 3 // Unity跨平台桌面项目
    static int composeMultiPlatform = 4 // Kotlin Compose MultiPlatform跨平台桌面项目
    static int desktopQt = 5 // Qt (C/C++代码)跨平台桌面项目

    /**
     *  Java框架类型
     */
    static int SpringBoot = 1 // Spring Boot框架
    static int SpringMVC = 2 // Spring MVC框架

    /**
     * CI代码库 自动同步部署脚本和配置等
     */
    static String CI_REPO_URL = "http://47.102.214.33/lanneng_develop/jenkins-shared-library.git" // https://github.com/DreamPWJ/jenkins-shared-library.git

    /**
     * Git常量
     */
    static String noChangeLog = "No New Changes"  // 没有变更记录
    static String noGit = "NONE"  // 无git标识
    static String defaultBranch = "master"  // 默认分支

    /**
     * 默认常量
     */
    static String defaultValue = "Default"  // 默认值

    /**
     * Git提交记录
     */
    static String gitCommitFeature = "feat"  // 新增功能标识
    static String gitCommitFix = "fix"  // 修复问题标识

}
