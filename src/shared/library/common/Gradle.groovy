package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2021/12/17 13:22
 * @email 406798106@qq.com
 * @description Gradle相关
 */
class Gradle implements Serializable {

    /**
     *  初始化环境变量
     */
    static def initEnv(ctx) {
        try {
            // brew install gradle
            // 动态获取环境变量使用  which gradle 命令响应结果
            def path = Utils.getShEchoResult(ctx, "which gradle")
            if (path?.trim()) {
                path = "/opt/homebrew/bin/gradle"
            }
            ctx.env.PATH = "${ctx.env.PATH}:${path}" //添加了系统环境变量上
        } catch (e) {
            ctx.println("初始化Gradle环境变量失败")
            ctx.println(e.getMessage())
        }
    }

    /**
     *  初始化Gradle项目
     */
    static def initProject(ctx) {
        // 执行gradle wrapper命令 允许在没有安装gradle的情况下运行Gradle任务 解决gradlew is not found (No such file or directory)
        // ctx.sh "brew install gradle && gradle -v"
        // 自动失败可手动执行gradle wrapper命令
        ctx.sh "gradle wrapper && chmod +x ./gradlew && ./gradlew -v"
    }

    /**
     * 构建
     */
    static def build(ctx, tasks = "clean build") {
        ctx.sh "./gradlew ${tasks}" // Linux和MacOS使用./gradlew  Windows系统 直接gradlew
    }


}
