package shared.library.common

import shared.library.Utils
import shared.library.GlobalVars
import shared.library.common.Docker
import shared.library.common.Helm


/**
 * @author 潘维吉
 * @date 2021/8/18 13:22
 * @email 406798106@qq.com
 * @description 部署Kubernetes云原生应用
 * Kubernetes 是一个开源系统，用于容器化应用的自动部署、自动容器编排、自动扩缩与自动修复等管理。它将构成应用的容器按逻辑单位进行分组以便于管理和发现。
 */
class Kubernetes implements Serializable {

    static def k8sYAMLFile = "k8s.yaml" // k8s集群应用部署yaml定义文件

    /**
     * 声明式执行k8s集群部署
     */
    static def deploy(ctx, map, deployNum = 0) {
        def k8sStartTime = new Date()
        // 动态替换k8s yaml声明式部署文件
        setYamlConfig(ctx, map, deployNum)

        // 多个K8s集群同时循环滚动部署
        "${map.k8s_credentials_ids}".trim().split(",").each { k8s_credentials_id ->
            // KUBECONFIG变量为k8s中kubectl命令的yaml配置授权访问文件内容 数据保存为Jenkins的“Secret file”类型的凭据，用credentials方法从凭据中获取
            ctx.withCredentials([ctx.file(credentialsId: "${k8s_credentials_id}", variable: 'KUBECONFIG')]) {
                // 安装kubectl命令访问k8s集群: https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/
                // 下载集群的配置文件，复制到本地计算机的 $HOME/.kube/config（kubectl的默认路径）
                // 若您之前配置过KUBECONFIG环境变量，kubectl会优先加载KUBECONFIG环境变量，而不是$HOME/.kube/config，使用时请注意
                // ctx.println("k8s集群访问配置：${ctx.KUBECONFIG}")
                // ctx.sh "kubectl version"

                // 部署应用 相同应用不同环境配置 需循环执行不同的镜像 指定命名空间--namespace=
                ctx.sh """ 
                    kubectl apply -f ${k8sYAMLFile}
                    """

                // 查看个组件的状态
                ctx.sh """ 
                    kubectl get pod
                    kubectl get svc
                    kubectl top nodes
                    """

                // 七层负载和灰度发布配置部署ingress
                // ingressNginxDeploy(ctx, map)

                // 部署pod水平扩缩容 基于QPS自动伸缩
                // deployHPA(ctx, map)

                // 删除服务
                // ctx.sh "kubectl delete -f ${k8sYAMLFile}"
                // kubectl 停止删除pod 默认等待30秒  删除deployment 命令kubectl delete deployment  删除所有 kubectl delete pods --all  --force
                // kubectl delete pod podName
                // 查看详细信息   kubectl describe pod podName

                // 查看命名空间下pod在哪些node节点运行
                // ctx.sh "kubectl get pod -n default -o wide"
                // 查看node节点当前的节点资源占用情况
                // ctx.sh "kubectl top nodes"

                // K8S健康检查
                // healthDetection(ctx)

                // K8S运行容器方式使用Docker容器时 删除无效镜像 减少磁盘占用
                // cleanDockerImages(ctx)

                ctx.println("K8S集群部署完成 ✅")
            }
        }
        ctx.healthCheckTimeDiff = Utils.getTimeDiff(k8sStartTime, new Date()) // 计算应用启动时间
    }

