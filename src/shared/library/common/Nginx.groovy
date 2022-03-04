package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/9/1 08:02
 * @email 406798106@qq.com
 * @description Nginx中间件服务
 */
class Nginx implements Serializable {

    static def sshCommand = "root@39.96.19.25"
    static def nginxConfFolder = "/etc/nginx/conf.d"

    /**
     * CI/CD自动设置Nginx负载均衡 自动生效
     */
    static def conf(ctx, serverIp, mainPort, workerServerIp, workPort) {
        try {
            // 对于蓝绿部署和滚动部署等场景 CI/CD自动配置Nginx负载均衡
            def defaultFileName = "nginx-default.conf"
            def newFileName = "${ctx.env.JOB_NAME}.conf"
            def jenkinsResources = "${ctx.env.WORKSPACE}/ci/resources"
            // nginx负载均衡基础模板
            def nginxConfFile = "${jenkinsResources}/${newFileName}"
            // 更换配置文件名称 防止重复
            ctx.sh "mv ${jenkinsResources}/${defaultFileName} ${nginxConfFile}"
            // 更新模板数据配置
            def nginxConfContent = ctx.readFile(file: "${nginxConfFile}")
            ctx.writeFile file: "${nginxConfFile}", text: "${nginxConfContent}"
                    .replaceAll("LISTEN_SERVER_PORT", mainPort)
                    .replaceAll("MAIN_SERVER_IP", serverIp)
                    .replaceAll("MAIN_SERVER_PORT", mainPort)
                    .replaceAll("WORKER_SERVER_IP", workerServerIp)
                    .replaceAll("WORKER_SERVER_PORT", workPort)
                    .replaceAll("UPSTREAM_NAME", ctx.env.JOB_NAME)
            // 检测端口是否冲突
            ctx.sh " ssh ${sshCommand} 'lsof -i:${mainPort}' "
            // 复制新模板配置到Nginx服务目录下
            ctx.sh "scp -r ${nginxConfFile} ${sshCommand}:${nginxConfFolder}/"
            // 重新加载nginx使配置生效
            ctx.sh " ssh ${sshCommand} ' nginx -t -c ${nginxConfFolder}/${newFileName} && nginx -s reload ' "
        } catch (e) {
            ctx.println("Nginx负载均衡自动配置*.conf失败")
            ctx.println(e.getMessage())
        }
    }

}
