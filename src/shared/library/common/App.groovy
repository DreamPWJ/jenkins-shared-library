package shared.library.common

/**
 * @author 潘维吉
 * @date 2022/1/9 13:22
 * @email 406798106@qq.com
 * @description App通用服务
 * 包含Android、iOS、Flutter、Unity、React Native等通用的方法
 */
class App implements Serializable {

    /**
     * 获取APP信息
     */
    static def getAppInfo(ctx) {
        try {
            ctx.sh "yargs"
        } catch (error) {
            ctx.sh "npm i -D yargs"
        }
    }

}
