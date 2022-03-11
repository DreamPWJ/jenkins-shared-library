const jwt = require("jsonwebtoken");
const axios = require('axios');
const fs = require("fs");
const exec = require('child_process').exec;
const yargs = require('yargs');
const argv = yargs.argv;

/**
 * @author 潘维吉
 * @description 集成App Store Connect API实现自动化操作流程  自定义Apple Store审核状态变更自动通知
 * 终端执行命令如下
 node app-store-connect-api.js --appIdentifier='com.panweiji.propertyservice.store' --appVersion='1.0.0' --apiKeyId='RK28R5AN27' --privateKey="/Library/AuthKey_RK28R5AN27.p8" --issuerId='69a6de8d-bf99-47e3-e053-5b8c7c11a4d1' --phone='18863302302'
 * 官方文档: https://developer.apple.com/documentation/appstoreconnectapi
 * jwt生成: https://www.npmjs.com/package/jsonwebtoken
 */

// 安装 npm install -g axios && npm install -g jsonwebtoken && npm i -D yargs

let systemHome = process.env.HOME || "/Users/panweiji"   //  Linux与MacOS系统主目录 Windows系统为 process.env.USERPROFILE

let appId = ""  // 应用Id
let appName = ""  // 应用名称
let appStoreState = "" // App Store应用状态

//let appIdentifier = "com.panweiji.propertyservice.store"  // 应用唯一Id
//let appVersion = "1.0.0"  // 应用版本
//let phone = "18863302302" // 发布人手机号 用于审核状态变更@通知

let {appIdentifier, appVersion, apiKeyId, privateKey, issuerId, phone} = argv;
console.log(` node app-store-connect-api.js --appIdentifier=${appIdentifier} --appVersion=${appVersion} --apiKeyId=${apiKeyId} --privateKey=${privateKey} --issuerId=${issuerId} --phone=${phone}`);

const APP_STORE_CONNECT_API_KEY_ID = apiKeyId || "BNL32GR27G"
const APP_STORE_CONNECT_PRIVATE_KEY = systemHome + privateKey || systemHome + "/Library/AuthKey_BNL32GR27G.p8"
const APP_STORE_CONNECT_ISSUER_ID = issuerId || "a0cae47e-ca4b-4503-a967-4282302c0c5a"

const NOW = Math.round((new Date()).getTime() / 1000);

const PAYLOAD = {
    'iss': APP_STORE_CONNECT_ISSUER_ID,
    'exp': NOW + 1200,
    'aud': 'appstoreconnect-v1'
};

const SIGN_OPTS = {
    'algorithm': 'ES256',
    'header': {
        'alg': 'ES256',
        'kid': APP_STORE_CONNECT_API_KEY_ID,
        'typ': 'JWT'
    }
};

// 读取私钥文件数据
const privateKeyFileData = fs.readFileSync(APP_STORE_CONNECT_PRIVATE_KEY);

// 生成App Store Connect API访问token
const bearerToken = jwt.sign(
    PAYLOAD,
    privateKeyFileData,
    SIGN_OPTS
);

// token有效期20分钟
// console.log(bearerToken);

// 多次重试 因为有时苹果会拒绝正确的请求
// for (let i = 0; i < 5; i++) {

/**
 * 请求App Store Connect API获取审核状态等信息
 */
