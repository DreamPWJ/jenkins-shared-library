// Playwright是微软出品新一代前端自动化测试工具  支持大部分主流PC浏览器和移动端浏览器
// 支持自动录制脚本、截图、录视频等  https://playwright.dev/
// 可基于 playwright codegen 命令自动录制脚本 无需编写脚本  浏览器操作测试行为会自动保存到脚本中

// 安装playwright库
// npm i -D playwright
// npm i -D @playwright/test
// 安装浏览器驱动文件（文件较大有点慢）
// pip3 install playwright

// 自动化录制脚本
// -target 生成语言，有python/javascript/python-async/csharp可选，缺省值为python
// -o 保存路径，也可以写成--output  -h 查看帮助，也可写成--help
// -b 指定浏览器，浏览器选项如下(缺省默认为chromium) firefox、webkit
// playwright codegen --target javascript -o 'playwright-codegen.js' -b chromium https://mp.weixin.qq.com/

// 截屏   await page.screenshot({ path: 'screenshot.png' });
