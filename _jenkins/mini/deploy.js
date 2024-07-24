const ci = require('miniprogram-ci');
const path = require('path');
const fs = require("fs");
const yargs = require('yargs');
const argv = yargs.argv;
const appDirectory = fs.realpathSync(process.cwd());
const projectConfig = require('./project.config.json');

/*
 @author 潘维吉
 @description  小程序CI 自动构建预览上传等
 微信小程序miniprogram-ci文档地址: https://developers.weixin.qq.com/miniprogram/dev/devtools/ci.html
 支付宝小程序ci文档地址: https://opendocs.alipay.com/mini/miniu/api
*/

(async () => {
        try {
            // let { wxVersion: version, wxDesc: desc } = require('./package.json').wx
            const {type, v: version, desc, isNeedNpm, buildDir, wxCiResultFile, qrcodeName, robot} = argv;
            console.log(`type: ${type}`);
            console.log(`version: ${version}`);
            console.log(`desc: ${desc}`);
            console.log(`isNeedNpm: ${isNeedNpm}`);
            console.log(`buildDir: ${buildDir}`);
            console.log(`qrcodeName: ${qrcodeName}`);
            console.log(`robot: ${robot}`);
            console.log(`appDirectory: ${appDirectory}`);

            const project = new ci.Project({
                appid: projectConfig.appid,
                type: 'miniProgram', // miniProgram/miniProgramPlugin/miniGame/miniGamePlugin
                projectPath: path.resolve(appDirectory, `./${buildDir}`), // path.resolve(appDirectory, './dist'),
                privateKeyPath: process.cwd() + '/private.key', // 获取argv.p 执行node deploy.js -p
                // 忽略不需要打包的文件
                ignores: ['node_modules/**/*', '.git/*', 'ci/*', 'ci@tmp/*', 'deploy.js', 'private.key',
                    'yarn.lock', 'package-lock.json', '*.md', '*.cer', `${qrcodeName}.jpg`],
            });

            if (isNeedNpm === 'true') {  // 在有需要的时候构建npm
                console.log(`小程序内置npm构建`);
                const warning = await ci.packNpm(project, {
                    reporter: (infos) => {
                        console.log(infos)
                    }
                })
                console.warn(warning)
                // 可对warning进行格式化
                /*
                  warning.map((it, index) => {
                          return `${index + 1}. ${it.msg}
                  \t> code: ${it.code}
                  \t@ ${it.jsPath}:${it.startLine}-${it.endLine}`
                        }).join('---------------\n')
                */
                // 完成构建npm之后，可用ci.preview或者ci.upload
            }

            const defaults = {
                project,
                desc,
                robot,
                setting: {
                    es6: true, es7: true,
                    minify: true, codeProtect: false, autoPrefixWXSS: true
                },
                //setting: {...projectConfig.setting},
                //onProgressUpdate: console.log, // 进度更新监听函数  process.env.NODE_ENV === 'development' ? console.log : () => {}
            };

            const uploadConfig = Object.assign({}, defaults, {
                version,
                threads: 10, // 指定本地编译过程中开启的线程数
            });

            switch (type) {
                case 'develop':
                    // 生成预览图片二维码
                    const previewConfig = Object.assign({}, defaults, {
                        qrcodeFormat: 'image', // 返回二维码文件的格式 "image" 或 "base64"， 默认值 "terminal" 供调试用
                        qrcodeOutputDest: `${qrcodeName}.jpg`,
                        // pagePath: 'pages/index/index', // 预览页面
                        // searchQuery: 'a=1&b=2',  // 预览参数 [注意!]这里的`&`字符在命令行中应写成转义字符`\&`
                    });
                    // 预览
                    const previewResult = await ci.preview(previewConfig);
                    handleResult(wxCiResultFile, previewResult)
                    break;
                case 'trial':
                    // 上传
                    const uploadResult = await ci.upload(uploadConfig);
                    handleResult(wxCiResultFile, uploadResult)
                    break;
                case 'release':
                    // 发布
                    const releaseUploadResult = await ci.upload(uploadConfig);
                    handleResult(wxCiResultFile, releaseUploadResult)
                    break;
                default:
                    break;
            }
        } catch (e) {
            console.error('小程序预览上传发布失败 ❌')
            console.error(e);
            process.exit(1);
        }

    }
)();

/**
 * 响应结果自定义处理
 */
function handleResult(wxCiResultFile, result) {
    console.log("响应结果")
    console.log(result)
    let customResult = {}
    result.subPackageInfo.map(item => {
        if (item.name === "__FULL__") {
            Object.assign(customResult, {"totalPackageSize": item.size})
        }
        if (item.name === "__APP__") {
            Object.assign(customResult, {"mainPackageSize": item.size})
        }
    })
    fs.writeFile(wxCiResultFile, JSON.stringify(customResult));
}
