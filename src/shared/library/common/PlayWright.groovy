package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/12/16 13:22
 * @email 406798106@qq.com
 * @description Playwright是微软出品新一代前端自动化测试工具  支持大部分主流PC浏览器和移动端浏览器
 * 支持自动录制脚本、截图、录视频、自动等待、键盘鼠标、上传下载、网络请求拦截等  https://playwright.dev/
 */
class PlayWright implements Serializable {

    /**
     * 初始化
     */
    static def init(ctx) {
        try {
            // 使用Docker方式安装 https://playwright.dev/docs/docker
            // 判断服务器是是否安装playwright环境
            ctx.sh "playwright --version"
        } catch (error) {
            // 固定版本号防止新版有兼容性问题和需要下载新的Chromium版本等问题
            def playwrightVersion = "1.20.0"
            ctx.sh "npm i -D playwright@${playwrightVersion}"
            ctx.sh "npm i -D yargs"
            // ctx.sh "npm i -D @playwright/test"
            ctx.sh "pip3 install playwright==${playwrightVersion}" // 安装浏览器驱动文件（文件较大下载有点慢）
        }
    }

    /**
     * 小程序平台
     */
    static def miniPlatform(ctx) {
        this.init(ctx)
        def fileName = "mini-playwright.js"
        ctx.sh "cp -r ${ctx.miniConfigDir}/${fileName} ./"
        // 提审参数 如版本描述、账号密码等动态传入并自动填写到页面上填充值
        ctx.sh "node --unhandled-rejections=strict ${fileName} --versionDesc='${ctx.params.VERSION_DESC}' ${ctx.miniReviewInfo} "
        ctx.sh "rm -f ${fileName}"
    }


}
