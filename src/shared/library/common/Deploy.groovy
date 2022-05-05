package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2021/1/26 13:22
 * @email 406798106@qq.com
 * @description 部署相关
 */
class Deploy implements Serializable {

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
     * 不同分布式部署节点不同环境文件自动替换
     */
    static def replaceEnvFile(ctx, sourceFilePath, targetFilePath) {
        // 源文件和多个目标文件可放在代码里面维护 部署时候根据配置自动替换
        ctx.sh "cp -p ${targetFilePath} ${sourceFilePath}"
    }

    /**
     * 执行
     */
    static def execute(ctx) {
        ctx.sh ""
    }

}
