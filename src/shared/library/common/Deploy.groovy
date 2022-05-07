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
     * SSH 通过堡垒机/跳板机 访问目标机器 利用ssh的高级的ProxyJump最方便或中级的ProxyCommand或者ssh tunnel功能来透过跳板机
     */
    static def sshProxy(ctx) {
        // SSH客户端执行访问的机器通过跳板机直接访问目标机器
        // OpenSSH 7.3版本查看版本ssh -V 开始使用更方便ssh ProxyJump 文档: https://woodenrobot.me/2019/07/18/ssh-proxyjump/
        // ssh -J root@外网跳板机IP:22 root@内网目标机器IP -p 22
        ctx.sh "ssh -J root@${ctx.proxyJumphost} ${ctx.remote.user}@${ctx.remote.host}"
        // ssh -J root@119.188.90.222 root@172.16.0.91
        // scp -o 'ProxyJump root@跳板机IP' file.txt root@目标机器IP:/my/
        // Tabby跨越堡垒机的SSH利器 文档: https://zhuanlan.zhihu.com/p/490662490
    }

    /**
     * 不同分布式部署节点不同环境文件自动替换
     * 自定义的部署配置文件替代默认配置文件等
     */
    static def replaceEnvFile(ctx, sourceFilePath, targetFilePath) {
        // 源文件和多个目标文件可放在代码里面维护 部署时候根据配置自动替换到目标服务器
        // 或者项目源码仓库内的配置文件替换CI仓库的默认文件等
        ctx.sh "cp -p ${targetFilePath} ${sourceFilePath}"
    }

    /**
     * 执行
     */
    static def execute(ctx) {
        ctx.sh ""
    }

}