axios.get(`https://api.appstoreconnect.apple.com/v1/apps?filter[bundleId]=${appIdentifier}`, {
        headers: {
            "Content-Type": "application/json",
            'Authorization': 'Bearer ' + bearerToken,
        }
    }
).then(res => {
    // console.log("/apps接口响应结果: " + JSON.stringify(res.data));
    let appsData = res.data.data.reverse() // 反转数组中元素的顺序
    appId = appsData[0].id
    appName = appsData[0].attributes.name
    // 参考文档: https://developer.apple.com/documentation/appstoreconnectapi/list_all_app_infos_for_an_app/
    axios.get(`https://api.appstoreconnect.apple.com/v1/apps/${appId}/appInfos`, {
            headers: {
                "Content-Type": "application/json",
                'Authorization': 'Bearer ' + bearerToken,
            }
        }
    ).then(res => {
        //console.log("/appInfos接口响应结果: " + JSON.stringify(res.data));
        // 上架后存在两个结果顺序是 1. 线上的状态 2. 审核预发布的状态 根据需要是否倒序
        let appInfosData = res.data.data.reverse() // 反转数组中元素的顺序
        appStoreState = appInfosData[0].attributes.appStoreState
        console.log("当前时间: " + (new Date(+new Date() + 8 * 3600 * 1000)).toISOString().replace(/T/, ' ').replace(/\..+/, ''));
        console.log("应用标识id: " + appId);
        console.log("应用名称: " + appName);
        console.log("应用版本: " + appVersion);
        // App Store审核状态列表如下
        // READY_FOR_SALE、PENDING_APPLE_RELEASE、PENDING_DEVELOPER_RELEASE、WAITING_FOR_REVIEW、IN_REVIEW、PREPARE_FOR_SUBMISSION、DEVELOPER_REMOVED_FROM_SALE
        // DEVELOPER_REJECTED、REJECTED、METADATA_REJECTED、INVALID_BINARY、UNRESOLVED_ISSUES、REMOVED_FROM_SALE
        console.log("App Store审核状态: " + appStoreState);
        // 以下几种状态才进行通知
        if (appStoreState === "IN_REVIEW" || appStoreState === "PENDING_DEVELOPER_RELEASE" || appStoreState === "READY_FOR_SALE"
            || appStoreState.includes("REJECTED") || appStoreState.includes("INVALID")) {
            sendDingNotice();
        }
        // 上架成功后处理
        if (appStoreState === "READY_FOR_SALE") {
            console.log("App Store应用主页: https://apps.apple.com/cn/app/apple-store/id" + appId);
            let deleteFileName = systemHome + "/AppStore/app-store.sh"
            if (fs.existsSync(deleteFileName)) {
                console.log("App Store上架成功后删除具体的执行任务");
                // 同时支持多个并行Apple Store审核状态通知  清除定时任务的具体Job
                // shell 获得字符串所在行数及位置
                let num;
                exec(`cat ${deleteFileName} | grep -n ${appIdentifier} | awk -F \":\" '{print $1}'`, function (error, stdout, stderr) {
                    if (error) {
                        console.log('exec error: ' + error);
                        return;
                    }
                    num = stdout.replace(/[\r\n]/g, "") // 去掉换行符
                    console.log(`获得字符串所在文件的位置为: ${num}`);
                    if (num != "") {
                        // 删除第几行
                        exec(`sed -ig '${num}d' ${deleteFileName}`, function (error, stdout, stderr) {
                            if (error) {
                                console.log('删除第几行失败: ' + error);
                                return;
                            }
                            console.log('删除具体的定时任务成功');
                        });
                    }
                });
            }
        }
    }).catch(error => {
        console.error("appInfos接口错误信息: " + error)
    })
}).catch(error => {
    console.error("apps接口错误信息: " + error)
})

/**
 * 检测通知唯一次数 是否已经通知过
 */
function isAlreadyNotice() {
    try {
        // 唯一标识组合
        let uniqueId = appId + "-" + appVersion + "-" + appStoreState
        // 存储文件
        let dataFileName = systemHome + "/AppStore/is_notice.txt"
        // 文件内容
        let content = ""

        // 读取文件内容
        content = fs.readFileSync(dataFileName, 'utf8')

        // 增量写入文件
        if (fs.existsSync(dataFileName)) {
            if (content.includes(uniqueId) === false) {
                let writeData = content + "," + uniqueId
                try {
                    fs.writeFileSync(dataFileName, writeData)
                    //文件写入成功。
                    console.log(`文件写入 ${writeData} 成功`)
                } catch (err) {
                    console.error(err)
                }
            }
        } else {
            console.error(`写入文件 ${dataFileName} 不存在 !`)
        }

        return content.includes(uniqueId) ? false : true;
    } catch (error) {
        console.error("数据存储错误信息: " + error)
    }
}

/**
 * 审核通过和被拒绝 钉钉群通知
 */
function sendDingNotice() {
    if (isAlreadyNotice()) {
        // 钉钉文档地址: https://open.dingtalk.com/document/robots/custom-robot-access
        try {
            console.log("钉钉群通知");
            axios.post('https://oapi.dingtalk.com/robot/send?access_token=ac8c99031afc2d0973bfd4c15b7f80e66a1b4dc11a5371236723b35476276b3a', {
                msgtype: "actionCard",
                actionCard: {
                    "title": `${appName} v${appVersion} App Store审核状态变更通知 乐享科技`,
                    "text": `![screenshot](@lADOpwk3K80C0M0FoA) \n\n ### ${appName} iOS v${appVersion}版本 
                    \n\n ### 审核上架状态为: ${appStoreState} ${(appStoreState.includes("REJECTED") || appStoreState.includes("INVALID")) ? '❌' : '✅'}
                    \n\n ###### 通知时间: ${(new Date(+new Date() + 8 * 3600 * 1000)).toISOString().replace(/T/, ' ').replace(/\..+/, '')}
                    \n\n 请及时处理 !  🏃 @${phone}`,
                    "btnOrientation": "0", // 0：按钮竖直排列  1：按钮横向排列
                    "btns": [
                        {
                            "title": "App Store Connect",
                            "actionURL": "https://appstoreconnect.apple.com/apps/"
                        },
                        {
                            "title": "App Store应用主页",
                            "actionURL": "https://apps.apple.com/cn/app/apple-store/id" + appId
                        }
                    ],
                },
                "at": {
                    atMobiles: [phone],
                    isAtAll: false
                }

            });
        } catch (error) {
            console.error(error)
        }
    }
}

// }


