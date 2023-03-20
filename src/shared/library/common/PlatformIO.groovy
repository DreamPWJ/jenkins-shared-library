package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2022/7/26 13:22
 * @email 406798106@qq.com
 * @description PlatformIO嵌入式开发和包管理平台
 */
class PlatformIO implements Serializable {

    /**
     * 嵌入式构建
     * 多平台和多架构构建系统
     */
    static def build(ctx) {
        ctx.sh "pio --version"
        def monorepoProjectDir = "" // 工程目录
        if ("${ctx.IS_MONO_REPO}" == 'true') {  // 是否MonoRepo单体式仓库  单仓多包
            monorepoProjectDir = "${ctx.MONO_REPO_MAIN_PACKAGE}/${ctx.PROJECT_NAME}/"
        }
        ctx.dir("${ctx.env.WORKSPACE}/${monorepoProjectDir}") {
            // ctx.sh " pio ci ${ctx.MONO_REPO_MAIN_PACKAGE}/${ctx.PROJECT_NAME} "
            // -e, --environment 指定环境变量 多环境文档 https://docs.platformio.org/en/stable/projectconf/section_env.html
            if ("${ctx.PLATFORMIO_ENV}" != "") {
                ctx.sh " platformio run -e ${ctx.PLATFORMIO_ENV} "
            } else {
                ctx.sh " platformio run  "  // -d ./${ctx.MONO_REPO_MAIN_PACKAGE}/${ctx.PROJECT_NAME}
            }

        }
        // 构建烧录固件位置: .pio/build/*/firmware.bin
        ctx.iotPackageLocation = Utils.getShEchoResult(ctx, "find " + monorepoProjectDir + ".pio/build/*/firmware.bin")
        ctx.iotPackageLocationPath = ctx.iotPackageLocation.replace("/firmware.bin", "")
        ctx.println(ctx.iotPackageLocation)
        ctx.iotPackageSize = Utils.getFileSize(ctx, ctx.iotPackageLocation)
        ctx.println("固件大小: " + ctx.iotPackageSize)
    }

    /**
     * 单元测试
     * 参考文档： https://docs.platformio.org/en/latest/core/userguide/cmd_test.html
     */
    static def unitTest(ctx) {
        ctx.sh "pio test"
    }

    /**
     * 代码质量规范检测
     */
    static def codeCheck(ctx) {
        // clang-tidy代码规范分析 LLVM编译器是 C/C++/ObjectiveC语言基础 运行 clang-tidy 时，它会执行静态代码分析以查找一些常见问题和代码样式违规，并提供可应用于您的代码的修复程序
        ctx.sh "pio check"
    }

    /**
     * Golioth嵌入式云端构建和OTA控制升級
     * 參考文档：https://github.com/goliothlabs/arduino-sdk
     */
    static def buildInCloud(ctx) {
        ctx.sh "pio ci"
    }

}
