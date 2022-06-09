package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2022/1/9 13:22
 * @email 406798106@qq.com
 * @description App通用服务
 * 涵盖Android、iOS、Flutter、Unity、React Native等通用的方法
 */
class App implements Serializable {

    /**
     * 获取APP信息需要的依赖安装
     */
    static def getAppInfoPackageInit(ctx) {
        // 查看当前项目的依赖模块  查看全局依赖模块命令是 npm ls -g --depth 0
        def nodePackages = Utils.getShEchoResult(ctx, "npm ls --depth 0")
        if (!nodePackages.contains("yargs")) {
            ctx.sh "npm i -D yargs"
            ctx.sh "npm install app-info-parser"
        }
    }

}
