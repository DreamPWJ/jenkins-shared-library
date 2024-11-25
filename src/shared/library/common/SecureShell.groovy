package shared.library.common

import groovy.json.JsonOutput
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
     * 自动设置SSH免密连接 用于CI/CD服务器和应用部署服务器免密通信  避免手动批量设置繁琐重复劳动
     */
    static def autoSshLogin(ctx, map) {
        ctx.println "自动设置SSH免密连接 用于CI/CD服务器和应用部署服务器免密通信 注意当前跳板机或部署机器先执行 ssh-keygen -t rsa"
        try {
            if ("${ctx.remote.user}".trim() == "" || "${ctx.remote.host}".trim() == "") {
                ctx.currentBuild.result = 'FAILURE'
                ctx.error("请配置部署服务器登录用户名或IP地址 ❌")
            }

            // 检测ssh免密连接是否成功 ssh/scp跳过首次连接远程主机的指纹fingerprint(防止中间人攻击)设置-o StrictHostKeyChecking=no
            ctx.sh "ssh ${ctx.proxyJumpSSHText} -o StrictHostKeyChecking=no ${ctx.remote.user}@${ctx.remote.host} exit"
        } catch (error) {
            ctx.println error.getMessage()
            if (error.getMessage().contains("255")) { // 0连接成功 255无法连接
                ctx.println "免密登录失败 ❌, 根据hosts.txt或proxy_jump_hosts.json文件已有的账号信息自动设置, 如果没有配置请手动设置ssh免密登录文件"
                try {
                    // 目的是清除当前机器里关于远程服务器的缓存和公钥信息 如远程服务器已重新初始化或升降配或SSH客户端软件或发生了中间人攻击等情况 导致本地还有缓存
                    // ECDSA host key "ip" for  has changed and you have requested strict checking 报错
                    // 出现kex_exchange_identification: Connection closed by remote host内容，主要是由于远程计算机登录节点的数量限制问题
                    if ("${ctx.isProxyJumpType}" == "true") {
                        ctx.sh "ssh-keygen -R ${map.proxy_jump_ip}"
                        // 刷新known_hosts中对应远程服务器公钥
                        ctx.sh "ssh-keyscan -H ${map.proxy_jump_ip} >> ~/.ssh/known_hosts"
                    } else {
                        ctx.sh "ssh-keygen -R ${ctx.remote.host}"
                        ctx.sh "ssh-keyscan -H ${ctx.remote.host} >> ~/.ssh/known_hosts"
                    }
                    // ctx.sh "rm -f ~/.ssh/known_hosts" // 删除known_hosts中对应远程服务器公钥 重新初始化
                } catch (e) {
                    ctx.println "清除当前机器里关于远程服务器的缓存和公钥信息失败"
                    ctx.println e.getMessage()
                }
                ctx.dir("${ctx.env.WORKSPACE}/ci") {
                    try {
                        // 安全性高和定制化的数据建议保存为Jenkins的“Secret file”类型的凭据并获取 无需放在代码中
                        if ("${map.ssh_hosts_id}".trim() != "") {
                            ctx.withCredentials([ctx.file(credentialsId: "${map.ssh_hosts_id}", variable: 'SSH_HOSTS')]) {
                                def textData = ctx.readFile(file: "${ctx.SSH_HOSTS}")
                                def filePath = "_linux/hosts.txt"
                                // 使用 Groovy 代码写入文件
                                ctx.writeFile file: filePath, text: textData
                            }
                        }

                        if ("${ctx.isProxyJumpType}" == "true") {  // 跳板机方式部署
                            // 执行升级检测  在较新版本的OpenSSH 7.3及以上中( ssh -V 查看版本)，跳板机（jump host）-J 选项是存在的
                            ctx.sh " cd _linux/shell/ && chmod +x upgrade-ssh.sh && ./upgrade-ssh.sh "

                            ctx.withCredentials([ctx.file(credentialsId: "${map.proxy_jump_hosts_id}", variable: 'PROXY_JUMP_HOSTS')]) {
                                def jsonData = ctx.readFile(file: "${ctx.PROXY_JUMP_HOSTS}")
                                def json = ctx.readJSON text: "${jsonData}"
                                def filePath = "_linux/proxy_jump_hosts.json" // 跳板机地址账号密码登配置文件
                                // 使用 Groovy 代码写入文件
                                // 将数组转换为JSON格式的字符串
                                def jsonText = JsonOutput.toJson(json)
                                ctx.writeFile file: filePath, text: jsonText
                            }
                        }

                        if ("${ctx.isProxyJumpType}" == "true") {
                            // 执行跳板机方式免密登录脚本
                            ctx.sh " cd _linux && chmod +x auto-proxy-ssh.sh && ./auto-proxy-ssh.sh "
                        } else {
                            // 执行免密登录脚本
                            ctx.sh " cd _linux && chmod +x auto-ssh.sh && ./auto-ssh.sh "
                        }
                    } catch (e) {
                        ctx.println e.getMessage()
                    }
                }
            }
        }
    }

}
