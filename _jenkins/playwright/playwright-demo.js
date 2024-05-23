import {test} from '@playwright/test';

/*
 @author 潘维吉
 @description  基于Playwright做小程序自动化提审
 Playwright是微软出品新一代前端自动化测试工具  支持大部分主流PC浏览器和移动端浏览器
 支持自动录制脚本、截图、录视频、自动等待、键盘鼠标、上传下载、网络请求拦截等  https://playwright.dev/
 初始化 npm i -g playwright  启动录制  playwright codegen https://lbs.amap.com/tools/picker
 执行 node playwright-demo.js
*/


test('test', async ({page}) => {
    await page.goto('https://lbs.amap.com/tools/picker');
    await page.getByPlaceholder('请输入关键字进行搜索').click();
    await page.getByPlaceholder('请输入关键字进行搜索').fill('日照阳光海岸5号');
    await page.getByRole('link', {name: '搜索', exact: true}).click();
    await page.locator('.amap_lib_placeSearch_poi').click();
    // 获取div元素内容
    const name = await page.locator('.amap-lib-infowindow-title').textContent();
    const address = await page.locator('.amap-lib-infowindow-content-wrap').textContent();
    console.log(name);
    console.log(address)

});