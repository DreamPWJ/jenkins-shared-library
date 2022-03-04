const puppeteer = require('puppeteer');
// yarn add puppeteer

(async () => {
    const browser = await puppeteer.launch({
            headless: false, // 关闭无头模式，方便看到无头浏览器执行的过程
            defaultViewport: null, // 设置浏览器视窗最大化
            args: ['--start-maximized'],
            timeout: 30000, // 默认超时为30秒，设置为0则表示不设置超时
        }
    );

    const page = await browser.newPage();     // 打开空白页面
    await page.goto('https://mp.weixin.qq.com/');
    // 登录
    /* await page.type('#username', "用户名");
     await page.type('#password', "密码");
     await page.click('#btn_login');
    // 页面登录成功后，需要保证redirect 跳转到请求的页面
    await page.waitForNavigation(); */

    // 截屏
    await page.screenshot({path: 'screenshot.png'});

    // 关闭浏览器
    await browser.close();
})();
