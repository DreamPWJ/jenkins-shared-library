package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2022/5/9 08:22
 * @email 406798106@qq.com
 * @description SSH安全访问相关
 */
class SecureShell implements Serializable {

    /**
     * SSH自动免密登录 1. 公私钥方式  2. 密码方式
     */
    static def login(ctx) {
        // ctx.sh "ssh -V"
    }

    /**
     * 自动设置免密连接 用于CI/CD服务器和应用部署服务器免密通信  避免手动批量设置繁琐重复劳动
     */
    static def autoSshLogin(ctx) {
        try {
            if ("${ctx.remote.user}".trim() == "" || "${ctx.remote.host}".trim() == "") {
                ctx.currentBuild.result = 'FAILURE'
                ctx.error("请配置部署服务器登录用户名或IP地址 ❌")
            }
            // 检测ssh免密连接是否成功
            ctx.sh "ssh ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ctx.remote.host} exit"
        } catch (error) {
            ctx.println error.getMessage()
            if (error.getMessage().contains("255")) { // 0连接成功 255无法连接
                ctx.println "免密登录失败, 根据hosts.txt文件已有的账号信息自动设置, 如果没有配置hosts.txt请手动设置ssh免密登录"
                ctx.println "如果有跳板机情况, 可手动将构建机器的公钥分别添加到外网跳板机和内网目标机authorized_keys内实现免密登录"
                try {
                    // 目的是清除当前机器里关于远程服务器的缓存和公钥信息 如远程服务器已重新初始化情况 导致本地还有缓存
                    // ECDSA host key "ip" for  has changed and you have requested strict checking 报错
                    ctx.sh "ssh-keygen -R ${ctx.remote.host}"
                } catch (e) {
                    ctx.println "清除当前机器里关于远程服务器的缓存和公钥信息失败"
                    ctx.println e.getMessage()
                }
                ctx.dir("${ctx.env.WORKSPACE}/ci") {
                    try {
                        // 执行免密登录脚本
                        ctx.sh " cd _linux && chmod +x auto-ssh.sh && ./auto-ssh.sh "
                    } catch (e) {
                        ctx.println e.getMessage()
                    }
                }
            }
        }
    }

}
