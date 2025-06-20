package shared.library.common

import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*

//import groovy.json.JsonOutput

/**
 * @author 潘维吉
 * @date 2021/8/18 13:22
 * @email 406798106@qq.com
 * @description 部署Kubernetes云原生应用
 * Kubernetes 是一个开源容器编排系统，用于容器化应用的自动部署、自动容器编排、自动扩缩与自动修复等管理。它将构成应用的容器按逻辑单位进行分组以便于管理和发现。
 */
class Kubernetes implements Serializable {

    static def k8sYamlFile = "k8s.yaml" // k8s集群应用部署yaml定义文件
    static def pythonYamlFile = "k8s_yaml.py" // 使用Python动态处理Yaml文件
    static def k8sNameSpace = "default" // k8s默认命名空间
    static def canaryDeploymentName = "" // 灰度发布部署名称

    /**
     * 声明式执行k8s集群部署
     */
    static def deploy(ctx, map, deployNum = 0) {

        // 多个K8s集群同时循环滚动部署
        "${map.k8s_credentials_ids}".trim().split(",").each { k8s_credentials_id ->
            // KUBECONFIG变量为k8s中kubectl命令的yaml配置授权访问文件内容 数据保存为Jenkins的“Secret file”类型的凭据，用credentials方法从凭据中获取
            ctx.withCredentials([ctx.file(credentialsId: "${k8s_credentials_id}", variable: 'KUBECONFIG')]) {
                // 1. 安装kubectl命令访问k8s集群: https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/ 或 https://kubernetes.io/docs/tasks/tools/install-kubectl-windows/
                // kubectl命令Windows下配置到环境变量Path中 路径为kubectl.exe所在的文件夹目录 不包括exe文件
                // 下载集群的配置文件，复制到本地计算机的 $HOME/.kube/config（kubectl的默认路径）
                // 2. 若您之前配置过KUBECONFIG环境变量，kubectl会优先加载KUBECONFIG环境变量包括文件路径，而不是$HOME/.kube/config，使用时请注意
                // ctx.println("k8s集群访问配置：${ctx.KUBECONFIG}")  ctx.sh "kubectl version"

                ctx.println("开始部署Kubernetes云原生应用 🏗️ ")

                // 动态替换k8s yaml声明式部署文件
                setYamlConfig(ctx, map, deployNum)

                // 部署应用 相同应用不同环境配置 需循环执行不同的镜像 指定命名空间--namespace=
                ctx.sh """ 
                    kubectl apply -f ${k8sYamlFile}
                    """

                ctx.println("K8S集群执行部署命令完成 ✅")

                // 部署命令执行后的各种处理
                afterDeployRun(ctx, map, deployNum)

            }
        }
    }

    /**
     * 动态替换k8s yaml声明式部署文件
     * 可自定义yaml部署文件 如存放在业务代码仓库 无需本次动态配置
     */
    //@NonCPS
    static def setYamlConfig(ctx, map, deployNum = 0) {
        def appName = "${ctx.FULL_PROJECT_NAME}" // 应用名称
        def hostPort = "${ctx.SHELL_HOST_PORT}" // 宿主机端口
        def containerPort = "${ctx.SHELL_EXPOSE_PORT}" // 容器内端口

        def imageTag = Docker.imageTag
        def k8sPodReplicas = "${ctx.K8S_POD_REPLICAS}"

/*        def k8sVersion = getK8sVersion(ctx)
        // k8s高版本默认containerd轻量 高性能 已移除对Docker作为容器运行时不再内置 Dockershim 组件负责与 Docker 通信的桥接层
        if (Utils.compareVersions(k8sVersion, "1.24.0") == 1 || Utils.compareVersions(k8sVersion, "1.24.0") == 0) {
            // 新版 Docker 构建的镜像默认符合 OCI（Open Container Initiative）规范仍可在K8S环境 containerd、CRI-O 等运行时中无缝运行
            // ctx.sh " ctr version "  // containerd版本号 containerd是Docker引擎基础组件所有默认自带
        }*/

        // 判断是否存在扩展端口
        def yamlDefaultPort = ""
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && ctx.SHELL_EXTEND_PORT != "") {
            if ("${ctx.IS_SOURCE_CODE_DEPLOY}" == 'true') {
                hostPort = "${ctx.SHELL_EXTEND_PORT}" // 宿主机端口
            }
            containerPort = "${ctx.SHELL_EXTEND_PORT}"
            yamlDefaultPort = " --default_port=${ctx.SHELL_HOST_PORT} "
            ctx.println("应用服务扩展端口: " + containerPort)
        }

