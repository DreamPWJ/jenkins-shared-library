const {chromium} = require('playwright');

/*
 @author 潘维吉
 @description  基于Playwright做小程序自动化提审
 Playwright是微软出品新一代前端自动化测试工具  支持大部分主流PC浏览器和移动端浏览器
 支持自动录制脚本、截图、录视频、自动等待、键盘鼠标、上传下载、网络请求拦截等  https://playwright.dev/
 在文件目录下 初始化 npm install playwright  &&  npx playwright install chromium  启动录制  playwright codegen https://www.baidu.com
 执行 node playwright-demo.js
*/

(async () => {
    const browser = await chromium.launch({headless: false});
    const context = await browser.newContext();
    const page = await context.newPage();

    // 访问一个网页
    await page.goto('https://www.baidu.com/');

    await page.locator('#kw').click();
    await page.locator('#kw').fill('playwright');
    await page.getByRole('button', {name: '百度一下'}).click();
    const page1Promise = page.waitForEvent('popup');
    await page.getByRole('link', {name: 'microsoft/playwright - GitHub'}).click();

    await page1Promise;
    // 关闭页面和浏览器
    //await page.close();
    //await browser.close();
})();