    /**
     * 动态替换k8s yaml声明式部署文件
     * 可自定义yaml部署文件 如存放在业务代码仓库 无需本次动态配置
     */
    static def setYamlConfig(ctx, map, deployNum = 0) {
        def hostPort = "${ctx.SHELL_HOST_PORT}" // 宿主机端口
        def containerPort = "${ctx.SHELL_EXPOSE_PORT}" // 容器内端口

        def imageTag = Docker.imageTag
        def k8sPodReplicas = "${ctx.K8S_POD_REPLICAS}"

        // 判断是否存在扩展端口
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && ctx.SHELL_EXTEND_PORT != "") {
            containerPort = "${ctx.SHELL_EXTEND_PORT}"
            ctx.println("应用服务扩展端口: " + containerPort)
        }
        // 判断是否存在NFS网络文件服务挂载信息
        def nfsHostPath = ""    // NFS宿主机文件路径
        def nfsServerPath = ""  // NFS服务器文件路径
        if ("${ctx.NFS_MOUNT_PATHS}".trim() != "") {
            nfsHostPath = "${ctx.NFS_MOUNT_PATHS}".split(",")[0]
            nfsServerPath = "${ctx.NFS_MOUNT_PATHS}".split(",")[1]
            if (deployNum != 0) { // k8s内相同应用不同容器镜像标签部署
                imageTag += Docker.imageNodeTag + deployNum
                k8sPodReplicas = Integer.parseInt(k8sPodReplicas) - 1 // 除主节点其它节点相同
            } else {
                k8sPodReplicas = 1  // 主节点只部署一个
            }
        }

        // 复杂参数动态组合配置yaml文件
        if ("${ctx.NFS_MOUNT_PATHS}".trim() != "") {
            ctx.dir("${ctx.env.WORKSPACE}/ci/_k8s") {
                def kubernetesFile = "kubernetes.yaml"
                def data = ctx.readFile(file: "${kubernetesFile}")
                def yamlData = ctx.readYaml text: data

                def containers0 = yamlData.spec.template.spec.containers[0] as ArrayList
                def volumeMounts0 = yamlData.spec.template.spec.containers[0].volumeMounts[0] as ArrayList
                def volumes0 = yamlData.spec.template.spec.volumes[0] as ArrayList

                /*  ctx.println(volumeMounts0.name[0])
                  ctx.println(volumes0.nfs[0].server)
                  ctx.println(containers0.env[0].name)
                  ctx.println(containers0.env[0])*/

                def nfsName = "nfs-storage"
                volumeMounts0.each { volumeMountsItem ->
                    volumeMountsItem.name = nfsName
                    volumeMountsItem.mountPath = nfsHostPath
                }
                volumes0.each { volumesItem ->
                    volumesItem.name = nfsName
                }

                volumes0.nfs[0].server = ctx.NFS_SERVER
                volumes0.nfs[0].path = nfsServerPath

                ctx.sh "rm -f ${kubernetesFile}"
                ctx.writeYaml file: "${kubernetesFile}", data: yamlData
                ctx.sh " cat ${kubernetesFile} "
            }
        }

        ctx.sh "sed -e 's#{IMAGE_URL}#${ctx.DOCKER_REPO_REGISTRY}/${ctx.DOCKER_REPO_NAMESPACE}/${ctx.dockerBuildImageName}#g;s#{IMAGE_TAG}#${imageTag}#g;" +
                " s#{APP_NAME}#${ctx.FULL_PROJECT_NAME}#g;s#{SPRING_PROFILE}#${ctx.SHELL_ENV_MODE}#g; " +
                " s#{HOST_PORT}#${hostPort}#g;s#{CONTAINER_PORT}#${containerPort}#g; " +
                " s#{MEMORY_SIZE}#${map.docker_memory}#g;s#{K8S_POD_REPLICAS}#${k8sPodReplicas}#g; " +
                " s#{K8S_IMAGE_PULL_SECRETS}#${map.k8s_image_pull_secrets}#g; " +
                " ' ${ctx.WORKSPACE}/ci/_k8s/${k8sYAMLFile} > ${k8sYAMLFile} "

