package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2021/12/24 13:22
 * @email 406798106@qq.com
 * @description Ansible自动化运维工具
 */
class Ansible implements Serializable {

    /**
     * Ansible安装
     */
    static def install(ctx) {
        ctx.sh "yum install -y ansible || true"
        ctx.sh "apt-get install -y ansible || true"
    }

    /**
     * 批量同步配置
     */
    static def batchSync(ctx) {
        ctx.sh ""
    }

}
