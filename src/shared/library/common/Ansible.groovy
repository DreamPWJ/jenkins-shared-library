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
     * Ansible或SSH 通过堡垒机/跳板机 访问目标机器 利用ssh的ProxyCommand或ProxyJump功能来透过跳板机
     */
    static def accessTargetMachine(ctx) {
        // Ansible机器的 ~/.ssh/config 配置如下即可：
        /*
        Host machine-name
            HostName  # 目标机器IP 真正登陆的服务器 不支持域名必须IP地址
            Port 22
            User root
            ProxyCommand ssh root@跳板机IP -W %h:%p */

        ctx.sh "ssh root@目标机器内网IP"

        // 直接访问目标机器
        // ssh root@目标机器内网IP
        // ansible -i host -m setup 目标机器内网IP
        // ssh username@目标机器IP -p 22 -o ProxyCommand='ssh -p 22 username@跳板机IP -W %h:%p'
    }

    /**
     * 批量同步配置
     */
    static def batchSync(ctx) {
        ctx.sh ""
    }

}
