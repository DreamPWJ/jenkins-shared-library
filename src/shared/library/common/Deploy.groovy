package shared.library.common

import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*

/**
 * @author 潘维吉
 * @date 2021/1/26 13:22
 * @email 406798106@qq.com
 * @description 部署相关
 */
class Deploy implements Serializable {

    /**
     * SSH 通过堡垒机/跳板机 访问目标机器 利用ssh的高级的ProxyJump最方便或中级的ProxyJump或者ssh tunnel隧道功能来透过跳板机
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
     * 自动替换相同应用不同分布式部署节点的环境文件
     * 自定义的部署配置文件替代默认配置文件等
     * 注意：多机配置文件命名-n-拼接方式覆盖 如config-1-.yaml
     */
    static def replaceEnvFile(ctx, deployNum = 0) {
        // 源文件和多个目标文件可放在代码里面维护 部署时候根据配置自动替换到目标服务器
        // 或者项目源码仓库内的配置文件替换CI仓库的默认文件等
        def projectDir = ""  // 获取项目代码具体目录
        def sourceFilePath = ""  // 源文件目录 真正的配置文件
        def targetFilePath = ""  // 目标文件目录 要替换的配置文件
        if ("${ctx.SOURCE_TARGET_CONFIG_DIR}".trim() != "") {
            ctx.println("自动替换不同分布式部署节点的环境文件")
            sourceFilePath = "${ctx.SOURCE_TARGET_CONFIG_DIR}".split(",")[0]
            targetFilePath = "${ctx.SOURCE_TARGET_CONFIG_DIR}".split(",")[1]
            // 获取项目代码具体目录
            projectDir = "${ctx.env.WORKSPACE}" + ("${ctx.IS_MAVEN_SINGLE_MODULE}" == 'true' ? "" : ("${ctx.MAVEN_ONE_LEVEL}" == "" ? "/${ctx.PROJECT_NAME}" : "/${ctx.MAVEN_ONE_LEVEL}${ctx.PROJECT_NAME}"))
        }

        // 多个服务器配置文件不同
        if ("${ctx.IS_DIFF_CONF_IN_DIFF_MACHINES}" == 'true' && "${ctx.SOURCE_TARGET_CONFIG_DIR}".trim() != "") {
            // 获取不同机器的数字号 不同机器替换不同的机器特定配置文件
            def machineNum = deployNum == 0 ? "${ctx.MACHINE_TAG.replace("号机", "")}".toInteger() : deployNum

            // 遍历文件夹下的所有文件并重命名 多机配置文件命名-n-拼接方式 如config-1-.yaml
            ctx.dir("${projectDir}/${sourceFilePath}/") {
                def files = ctx.findFiles(glob: "*.*") // glob符合ant风格
                files.each { item ->
                    // ctx.println("${item.name}")
                    def machineFlag = "-${machineNum}-"
                    if ("${item.name}".contains(machineFlag)) {
                        def newConfigName = "${item.name.replace(machineFlag, "")}"
                        //ctx.println(newConfigName)
                        ctx.sh "mv ${item.name} ${newConfigName}"
                    } else if (machineNum >= 3) {  // 针对3台以上部署机器 但配置只有两种不同的  第一台机器一个配置  其它所有机器一个配置的情况
                        //  默认第三台机器以后 使用的都是第二台机器的配置即可
                    }
                }
            }

            // 重命名后整体批量复制替换多个文件
            ctx.sh "cp -r ${projectDir}/${sourceFilePath}/* ${projectDir}/${targetFilePath}/"
            // 替换文件应该放在部署服务器上面 或 重新打包部署
        } else if ("${ctx.SOURCE_TARGET_CONFIG_DIR}".trim() != "") {   // 多个服务器配置文件相同
            // 重命名后整体批量复制替换多个文件   针对服务器配置文件相同的情况  但代码内没做多环境  使用配置文件目录区分
            ctx.sh "cp -r ${projectDir}/${sourceFilePath}/* ${projectDir}/${targetFilePath}/"
        }
    }

    /**
     * 不同分布式部署节点使用不同的yaml环境文件
     * 如 application-prod-1.yaml 、application-prod-2.yaml
     */
    static def changeEnvModeYamlFile(ctx, deployNum = 0) {
        if ("${ctx.IS_DIFF_CONF_IN_DIFF_MACHINES}" == 'true' && "${ctx.JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringBoot) {
            // 获取不同机器的数字号 不同机器替换不同的机器特定配置文件
            def machineNum = deployNum == 0 ? "${ctx.MACHINE_TAG.replace("号机", "")}".toInteger() : deployNum
            def machineFlag = "-${machineNum}"
            ctx.SHELL_ENV_MODE = "${ctx.SHELL_ENV_MODE}" + machineFlag
            ctx.println("${ctx.SHELL_ENV_MODE}")
        }
    }

