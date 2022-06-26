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
     * 多个机器批量执行命令
     */
    static def batchSync(ctx) {
        // ansible 主机组或者主机 -m 模块 -a 命令  参考文章: https://blog.51cto.com/395469372/2133486
        ctx.sh "ansible groupName -m command -a \"pwd\" "
    }

}