        // 不同配置环境的相同应用 或者 定时任务在应用代码内无分布式处理机制情况
        if ("${ctx.IS_DIFF_CONF_IN_DIFF_MACHINES}" == 'true' && "${ctx.SOURCE_TARGET_CONFIG_DIR}".trim() != "") {
            if (deployNum != 0) { // k8s内相同应用不同容器镜像标签部署
                appName += "-node"
                imageTag += Docker.imageNodeTag + deployNum
                k8sPodReplicas = Integer.parseInt(k8sPodReplicas) - 1 // 除主节点其它节点配置相同
            } else {
                k8sPodReplicas = 1  // 主节点只部署一个
            }
        }

        // 是否执行K8S默认的健康探测
        def terminationGracePeriodSeconds = 30 // k8s默认30s 因为无健康探测就不知道应用是否启动成功 延长旧pod销毁时间 可减少对外服务中断时长
        if ("${ctx.IS_DISABLE_K8S_HEALTH_CHECK}" == "false") {
            terminationGracePeriodSeconds = 3 // 开启健康探测情况减少pod优雅关闭时间 加速部署 探测到新pod启动成功快速销毁旧pod
        }

        // 灰度发布  金丝雀发布  A/B测试
        def canaryFlag = "canary"
        canaryDeploymentName = appName + "-" + canaryFlag + "-deployment"
        if ("${ctx.IS_CANARY_DEPLOY}" == 'true') {
            // 只发布一个新的pod服务用于验证服务, 老服务不变, 验证完成后取消灰度发布, 重新发布全量服务
            appName += "-" + canaryFlag
            k8sPodReplicas = 1  // 只部署一个新服务用于验证
            // k8sPodReplicas= ctx.K8S_CANARY_DEPLOY_PERCENTAGE * Integer.parseInt(k8sPodReplicas)  // 也可以根据pod做百分比计算
            // 新增了canary测试节点同时减少老旧pod节点数
/*          def oldDeploymentName = appName + "-deployment"
            def newK8sPodReplicas = Integer.parseInt(k8sPodReplicas) - 1
            ctx.sh "kubectl scale deployment ${oldDeploymentName} --replicas=${newK8sPodReplicas} || true"   */
        }

        ctx.sh "sed -e 's#{IMAGE_URL}#${ctx.DOCKER_REPO_REGISTRY}/${ctx.DOCKER_REPO_NAMESPACE}/${ctx.dockerImageName}#g;s#{IMAGE_TAG}#${imageTag}#g;" +
                " s#{APP_NAME}#${appName}#g;s#{APP_COMMON_NAME}#${ctx.FULL_PROJECT_NAME}#g;s#{SPRING_PROFILE}#${ctx.SHELL_ENV_MODE}#g; " +
                " s#{HOST_PORT}#${hostPort}#g;s#{CONTAINER_PORT}#${containerPort}#g;s#{DEFAULT_CONTAINER_PORT}#${ctx.SHELL_EXPOSE_PORT}#g; " +
                " s#{K8S_POD_REPLICAS}#${k8sPodReplicas}#g;s#{MAX_CPU_SIZE}#${map.docker_limit_cpu}#g;s#{MAX_MEMORY_SIZE}#${map.docker_memory}#g;s#{JAVA_OPTS_XMX}#${map.docker_java_opts}#g; " +
                " s#{K8S_IMAGE_PULL_SECRETS}#${map.k8s_image_pull_secrets}#g;s#{CUSTOM_HEALTH_CHECK_PATH}#${ctx.CUSTOM_HEALTH_CHECK_PATH}#g;s#{K8S_NAMESPACE}#${k8sNameSpace}#g; " +
                " s#{K8S_GRACE_PERIOD_SECONDS}#${terminationGracePeriodSeconds}#g; " +
                " ' ${ctx.WORKSPACE}/ci/_k8s/${k8sYamlFile} > ${k8sYamlFile} "

        def pythonYamlParams = ""
        def isYamlUseSession = ""
        def yamlVolumeMounts = ""
        def yamlNfsParams = ""
        def setCustomStartupCommand = ""
        def setYamlArgs = ""
        def setPythonParams = ""
        def isK8sHealthProbe = ""