    /**
     * 自定义的nginx配置文件替换
     * 需要定制化特殊化需求可在部署的时候动态替换本文件
     */
    static def replaceNginxConfig(ctx) {
        if ("${ctx.CUSTOM_NGINX_CONFIG}".trim() != "") {
            ctx.println("替换自定义的nginx配置文件")
            def monoRepoProjectDir = "${ctx.IS_MONO_REPO}" == 'true' ? "${ctx.monoRepoProjectDir}/" : ""
            ctx.sh " cp -p ${ctx.env.WORKSPACE}/${monoRepoProjectDir}${ctx.CUSTOM_NGINX_CONFIG} " +
                    " ${ctx.env.WORKSPACE}/ci/.ci/web/default.conf "
        }
    }

    /**
     * 控制服务 启动 停止 重启等
     */
    static def controlService(ctx, map) {
        def type = "" // 控制类型
        def typeText = "" // 控制类型文案
        def dockerContainerName = "${ctx.FULL_PROJECT_NAME}-${ctx.SHELL_ENV_MODE}" // docker容器名称
        def deploymentName = "${ctx.PROJECT_NAME}" + "-deployment"  // kubernetes deployment名称

        if (GlobalVars.start == ctx.params.DEPLOY_MODE) {
            type = "启动"
        }
        if (GlobalVars.stop == ctx.params.DEPLOY_MODE) {
            type = "停止"
        }
        if (GlobalVars.destroy == ctx.params.DEPLOY_MODE) {
            type = "销毁"
        }
        if (GlobalVars.restart == ctx.params.DEPLOY_MODE) {
            type = "重启"
        }
        typeText = type + "服务: " + ("${ctx.IS_K8S_DEPLOY}" == 'true' ? deploymentName : dockerContainerName)
        ctx.println(typeText)

        // 多服务器命令控制
        if ("${ctx.IS_K8S_DEPLOY}" == 'true') {
            // 多个K8s集群同时循环滚动部署
            "${map.k8s_credentials_ids}".trim().split(",").each { k8s_credentials_id ->
                // KUBECONFIG变量为k8s中kubectl命令的yaml配置授权访问文件内容 数据保存为Jenkins的“Secret file”类型的凭据，用credentials方法从凭据中获取
                ctx.withCredentials([ctx.file(credentialsId: "${k8s_credentials_id}", variable: 'KUBECONFIG')]) {
                    // ctx.sh "kubectl version"
                    ctx.println("K8S服务方式控制服务 启动、停止、重启等")
                    if (GlobalVars.start == ctx.params.DEPLOY_MODE) {
                        startService(ctx, map)
                    }
                    if (GlobalVars.stop == ctx.params.DEPLOY_MODE) {
                        stopService(ctx, map)
                    }
                    if (GlobalVars.destroy == ctx.params.DEPLOY_MODE) {
                        destroyService(ctx, map)
                    }
                    if (GlobalVars.restart == ctx.params.DEPLOY_MODE) {
                        restartService(ctx, map)
                    }
                }
            }
        } else {
            // Docker服务方式
            ctx.println("Docker服务方式控制服务 启动、停止、重启等")
            def command = ""
            if (GlobalVars.start == ctx.params.DEPLOY_MODE) {
                command = "docker start " + dockerContainerName
            }
            if (GlobalVars.stop == ctx.params.DEPLOY_MODE) {
                command = "docker stop " + dockerContainerName
            }
            if (GlobalVars.destroy == ctx.params.DEPLOY_MODE) {
                command = "docker stop " + dockerContainerName + " && docker rm " + dockerContainerName
            }
            if (GlobalVars.restart == ctx.params.DEPLOY_MODE) {
                command = "docker restart " + dockerContainerName
            }
            // 执行控制命令
            ctx.println "${ctx.remote.host}"
            ctx.sh " ssh ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ctx.remote.host} ' " + command + " ' "
            // 循环串行执行多机分布式部署
            if (!ctx.remote_worker_ips.isEmpty()) {
                if (GlobalVars.restart == ctx.params.DEPLOY_MODE) {
                    ctx.timeout(time: 5, unit: 'MINUTES') {
                        def healthCheckMsg = ctx.sh(
                                script: "ssh  ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ctx.remote.host} ' cd /${ctx.DEPLOY_FOLDER}/ && ./health-check.sh -a ${ctx.PROJECT_TYPE} -b http://${ctx.remote.host}:${ctx.SHELL_HOST_PORT} '",
                                returnStdout: true).trim()
                        ctx.println "${healthCheckMsg}"
                    }
                }
                ctx.remote_worker_ips.each { ip ->
                    ctx.println ip
                    ctx.sh " ssh ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ip} ' " + command + " ' "
                    if (GlobalVars.restart == ctx.params.DEPLOY_MODE) {
                        // ctx.sleep 30  // 重启多个服务 防止服务不可用等待顺序重启
                        // 健康检测  判断服务是否启动成功
                        ctx.timeout(time: 5, unit: 'MINUTES') {  // health-check.sh有检测超时时间 timeout为防止shell脚本超时失效兼容处理
                            def healthCheckMsg = ctx.sh(
                                    script: "ssh  ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ip} ' cd /${ctx.DEPLOY_FOLDER}/ && ./health-check.sh -a ${ctx.PROJECT_TYPE} -b http://${ip}:${ctx.SHELL_HOST_PORT} '",
                                    returnStdout: true).trim()
                            ctx.println "${healthCheckMsg}"
                        }
                    }
                }
            }
        }

        // 控制完成钉钉通知大家 重要操作默认执行钉钉通知
        // if ("${ctx.params.IS_DING_NOTICE}" == 'true')  // 是否钉钉通知
        DingTalk.notice(ctx, "${map.ding_talk_credentials_id}", "执行" + type + "服务命令 [${ctx.env.JOB_NAME} ${ctx.PROJECT_TAG}](${ctx.env.JOB_URL})  👩‍💻 ", typeText + "\n  ##### 执行" + type + "控制命令完成 ✅  " +
                "\n  ###### 执行人: ${ctx.BUILD_USER} \n ###### 完成时间: ${Utils.formatDate()} (${Utils.getWeek(ctx)})", "")
    }

