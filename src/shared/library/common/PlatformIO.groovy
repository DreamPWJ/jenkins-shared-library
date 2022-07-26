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
        if ("${ctx.IS_MONO_REPO}" == 'true') {  // 是否MonoRepo单体式式仓库
            // ctx.sh " pio ci ${ctx.MONO_REPO_MAIN_PACKAGE}/${ctx.PROJECT_NAME} "
           ctx.sh " platformio run -d ./${ctx.MONO_REPO_MAIN_PACKAGE}/${ctx.PROJECT_NAME} "  // 构建烧录固件位置: .pio/build/*/firmware.bin
        } else {
            ctx.sh " platformio run "
        }
    }

    /**
     * Golioth嵌入式云端构建和OTA升級
     * 參考文檔：https://github.com/goliothlabs/arduino-sdk
     */
    static def buildInCloud(ctx) {
        ctx.sh "pio ci"
    }

}
