const fs = require("fs");
const yargs = require('yargs');
const argv = yargs.argv;

// 安装命令 npm install app-info-parser 或者 yarn add app-info-parser
// 依赖安装 npm i -D yargs
const AppInfoParser = require('app-info-parser')

/**
 * @author 潘维吉
 * @description 获取APP包信息
 * 文档地址: https://github.com/chenquincy/app-info-parser
 */

const {appFilePath, outInfoFile} = argv;

/*
const appFilePath = "test.apk"
const outInfoFile = "test.txt"
*/

const parser = new AppInfoParser(appFilePath)

parser.parse().then(result => {
    //console.log(result)
    //console.log('icon base64 ----> ', result.icon)

    let appInfo = ""
    // Android
    if (appFilePath.endsWith(".apk") || appFilePath.endsWith(".aab")) {
        let size = getFileBytes(appFilePath)
        let appName = ""
        if (result.application.label instanceof Array) {
            appName = result.application.label[0]
        } else {
            appName = result.application.label
        }
        /*      console.log(appName)
                console.log(result.versionName)
                console.log(size)
                console.log(result.versionCode)
                console.log(result.package)*/

        appInfo = `${appName},${result.versionName},${size},${result.versionCode},${result.package},Android`
    }

    // iOS
    if (appFilePath.endsWith(".ipa")) {
        let size = getFileBytes(appFilePath)
        /*      console.log(result.CFBundleDisplayName)
                console.log(result.CFBundleShortVersionString)
                console.log(size)
                console.log(result.CFBundleVersion)
                console.log(result.CFBundleIdentifier)
                console.log(result.mobileProvision.Platform)*/

        appInfo = `${result.CFBundleDisplayName},${result.CFBundleShortVersionString},${size},${result.CFBundleVersion},${result.CFBundleIdentifier},${result.mobileProvision.Platform}`
    }
    fs.writeFileSync(outInfoFile, appInfo);

}).catch(err => {
    console.error('获取APP包信息错误 ----> ', err)
})

/**
 * 获取文件大小字节
 */
function getFileBytes(appFilePath) {
    let statsObj;
    statsObj = fs.statSync(appFilePath, function (error, stats) {
        if (error) {
            console.error("获取文件大小错误");
        } else {
            // 文件大小字节 stats.size
        }
    })
    return formatSize(statsObj.size)
}

/**
 * 格式化大小单位
 */
function formatSize(a, b) {
    if (0 == a) return "0 Bytes";
    var c = 1024, d = b || 2, e = ["Bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"],
        f = Math.floor(Math.log(a) / Math.log(c));
    return parseFloat((a / Math.pow(c, f)).toFixed(d)) + " " + e[f]
}