    /**
     * 启动服务
     */
    static def startService(ctx, map) {
        if ("${ctx.IS_K8S_DEPLOY}" == 'true') {
            // K8s服务方式
            def deploymentName = "${ctx.PROJECT_NAME}" + "-deployment"
            ctx.sh " kubectl scale deployment " + deploymentName + " --replicas=" + "${ctx.K8S_POD_REPLICAS}"
        } else {
            // Docker服务方式
            def dockerContainerName = "${ctx.FULL_PROJECT_NAME}-${ctx.SHELL_ENV_MODE}"
            ctx.sh " docker start  " + dockerContainerName
        }
    }

    /**
     * 停止服务
     */
    static def stopService(ctx, map) {
        if ("${ctx.IS_K8S_DEPLOY}" == 'true') {
            // K8s服务方式
            def deploymentName = "${ctx.PROJECT_NAME}" + "-deployment"
            ctx.sh " kubectl scale deployment " + deploymentName + " --replicas=0 "
        } else {
            // Docker服务方式
            def dockerContainerName = "${ctx.FULL_PROJECT_NAME}-${ctx.SHELL_ENV_MODE}"
            ctx.sh " docker stop " + dockerContainerName
        }
    }

    /**
     * 重启服务
     */
    static def restartService(ctx, map) {
        if ("${ctx.IS_K8S_DEPLOY}" == 'true') {
            // K8s服务方式
            def deploymentName = "${ctx.PROJECT_NAME}" + "-deployment"
            // 重启deployment命令 会逐一滚动重启 重启过程中保证服务可用性
            ctx.sh " kubectl rollout restart deployment " + deploymentName

            // 扩缩容方式重启 会导致服务不可用 同时停止服务和启动服务
//         ctx.sh " kubectl scale deployment " + deploymentName + " --replicas=0 "
//         ctx.sleep 2
//         ctx.sh " kubectl scale deployment " + deploymentName + " --replicas=" + "${ctx.K8S_POD_REPLICAS}"

        } else {
            // Docker服务方式
            def dockerContainerName = "${ctx.FULL_PROJECT_NAME}-${ctx.SHELL_ENV_MODE}"
            ctx.sh " docker restart " + dockerContainerName
        }
    }

    /**
     * 销毁删除服务
     */
    static def destroyService(ctx, map) {
        if ("${ctx.IS_K8S_DEPLOY}" == 'true') {
            // K8s服务方式
            def deploymentName = "${ctx.PROJECT_NAME}" + "-deployment"
            ctx.sh " kubectl delete deployment " + deploymentName
        } else {
            // Docker服务方式
            def dockerContainerName = "${ctx.FULL_PROJECT_NAME}-${ctx.SHELL_ENV_MODE}"
            ctx.sh " docker stop " + dockerContainerName + " && docker rm " + dockerContainerName
        }
    }


}
