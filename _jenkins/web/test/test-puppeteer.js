const puppeteer = require('puppeteer');
// const devices = require('puppeteer/DeviceDescriptors')
// npm i puppeteer

(async () => {
    const browser = await puppeteer.launch({
        //executablePath: './chrome-mac/Chromium.app/Contents/MacOS/Chromium',  // 运行 Chromium 或 Chrome 可执行文件的路径（相对路径）
        headless: false, defaultViewport: null, args: ['--start-maximized']
    });
    const page = await browser.newPage(); // 新建一个页面
    //await page.emulate(devices['iPhone X'])
    await page.goto('https://www.baidu.com/'); // 访问网页
    await page.type('#kw', 'Puppeteer', {delay: 100}); // 在输入框中自动输入
    await page.click('#su'); // 点击按钮的id
    const watchDog = page.waitForFunction('2 > 1', {timeout: 30000});
    // try-catch-finally代码块
    setTimeout(res => {
        // 截屏
        page.screenshot({path: 'screenshot.png'});
    }, 3000)
    try {
        await watchDog;
    } catch (error) {
        console.error("错误信息: " + error);
    } finally {
        // browser.close(); //关闭浏览器。在运行代码的时候可注释掉该语句，看效果
    }

})();
