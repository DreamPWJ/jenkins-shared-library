package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/10/25 13:22
 * @email 406798106@qq.com
 * @description 服务健康状态相关
 */
class Health implements Serializable {

    /**
     * 健康探测
     */
    static def check(ctx) {
        ctx.retry(20) { // 闭包内脚本重复执行次数
            ctx.script {
                ctx.sh script: 'curl http://panweiji.com', returnStatus: true
                ctx.sleep(time: 5, unit: "SECONDS") // 暂停pipeline一段时间，单位为秒
            }
        }
    }

}