        // 复杂参数动态组合配置yaml文件
        if ("${ctx.IS_USE_SESSION}" == "true") {   // k8s集群业务应用是否使用Session 做亲和度关联
            isYamlUseSession = " --is_use_session=true "
        }
        if ("${ctx.DOCKER_VOLUME_MOUNT}".trim() != "") { // 容器挂载映射
            yamlVolumeMounts = "  --volume_mounts=${ctx.DOCKER_VOLUME_MOUNT} "
        }
        if ("${ctx.NFS_MOUNT_PATHS}".trim() != "") { // NFS服务
            yamlNfsParams = " --nfs_server=${ctx.NFS_SERVER}  --nfs_params=${ctx.NFS_MOUNT_PATHS} "
        }
        // 自定义启动命令
        if ("${ctx.IS_SOURCE_CODE_DEPLOY}" == 'true') {  // 源码直接部署 无需打包 只需要压缩上传到服务器上执行自定义命令启动
            // 1. 直接执行自定义命令  2. 执行命令文件
            setCustomStartupCommand = " --set_custom_startup_command='${ctx.CUSTOM_STARTUP_COMMAND}' "
        }
        // java动态设置k8s yaml args参数
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${ctx.COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java
                && "${ctx.JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringBoot && "${ctx.IS_SPRING_NATIVE}" == "false") {
            setYamlArgs = " --set_yaml_args='${map.docker_java_opts}' "
        }
        // 设置python语言相关的参数
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${ctx.COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
            setPythonParams = " --set_python_start_file=${ctx.CUSTOM_PYTHON_START_FILE} "
        }
        // 是否禁止执行K8S默认的健康探测
        if ("${ctx.IS_DISABLE_K8S_HEALTH_CHECK}" == "true") {
            isK8sHealthProbe = " --is_k8s_health_probe=true "
        }

