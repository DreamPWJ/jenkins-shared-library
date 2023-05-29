package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2023/5/29 13:22
 * @email 406798106@qq.com
 * @description GitHub通用服务
 */
class GitHub implements Serializable {

    /**
     * 初始化网络
     */
    static def init(ctx) {
        def isHasGithub = Utils.getShEchoResult(ctx, "cat /etc/hosts")
        if (!isHasGithub.contains("github.com")) {
            // 解决网络慢的问题 DNS直接解析hosts配置的地址
            ctx.sh "echo '140.82.112.4 github.com' >> /etc/hosts"
        }
    }

}
