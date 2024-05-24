const {chromium} = require('playwright');
const yargs = require('yargs');
const argv = yargs.argv;

/*
 @author 潘维吉
 @description  基于Playwright做小程序自动化提审
 Playwright是微软出品新一代前端自动化测试工具  支持大部分主流PC浏览器和移动端浏览器
 支持自动录制脚本、截图、录视频、自动等待、键盘鼠标、上传下载、网络请求拦截等  https://playwright.dev/
 初始化 npm i -g playwright  启动录制  playwright codegen https://playwright.dev/
 执行 node mini-playwright.js
*/

(async () => {
    const browser = await chromium.launch({
        headless: false
    });
    const context = await browser.newContext();

    // Open new page
    const page = await context.newPage();

    // 跳转页面 waitUntil确保页面加载完成后再执行下一步
    await page.goto('https://mp.weixin.qq.com/', {
        waitUntil: 'networkidle'
    });

    // 位置截图
    /*    await page.screenshot({
            path: 'mini-playwright-screenshot.png',
            clip: {x: 855, y: 155, width: 150, height: 150},
            fullPage: false
        });*/
    // 元素截图
    await page.locator('.login_frame').screenshot({path: 'mini-playwright-screenshot.png'});

    // Click text=版本管理  自动等待 timeout等待超时时间 默认30s  0是禁止超时 单位毫秒
    // await Promise.all([
    await page.click('text=版本管理', {timeout: 0})
    // ]);

    // Click button:has-text("提交审核")
    await page.click('button:has-text("提交审核")');

    // Click text=提交给微信团队审核前，请确保: 提交的小程序功能完整，可正常打开和运行，而不是测试版或 Demo 小程序的调试和预览可在开发者工具进行。 多次提交测试内容或 D >> i
    await page.click('text=提交给微信团队审核前，请确保: 提交的小程序功能完整，可正常打开和运行，而不是测试版或 Demo 小程序的调试和预览可在开发者工具进行。 多次提交测试内容或 D >> i');

    // Click text=下一步
    await page.click('text=下一步');

    // Click text=继续提交
    const [page1] = await Promise.all([
        page.waitForEvent('popup'),
        page.click('text=继续提交')
    ]);

    try {
        // 动态传入参数
        const {versionDesc, demoUser, demoPassword} = argv;
        // 小程序审核需要填写的数据
        await page1.fill("textarea[placeholder=\"请列点简要描述本次更新的功能点\"]", versionDesc)
        await page1.fill("input[placeholder=\"请提供审核测试使用的测试账号\"]", demoUser)
        await page1.fill("input[placeholder=\"请填写测试账号的密码\"]", demoPassword)
        console.log("小程序提交审核需要填写的数据");
        console.log(versionDesc);
        console.log(demoUser);
        console.log(demoPassword);
    } catch (e) {
        console.error("小程序审核需要填写的数据设置失败 ❌");
        console.error(e);
    }

    // await page1.waitForTimeout(5000) // 等待多少秒

    // Click a:has-text("提交审核")
    await page1.click('a:has-text("提交审核")');

    // Close page
    await page1.close();

    // Close page
    await page.close();

    // ---------------------
    await context.close();
    await browser.close();
})();