        pythonYamlParams = isYamlUseSession + yamlVolumeMounts + yamlNfsParams + yamlDefaultPort + setCustomStartupCommand + setYamlArgs + setPythonParams + isK8sHealthProbe
        if ("${pythonYamlParams}".trim() != "") {
            ctx.dir("${ctx.env.WORKSPACE}/ci/_k8s") {
                ctx.println("使用Python的ruamel包动态配置K8S的Yaml文件: " + pythonYamlParams)
                // ctx.sh " python --version "
                // ctx.sh " pip install ruamel.yaml "
                ctx.sh " python ${pythonYamlFile} --k8s_yaml_file=${ctx.env.WORKSPACE}/${k8sYamlFile}  ${pythonYamlParams} "
            }
        }
        ctx.sh " cat ${k8sYamlFile} "
    }

    /**
     * 部署Pod自动水平扩缩容  可基于QPS自定义参数
     * 参考文档：https://imroc.cc/k8s/best-practice/custom-metrics-hpa
     */
    static def deployHPA(ctx, map, deployNum = 0) {
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) { // 后端并发量大需要扩容  如果是QPS扩缩容 只限于服务端集成Prometheus监控
            // 安装k8s-prometheus-adpater
            // Helm.installPrometheus(ctx)

            def yamlName = "hpa.yaml"
            // 如果cpu或内存达到限额百分之多少 进行自动扩容
            def cpuHPAValue = 0.8 // CPU使用多少百分比扩缩容
            def memoryHPAValue = 0.9 // 内存使用多少百分比扩缩容
            def defaultHPANum = 1  // 默认扩容个数 加或乘 合理设置 防止资源不足

            def cpuHPA = Integer.parseInt("${map.docker_limit_cpu}".replace("m", "")) * cpuHPAValue + "m"
            // 内存值不支持小数  转成成为M数据
            def memoryUnit = "${map.docker_memory}".contains("G") ? "G" : "M"
            def memoryHPA = Math.floor(Integer.parseInt("${map.docker_memory}".replace(memoryUnit, "")) * memoryHPAValue * 1024) + "M"
            def k8sPodReplicas = "${ctx.K8S_POD_REPLICAS}"
            // 最大扩容数量设置为基础pod节点的倍数 默认为2倍或者+1 避免过多扩容节点导致资源耗尽
            def maxK8sPodReplicas = Integer.parseInt(k8sPodReplicas) + defaultHPANum // * 2

            // 不同配置环境的相同应用 或者 定时任务在应用代码内无分布式处理机制情况
            if ("${ctx.IS_DIFF_CONF_IN_DIFF_MACHINES}" == 'true' && "${ctx.SOURCE_TARGET_CONFIG_DIR}".trim() != "") {
                if (deployNum != 0) { // 第二次以后环境部署
                    k8sPodReplicas = Integer.parseInt(k8sPodReplicas) - 1 // 除主节点其它节点配置相同
                } else {
                    k8sPodReplicas = 1  // 主节点只部署一个  避免定时任务重复执行
                    maxK8sPodReplicas = 1
                }
            }

            def k8sVersion = getK8sVersion(ctx)
            def hpaApiVersion = "v2" // 默认使用新的稳定版本
            if (Utils.compareVersions(k8sVersion, "1.23.0") == -1) { // k8s低版本 使用低版本api
                hpaApiVersion = hpaApiVersion + "beta2"
            }

            ctx.sh "sed -e ' s#{APP_NAME}#${ctx.FULL_PROJECT_NAME}#g;s#{HOST_PORT}#${ctx.SHELL_HOST_PORT}#g; " +
                    " s#{APP_COMMON_NAME}#${ctx.FULL_PROJECT_NAME}#g;s#{K8S_POD_REPLICAS}#${k8sPodReplicas}#g;s#{MAX_K8S_POD_REPLICAS}#${maxK8sPodReplicas}#g; " +
                    " s#{MAX_CPU_SIZE}#${cpuHPA}#g;s#{MAX_MEMORY_SIZE}#${memoryHPA}#g;s#{K8S_NAMESPACE}#${k8sNameSpace}#g; " +
                    " s#{HPA_API_VERSION}#${hpaApiVersion}#g; " +
                    " ' ${ctx.WORKSPACE}/ci/_k8s/${yamlName} > ${yamlName} "
            ctx.sh " cat ${yamlName} "

            ctx.println("K8S集群执行部署Pod自动水平扩缩容 💕")
            // 部署Pod水平扩缩容  如果已存在不重新创建 部署前删除旧HPA更新到最新yaml配置 可使用 kubectl patch命令热更新
            ctx.sh "kubectl get hpa ${ctx.FULL_PROJECT_NAME}-hpa -n ${k8sNameSpace} || kubectl apply -f ${yamlName}"

        }
    }

    /**
     * 七层负载和灰度发布配置部署
     */
    static def ingressNginx(ctx, map) {
        def yamlName = "ingress.yaml"
        ctx.sh "sed -e ' s#{APP_NAME}#${ctx.FULL_PROJECT_NAME}#g;s#{HOST_PORT}#${ctx.SHELL_HOST_PORT}#g; " +
                " ' ${ctx.WORKSPACE}/ci/_k8s/${yamlName} > ${yamlName} "
        ctx.sh " cat ${yamlName} "

        // 部署七层负载和灰度发布配置
        ctx.sh "kubectl apply -f ${yamlName}"
        //ctx.sh "kubectl get ing"
    }

    /**
     * 灰度发布  金丝雀发布  A/B测试
     * 参考文档: https://help.aliyun.com/document_detail/200941.html
     */
    static def ingressDeploy(ctx, map) {
        // 需要提供一下几个参数：
        // 灰度发布匹配的方式  1、 header  2、 cookie   3. weight
        // yaml文件中灰度匹配的名称version  灰度匹配的值new  。 version=new 表示新版本
        // 灰度发布初始化流量权重 当时灰度部署完成后的新版流量权重 如20%访问流量到新版本
        // 新版发布后启动等待时间, 每隔多长时间更改流量规则, 单位秒  逐渐提高新版流量权重实现灰度发布
        // 新版发布全部完成老版本下线等待时间, 隔多长时间下线旧应用, 单位秒  流量全部切到新版本后下线旧应用等待保证稳定性

        // 定义 ingress yaml数据
        def ingressJson = [
                "host": "${ctx.APPLICATION_DOMAIN}",
                "http": ["paths": [["path"   : "/v2", "pathType": "Prefix",
                                    "backend": ["service": ["name": "${ctx.FULL_PROJECT_NAME}-service", "port": ["number": ctx.SHELL_HOST_PORT]]]]],
                ],
        ]

        // 根据service名称找到ingress的名称
        def ingressName = ctx.sh("kubectl get ingress -n ${k8sNameSpace} -o jsonpath='{.items[?(@.spec.rules[0].host==\"${ctx.APPLICATION_DOMAIN}\")].metadata.name}'").trim()

        // 基于 kubectl patch 动态更新方案（无需重建）
        // 新增 host 规则 /spec/rules/- yaml路径数组最后一个新增
        ctx.sh " kubectl patch ingress my-ingress -n ${k8sNameSpace} --type='json' -p='[{" op ": " add ", " path ": " / spec / rules / -", " value ": " $ { ingressJson } "}]' "

        // 查看yaml数组
        // kubectl get ingress my-ingress -n ${k8sNameSpace} -o jsonpath='{.spec.rules}'

        // 删除 host 规则 /spec/rules/index yaml路径数组index下标 查看API版本 kubectl get ingress my-ingress -n default -o jsonpath='{.apiVersion}'
        // kubectl patch ingress my-ingress -n ${k8sNameSpace} --type='json' -p='[{"op": "remove", "path": "/spec/rules/1"}]' -v9


        ctx.sh "kubectl apply -f ingress.yaml"
        ctx.sh "kubectl get ingress || true"
        // 系统运行一段时间后，当新版本服务已经稳定并且符合预期后，需要下线老版本的服务 ，仅保留新版本服务在线上运行。
        // 为了达到该目标，需要将旧版本的Service指向新版本服务的Deployment，并且删除旧版本的Deployment和新版本的Service。

        // 修改旧版本Service，使其指向新版本服务 更改selector: app的新pod名称
        ctx.sh "kubectl apply -f service.yaml"
        // 删除Canary Ingress资源gray-release-canary
        ctx.sh "kubectl delete ingress gray-release-canary"
        // 删除旧版本的Deployment和新版本的Service
        ctx.sh "kubectl delete deployment old-deployment-name"
        ctx.sh "kubectl delete svc new-service-name"
    }

    /**
     * K8S方式实现蓝绿部署
     */
    static def blueGreenDeploy(ctx, map) {
        // 蓝绿发布是为新版本创建一个与老版本完全一致的生产环境，在不影响老版本的前提下，按照一定的规则把部分流量切换到新版本，
        // 当新版本试运行一段时间没有问题后，将用户的全量流量从老版本迁移至新版本。
        ctx.sh " "
    }

    /**
     *  K8S部署后执行的脚本
     */
    static def afterDeployRun(ctx, map, deployNum) {

        // 查看个组件的状态  如 kubectl get svc
        // kubectl get pod || true
        ctx.sh """ 
                    kubectl top pod || true
                    kubectl top nodes || true
                    """

        // 部署Pod弹性水平扩缩容 可基于QPS自动伸缩  只需要初始化一次 定时任务没做分布式处理情况不建议扩缩容
        if ("${ctx.IS_CANARY_DEPLOY}" != 'true' && "${ctx.IS_K8S_AUTO_SCALING}" == 'true') {
            deployHPA(ctx, map, deployNum)
        }

        if ("${ctx.IS_CANARY_DEPLOY}" != 'true') {
            // 全量部署同时删除上次canary灰度部署服务
            ctx.sh "kubectl delete deployment ${canaryDeploymentName} --ignore-not-found || true"
        }

        // 七层负载和灰度发布配置部署ingress
        // ingressNginxDeploy(ctx, map)

        def k8sStartTime = new Date()
        // K8S部署验证是否成功
        verifyDeployment(ctx)
        // 计算应用部署启动时间
        ctx.healthCheckTimeDiff = Utils.getTimeDiff(k8sStartTime, new Date(), "${ctx.K8S_POD_REPLICAS}".toInteger())

    }

    /**
     * K8S验证部署是否成功
     */
    static def verifyDeployment(ctx) {
        // 前提开启 readinessProbe和livenessProbe 健康探测
        ctx.println("K8S集群所有Pod节点健康探测中, 请耐心等待... 🚀")
        def deploymentName = "${ctx.FULL_PROJECT_NAME}" // labels.app标签值
        def namespace = k8sNameSpace
        def k8sPodReplicas = Integer.parseInt(ctx.K8S_POD_REPLICAS) // 部署pod数
        ctx.sleep 2 // 等待检测  需要等待容器镜像下载如Pending状态等  可以先判断容器下载完成后再执行下面的检测
        // 等待所有Pod达到Ready状态
        ctx.timeout(time: 10, unit: 'MINUTES') { // 设置超时时间
            def podsAreReady = false
            int readyCount = 0
            int totalPods = 0
            def podStatusPhase = ""
            def whileCount = 0  // 循环次数
            while (!podsAreReady) {
                whileCount++
                def output = ctx.sh(script: "kubectl get pods -n $namespace -l app=$deploymentName -o json", returnStdout: true)
                def podStatus = ctx.readJSON text: output

                readyCount = podStatus.items.findAll { it.status.containerStatuses.every { it.ready == true } }.size()
                totalPods = podStatus.items.size()
                podStatusPhase = podStatus.items.status.phase // Running状态容器正式启动运行

                if (readyCount == totalPods) {
                    podsAreReady = true
                } else {
                    // yaml内容中包含初始化时间和启动完成时间 shell中自动解析所有内容，建议yq进行实际的YAML解析
                    ctx.echo "Waiting for all pods to be ready. Currently Ready: $readyCount / Total: $totalPods ,  podStatusPhase: $podStatusPhase"
                    if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
                        def sleepTime = k8sPodReplicas * 3 - whileCount
                        ctx.sleep sleepTime < 3 ? 3 : sleepTime // 每隔多少秒检查一次
                    }
                    if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                        def sleepTime = k8sPodReplicas * 2 - whileCount
                        ctx.sleep sleepTime < 2 ? 2 : sleepTime // 每隔多少秒检查一次
                    }
                }
            }

            // 除了Running之外的状态  都不能算部署成功 Pod处于Pending状态也会通过上面的Ready状态检测代码 其实部署是失败的
            // 如 Pending 由于资源不足或其他限制  Terminating 器可能还在停止中或资源清理阶段  ContainerCreating 容器尚未创建完成
            // Failed 如果Pod中的所有容器都因失败而退出，并且不会再重启，则Pod会进入Failed状态  CrashLoopBackOff 时，这意味着 Pod 中的主容器（或其中一个容器）反复启动并快速退出
            if (podsAreReady == true) { //  健康探测成功
                ctx.echo "Currently Ready: $readyCount / Total: $totalPods ,  podStatusPhase: $podStatusPhase"
                if (podStatusPhase.contains("Pending") || podStatusPhase.contains("Terminating")
                        || podStatusPhase.contains("ContainerCreating") || podStatusPhase.contains("CrashLoopBackOff")) {
                    Tools.printColor(ctx, "K8S集群中Pod服务部署启动失败  ❌", "red")
                    ctx.error("K8S集群中Pod服务部署启动失败 终止流水线运行 ❌")
                } else {
                    Tools.printColor(ctx, "K8S集群中所有Pod服务已处于启动状态 ✅")
                }
            } else { //  健康探测失败
                Tools.printColor(ctx, "K8S集群中Pod服务部署启动失败  ❌", "red")
                ctx.error("K8S集群中Pod服务部署启动失败 终止流水线运行 ❌")
            }
        }
    }

    /**
     * 获取k8s版本号
     */
    static def getK8sVersion(ctx) {
        def k8sVersion = ctx.sh(script: " kubectl version --short --output json ", returnStdout: true).trim()
        // 解析json数据
        def k8sVersionMap = ctx.readJSON text: k8sVersion
        def version = k8sVersionMap.serverVersion.gitVersion
        ctx.echo "K8S版本: ${version}"
        return version
    }

    /**
     * 基于K8s内置的回滚功能
     */
    static def rollback(ctx) {
        // 回滚上一个版本
        ctx.sh " kubectl rollout undo deployment/deployment-name -n ${k8sNameSpace} "
        // 回滚到指定版本
        ctx.sh " kubectl rollout undo deployment/deployment-name -n ${k8sNameSpace} --revision=2 "
    }

    /**
     * K8s健康探测
     */
    static def healthDetection(ctx) {
        // Pod通过三类探针来检查容器的健康状态。分别是StartupProbe（启动探针） ReadinessProbe（就绪探测） 和 LivenessProbe（存活探测）
    }

    /**
     * k8s集群自动垃圾回收机制
     * 清除无效镜像  删除无效镜像 减少磁盘占用
     */
    static def cleanDockerImages(ctx) {
        // kubelet容器自动GC垃圾回收 如 image-gc-high-threshold 参数 参考文档: https://kubernetes-docsy-staging.netlify.app/zh/docs/concepts/cluster-administration/kubelet-garbage-collection/
        // 默认Kubelet会在节点驱逐信号触发和Image对应的Filesystem空间不足的情况下删除冗余的镜像
        // node节点 cat /etc/kubernetes/kubelet 镜像占用磁盘空间的比例超过高水位（可以通过参数ImageGCHighThresholdPercent 进行配置），kubelet 就会清理不用的镜像
    }

}
