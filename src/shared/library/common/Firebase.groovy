package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/9/26 13:22
 * @email 406798106@qq.com
 * @description Firebase平台相关
 */
class Firebase implements Serializable {

    /**
     *  初始化环境
     *  设置 Google Cloud SDK 环境  https://cloud.google.com/sdk/gcloud/
     */
    static def initEnv(ctx) {
        try {
            // 验证是否安装
            ctx.sh "./google-cloud-sdk/install.sh --help"
        } catch (e) {
            ctx.println("Firebase的Google Cloud SDK环境未安装, 自动安装")
            // Cloud SDK 要求安装 Python；支持的版本是 Python 3（首选，3.5 到 3.8）和 Python 2（2.7.9 或更高版本）
            ctx.sh "python --version"
            // 下载 Linux 64的文件
            ctx.sh "curl -O https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-sdk-358.0.0-linux-x86_64.tar.gz"
            // 运行脚本
            ctx.sh "./google-cloud-sdk/install.sh"
            // 验证安装是否成功
            ctx.sh "./google-cloud-sdk/install.sh --help"
            // 初始化 SDK
            ctx.sh "./google-cloud-sdk/bin/gcloud init"
        }
    }

    /**
     * Fastlane插件方式 实现Android自动化测试
     * 插件地址: https://github.com/cats-oss/fastlane-plugin-firebase_test_lab_android
     */
    static def testLabForAndroid(ctx) {
        ctx.sh " fastlane firebase_test_lab_for_android "
    }

    /**
     * Fastlane插件方式 实现iOS自动化测试
     * 插件地址: https://github.com/fastlane/fastlane-plugin-firebase_test_lab
     */
    static def testLabForiOS(ctx) {
        ctx.sh " fastlane firebase_test_lab_for_ios "
    }

    /**
     *  Firebase Test Lab在不同真机系统设备中自动测试Android、iOS、游戏等应用并给出测试报告
     * 文档: https://firebase.google.com/docs/test-lab
     * CLI内集成: https://firebase.google.com/docs/test-lab/android/command-line
     */
    static def testLab(ctx) {
        // 确保您安装的版本是最新的SDK
        ctx.sh "gcloud components update"
        // 执行自动化测试
        ctx.sh " gcloud firebase test android run --app <local_server_path>/<app_apk>.apk " +
                " --test <local_server_path>/<app_test_apk>.apk"
    }

    /**
     *  Firebase Test Lab测试报告
     * 测试报告文档: https://firebase.google.com/docs/test-lab/android/analyzing-results
     */
    static def getTestReport(ctx) {
        // 获取测试报告结果
        // shell 命令添加一个 gsutil 命令以将测试结果数据复制到本地计算机或者在 Firebase 控制台查看
        ctx.sh " "
    }

}
