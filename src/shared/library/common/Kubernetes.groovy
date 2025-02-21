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
    static def k8sNameSpace = "default" // k8s命名空间

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
                // ctx.println("k8s集群访问配置：${ctx.KUBECONFIG}")
                // ctx.sh "kubectl version"

                ctx.println("开始部署Kubernetes云原生应用 🏗️ ")

                // 动态替换k8s yaml声明式部署文件
                setYamlConfig(ctx, map, deployNum)

                // 部署应用 相同应用不同环境配置 需循环执行不同的镜像 指定命名空间--namespace=
                ctx.sh """ 
                    kubectl apply -f ${k8sYamlFile}
                    """

                // 查看个组件的状态  如 kubectl get svc
                ctx.sh """ 
                    kubectl get pod
                    kubectl top pod
                    kubectl top nodes
                    """

                // 七层负载和灰度发布配置部署ingress
                // ingressNginxDeploy(ctx, map)

                // 部署Pod弹性水平扩缩容 可基于QPS自动伸缩  只需要初始化一次
                if ("${ctx.IS_K8S_AUTO_SCALING}" == 'true') {
                    deployHPA(ctx, map)
                }

                // 删除服务
                // ctx.sh "kubectl delete -f ${k8sYamlFile}"
                // kubectl 停止删除pod 默认等待30秒  删除deployment 命令kubectl delete deployment  删除所有 kubectl delete pods --all  --force
                // kubectl delete pod podName
                // 查看详细信息   kubectl describe pod podName

                // 查看命名空间下pod在哪些node节点运行
                // ctx.sh "kubectl get pod -n ${k8sNameSpace} -o wide"
                // 查看pod节点当前的节点资源占用情况
                // ctx.sh "kubectl top pod"
                // 查看node节点当前的节点资源占用情况
                // ctx.sh "kubectl top nodes"

                // K8S健康检查 K8S默认有健康探测策略  k8s.yaml文件实现
                // healthDetection(ctx)

                // K8S运行容器方式使用Docker容器时 删除无效镜像 减少磁盘占用  K8S默认有容器清理策略 无需手动处理
                // cleanDockerImages(ctx)

                ctx.println("K8S集群执行部署完成 ✅")

                def k8sStartTime = new Date()
                // K8S部署验证是否成功
                verifyDeployment(ctx)
                // 计算应用启动时间
                ctx.healthCheckTimeDiff = Utils.getTimeDiff(k8sStartTime, new Date())
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

        // 判断是否存在扩展端口
        def yamlDefaultPort = ""
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && ctx.SHELL_EXTEND_PORT != "") {
            containerPort = "${ctx.SHELL_EXTEND_PORT}"
            yamlDefaultPort = " --default_port=${ctx.SHELL_HOST_PORT} "
            ctx.println("应用服务扩展端口: " + containerPort)
        }

        // 不同配置环境的相同应用
        if ("${ctx.IS_DIFF_CONF_IN_DIFF_MACHINES}" == 'true' && "${ctx.SOURCE_TARGET_CONFIG_DIR}".trim() != "") {
            if (deployNum != 0) { // k8s内相同应用不同容器镜像标签部署
                appName += "-node"
                imageTag += Docker.imageNodeTag + deployNum
                k8sPodReplicas = Integer.parseInt(k8sPodReplicas) - 1 // 除主节点其它节点相同
            } else {
                k8sPodReplicas = 1  // 主节点只部署一个
            }
        }

        // 灰度发布  金丝雀发布  A/B测试
        def canaryFlag = "canary"
        if ("${ctx.IS_CANARY_DEPLOY}" == 'true') {
            // 只发布一个新的pod服务用于验证服务, 老服务不变, 验证完成后取消灰度发布, 重新发布全量服务
            appName += "-" + canaryFlag
            k8sPodReplicas = 1  // 只部署一个新服务用于验证
            // k8sPodReplicas= ctx.K8S_CANARY_DEPLOY_PERCENTAGE * Integer.parseInt(k8sPodReplicas)  // 也可以根据pod做百分比计算
            // 新增了canary测试节点同时减少老旧pod节点数
/*          def oldDeploymentName = appName + "-deployment"
            def newK8sPodReplicas = Integer.parseInt(k8sPodReplicas) - 1
            ctx.sh "kubectl scale deployment ${oldDeploymentName} --replicas=${newK8sPodReplicas} || true"   */
        } else {
            // 全量部署同时删除上次canary灰度部署服务
            def deploymentName = appName + "-" + canaryFlag + "-deployment"
            ctx.sh "kubectl delete deployment ${deploymentName} || true"
        }

        ctx.sh "sed -e 's#{IMAGE_URL}#${ctx.DOCKER_REPO_REGISTRY}/${ctx.DOCKER_REPO_NAMESPACE}/${ctx.dockerBuildImageName}#g;s#{IMAGE_TAG}#${imageTag}#g;" +
                " s#{APP_NAME}#${appName}#g;s#{APP_COMMON_NAME}#${ctx.FULL_PROJECT_NAME}#g;s#{SPRING_PROFILE}#${ctx.SHELL_ENV_MODE}#g; " +
                " s#{HOST_PORT}#${hostPort}#g;s#{CONTAINER_PORT}#${containerPort}#g;s#{DEFAULT_CONTAINER_PORT}#${ctx.SHELL_EXPOSE_PORT}#g; " +
                " s#{K8S_POD_REPLICAS}#${k8sPodReplicas}#g;s#{MAX_CPU_SIZE}#${map.docker_limit_cpu}#g;s#{MAX_MEMORY_SIZE}#${map.docker_memory}#g;s#{JAVA_OPTS_XMX}#${map.docker_java_opts}#g; " +
                " s#{K8S_IMAGE_PULL_SECRETS}#${map.k8s_image_pull_secrets}#g;s#{CUSTOM_HEALTH_CHECK_PATH}#${ctx.CUSTOM_HEALTH_CHECK_PATH}#g; " +
                " ' ${ctx.WORKSPACE}/ci/_k8s/${k8sYamlFile} > ${k8sYamlFile} "

        def pythonYamlParams = ""
        def isYamlUseSession = ""
        def yamlVolumeMounts = ""
        def yamlNfsParams = ""
        def setYamlArags = ""
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
        // java动态设置k8s yaml args参数
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${ctx.COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java
                && "${ctx.JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringBoot && "${ctx.IS_SPRING_NATIVE}" == "false") {
            setYamlArags = " --set_yaml_arags='${map.docker_java_opts}' "
        }
        // 设置python语言相关的参数
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${ctx.COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
            setPythonParams = " --set_python_start_file=${ctx.CUSTOM_PYTHON_START_FILE} "
        }
        // 是否禁止执行K8S默认的健康探测
        if ("${ctx.IS_DISABLE_K8S_HEALTH_CHECK}" == "true") {
            isK8sHealthProbe = " --is_k8s_health_probe=true "
        }

        pythonYamlParams = isYamlUseSession + yamlVolumeMounts + yamlNfsParams + yamlDefaultPort + setYamlArags + setPythonParams + isK8sHealthProbe
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
     * 基于QPS部署pod水平扩缩容
     * 参考文档：https://imroc.cc/k8s/best-practice/custom-metrics-hpa
     */
    static def deployHPA(ctx, map) {
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) { // QPS扩缩容 只限于服务端集成Prometheus监控
            // 安装k8s-prometheus-adpater
            // Helm.installPrometheus(ctx)

            def yamlName = "hpa.yaml"
            ctx.sh "sed -e ' s#{APP_NAME}#${ctx.FULL_PROJECT_NAME}#g;s#{HOST_PORT}#${ctx.SHELL_HOST_PORT}#g; " +
                    " s#{APP_COMMON_NAME}#${ctx.FULL_PROJECT_NAME}#g; s#{K8S_POD_REPLICAS}#${ctx.K8S_POD_REPLICAS}#g; " +
                    " ' ${ctx.WORKSPACE}/ci/_k8s/${yamlName} > ${yamlName} "
            ctx.sh " cat ${yamlName} "

            // 部署pod水平扩缩容  如果已存在不重新创建
            ctx.sh "kubectl get hpa ${ctx.FULL_PROJECT_NAME}-hpa -n ${k8sNameSpace} || kubectl apply -f ${yamlName}"

            // 若安装正确，可用执行以下命令查询自定义指标 查看到 Custom Metrics API 返回配置的 QPS 相关指标 可能需要等待几分钟才能查询到
            // ctx.sh " kubectl get --raw /apis/custom.metrics.k8s.io/v1beta1 || true "
            // kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1/namespaces/${k8sNameSpace}/pods/*/http_server_requests_qps"

            // 并发测试ab（apache benchmark） CentOS环境 sudo yum -y install httpd-tools    Ubuntu环境 sudo apt-get update && sudo apt-get -y install apache2-utils
            // ab -c 100 -n 10000 -r http://120.92.49.178:8080/  // 并发数-c  总请求数-n  是否允许请求错误-r  总的请求数(n) = 次数 * 一次并发数(c)
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

        ctx.sh "kubectl apply -f ingress.yaml"
        ctx.sh "kubectl get ingress"
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
     * k8s方式实现蓝绿部署
     */
    static def blueGreenDeploy(ctx, map) {
        // 蓝绿发布是为新版本创建一个与老版本完全一致的生产环境，在不影响老版本的前提下，按照一定的规则把部分流量切换到新版本，
        // 当新版本试运行一段时间没有问题后，将用户的全量流量从老版本迁移至新版本。
        ctx.sh " "
    }

    /**
     * K8S验证部署是否成功
     */
    static def verifyDeployment(ctx) {
        // 前提开启 readinessProbe和livenessProbe 健康探测
        ctx.println("K8S集群所有Pod节点健康探测中, 请耐心等待... 🚀")
        def deploymentName = "${ctx.FULL_PROJECT_NAME}" // labels.app标签值
        def namespace = k8sNameSpace
        ctx.sleep 3 // 等待检测  需要等待容器镜像下载如Pending状态等  可以先判断容器下载完成后再执行下面的检测
        // 等待所有Pod达到Ready状态
        ctx.timeout(time: 12, unit: 'MINUTES') { // 设置超时时间
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
                        ctx.sleep 10 // 每隔多少秒检查一次
                    }
                    if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                        ctx.sleep 5 // 每隔多少秒检查一次
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
     * 镜像方式部署
     */
    static def deployByImage(ctx, imageName, deploymentName, port) {
        ctx.println("开始部署Kubernetes云原生应用")
        // 创建示例部署并在端口 上公开它
        ctx.sh "kubectl delete deployment ${deploymentName}"
        ctx.sh "kubectl delete service ${deploymentName}"
        ctx.sh "kubectl create deployment balanced  ${deploymentName} --image=${imageName}"
        ctx.sh "kubectl expose deployment balanced  ${deploymentName} --type=NodePort --port=${port} "
        // 获取服务
        ctx.sh "kubectl get services ${deploymentName} && kubectl get pod" // STATUS 为 Running
        // 启动服务
        ctx.sh "minikube service ${deploymentName}"
        // ctx.sh "kubectl port-forward service/${deploymentName} 8080:8080" // 使用 kubectl 转发端口  kubectl port-forward 不会返回
    }

    /**
     * 基于K8s内置的回滚功能
     */
    static def rollback(ctx) {
        // 回滚上一个版本
        ctx.sh " kubectl rollout undo deployment/deployment-name  -n ${k8sNameSpace} "
        // 回滚到指定版本
        ctx.sh " kubectl rollout undo deployment/deployment-name --revision=2 -n ${k8sNameSpace} "
    }

    /**
     * K8s健康探测
     */
    static def healthDetection(ctx) {
        // Pod通过两类探针来检查容器的健康状态。分别是ReadinessProbe（就绪探测） 和 LivenessProbe（存活探测）
        ctx.sh "kubectl get pod -l app=***  -o jsonpath=\"{.items[0].metadata.name} "  // kubectl获取新pod名称
        // yaml内容中包含初始化时间和启动完成时间  shell中自动解析所有内容, 建议yq进行实际的YAML解析
        ctx.sh "kubectl get pods podName*** -o yaml"
        ctx.sh "kubectl -n ${k8sNameSpace} get pods podName*** -o yaml | yq e '.items[].status.conditions[] | select('.type' == \"PodScheduled\" or '.type' == \"Ready\") | '.lastTransitionTime'' - | xargs -n 2 bash -c 'echo \$(( \$(date -d \"\$0\" \"+%s\") - \$(date -d \"\$1\" \"+%s\") ))' "
        ctx.sh "kubectl get pods podName**** -o custom-columns=NAME:.metadata.name,FINISHED:.metadata.creationTimestamp "
    }

    /**
     * 清除k8s集群无效镜像  删除无效镜像 减少磁盘占用
     */
    static def cleanDockerImages(ctx) {
        // kubelet容器自动GC垃圾回收  参考文档: https://kubernetes-docsy-staging.netlify.app/zh/docs/concepts/cluster-administration/kubelet-garbage-collection/
        // 默认Kubelet会在节点驱逐信号触发和Image对应的Filesystem空间不足的情况下删除冗余的镜像
        // node节点 cat /etc/kubernetes/kubelet 镜像占用磁盘空间的比例超过高水位（可以通过参数ImageGCHighThresholdPercent 进行配置），kubelet 就会清理不用的镜像
        // ctx.sh "whoami && docker version &&  docker rmi \$(docker image ls -f dangling=true -q) --no-prune || true"
        // 在机器上设置定时任务 保留多少天  如 docker image prune -a --force --filter "until=720h"
        // 因占用资源被K8S驱逐的pod   删除所有状态为Evicted的Pod
        ctx.sh "kubectl get pods --namespace default | grep Evicted | awk '{print \$1}' | xargs kubectl delete pod -n default\n"
    }

}
