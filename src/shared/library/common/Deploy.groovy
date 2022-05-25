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
        // scp -o 'ProxyJump root@跳板机IP:22' file.txt root@目标机器IP:/my/
        // Tabby跨越堡垒机的SSH利器 文档: https://zhuanlan.zhihu.com/p/490662490
    }

    /**
     * 自动替换不同分布式部署节点的环境文件
     * 自定义的部署配置文件替代默认配置文件等
     */
    static def replaceEnvFile(ctx) {
        // 源文件和多个目标文件可放在代码里面维护 部署时候根据配置自动替换到目标服务器
        // 或者项目源码仓库内的配置文件替换CI仓库的默认文件等
        if ("${ctx.SOURCE_TARGET_CONFIG_DIR}".trim() != "") {
            def sourceFilePath = "${ctx.SOURCE_TARGET_CONFIG_DIR}".split(",")[0] // 源文件目录 真正的配置文件
            def targetFilePath = "${ctx.SOURCE_TARGET_CONFIG_DIR}".split(",")[1]  // 目标文件目录 要替换的配置文件
            ctx.println("自动替换不同分布式部署节点的环境文件")
            // 获取不同机器的数字号 不同机器替换不同的机器特定配置文件
            def machineNum = "${ctx.MACHINE_TAG.replace("号机", "")}".toInteger()
            // 遍历文件夹下的所有文件并重命名 多机配置文件命名-n-拼接方式 如config-1-.yaml
            ctx.dir("${ctx.env.WORKSPACE}/${sourceFilePath}/") {
                def files = ctx.findFiles(glob: "*.*") // glob符合ant风格
                files.each { item ->
                    //ctx.println("${item.name}")
                    def machineFlag = "-${machineNum}-"
                    if ("${item.name}".contains(machineFlag)) {
                        def newConfigName = "${item.name.replace(machineFlag, "")}"
                        //ctx.println(newConfigName)
                        ctx.sh "mv ${item.name} ${newConfigName}"
                    }
                }
            }

            // 重命名后整体批量复制替换多个文件
            ctx.sh "cp -r ${ctx.env.WORKSPACE}/${sourceFilePath}/* ${ctx.env.WORKSPACE}/${targetFilePath}/"
            // 替换文件应该放在部署服务器上面 或 重新打包部署
        }
    }

    /**
     * 替换自定义的nginx配置文件
     * 需要定制化特殊化需求可在部署的时候动态替换本文件
     */
    static def replaceNginxConfig(ctx) {
        if ("${ctx.CUSTOM_NGINX_CONFIG}".trim() != "") {
            ctx.println("替换自定义的nginx配置文件")
            ctx.sh "cp -p ${ctx.env.WORKSPACE}/${ctx.CUSTOM_NGINX_CONFIG} ${ctx.env.WORKSPACE}/ci/.ci/web/default.conf"
        }
    }


}