        ctx.sh " cat ${k8sYAMLFile} "
    }

    /**
     * 基于QPS部署pod水平扩缩容
     * 参考文档：https://piotrminkowski.com/2020/11/05/spring-boot-autoscaling-on-kubernetes/
     * https://github.com/stefanprodan/k8s-prom-hpa
     */
    static def deployHPA(ctx, map) {
        // 安装k8s-prometheus-adpater
        Helm.installPrometheus(ctx)

        def yamlName = "hpa.yaml"
        ctx.sh "sed -e ' s#{APP_NAME}#${ctx.FULL_PROJECT_NAME}#g;s#{HOST_PORT}#${ctx.SHELL_HOST_PORT}#g; " +
                " ' ${ctx.WORKSPACE}/ci/_k8s/${yamlName} > ${yamlName} "
        ctx.sh " cat ${yamlName} "

        // 部署pod水平扩缩容
        ctx.sh "kubectl apply -f ${yamlName}"
        // 若安装正确，可用执行以下命令查询自定义指标 查看到 Custom Metrics API 返回配置的 QPS 相关指标  可能需要等待几分钟才能查询到
        // ctx.sh " kubectl get --raw /apis/custom.metrics.k8s.io/v1beta1 || true "
        // kubectl get --raw "/apis/custom.metrics.k8s.io/v1beta1/namespaces/default/pods/*/http_server_requests_seconds_count_sum"

        // 并发测试ab（apache benchmark） CentOS环境 sudo yum -y install httpd-tools    Ubuntu环境 sudo apt-get update && sudo apt-get -y install apache2-utils
        // ab -c 100 -n 10000 -r http://120.92.49.178:8080/  // 并发数-c  总请求数-n  是否允许请求错误-r  总的请求数(n) = 次数 * 一次并发数(c)
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
    }

    /**
     * 灰度发布
     * 参考文档: https://help.aliyun.com/document_detail/200941.html
     */
    static def ingressDeploy(ctx, map) {
        // 需要提供一下几个参数：
        // 灰度发布匹配的方式  1、 header  2、 cookie
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
        ctx.sh "kubectl delete deploy old-deployment-name"
        ctx.sh "kubectl delete svc new-service-name"
    }

    /**
     * k8s方式实现蓝绿部署
     */
    static def blueGreenDeploy(ctx, map) {
        // 蓝绿发布是为新版本创建一个与老版本完全一致的生产环境，在不影响老版本的前提下，按照一定的规则把部分流量切换到新版本，
        // 当新版本试运行一段时间没有问题后，将用户的全量流量从老版本迁移至新版本。
        ctx.sh ""
    }

    /**
     * 镜像方式部署
     */
    static def deployByImage(ctx, imageName, deploymentName, port) {
        ctx.println("开始部署Kubernetes云原生应用")
        // 创建示例部署并在端口 上公开它
        ctx.sh "kubectl delete deployment ${deploymentName}"
        ctx.sh "kubectl delete service ${deploymentName}"
        ctx.sh "kubectl create deployment balanced  ${deploymentName} --image=${imageName}" // 测试镜像: idoop/zentao:latest
        ctx.sh "kubectl expose deployment balanced  ${deploymentName} --type=NodePort --port=${port} "
        // 获取服务
        ctx.sh "kubectl get services ${deploymentName} && kubectl get pod" // STATUS 为 Running
        // 启动服务
        ctx.sh "minikube service ${deploymentName}"
        // ctx.sh "kubectl port-forward service/${deploymentName} 8080:8080" // 使用 kubectl 转发端口  kubectl port-forward 不会返回

    }

    /**
     * K8s健康探测
     */
    static def healthDetection(ctx) {
        // Pod通过两类探针来检查容器的健康状态。分别是LivenessProbe（存活探测）和 ReadinessProbe（就绪探测）
        ctx.sh ""
    }

    /**
     * 清除k8s集群无效镜像  删除无效镜像 减少磁盘占用
     */
    static def cleanDockerImages(ctx) {
        // kubelet容器自动GC垃圾回收  参考文档: https://kubernetes-docsy-staging.netlify.app/zh/docs/concepts/cluster-administration/kubelet-garbage-collection/
        // 默认Kubelet会在节点驱逐信号触发和Image对应的Filesystem空间不足的情况下删除冗余的镜像
        // node节点 cat /etc/kubernetes/kubelet 镜像占用磁盘空间的比例超过高水位（可以通过参数ImageGCHighThresholdPercent 进行配置），kubelet 就会清理不用的镜像
        // ctx.sh "whoami && docker version &&  docker rmi \$(docker image ls -f dangling=true -q) --no-prune || true"
    }

}
