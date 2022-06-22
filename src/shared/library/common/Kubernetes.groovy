package shared.library.common

import shared.library.Utils
import shared.library.GlobalVars


/**
 * @author 潘维吉
 * @date 2021/8/18 13:22
 * @email 406798106@qq.com
 * @description 部署Kubernetes云原生应用
 * Kubernetes 是一个开源系统，用于容器化应用的自动部署、扩缩和管理。它将构成应用的容器按逻辑单位进行分组以便于管理和发现。
 */
class Kubernetes implements Serializable {

    /**
     * 声明式执行k8s集群部署
     */
    static def deploy(ctx, map) {
        // 动态替换k8s yaml声明式部署文件
        setYamlConfig(ctx, map)

        // 多个k8s集群同时滚动循环部署
        "${map.k8s_credentials_ids}".trim().split(",").each { k8s_credentials_id ->
            // KUBECONFIG变量为k8s中kubectl命令的yaml配置授权访问文件内容 数据保存为jenkins的“Secret file”类型的凭据，用credentials方法从凭据中获取
            ctx.withCredentials([ctx.file(credentialsId: "${k8s_credentials_id}", variable: 'KUBECONFIG')]) {
                // 安装kubectl命令访问k8s集群: https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/
                // 下载集群的配置文件，复制到本地计算机的 $HOME/.kube/config（kubectl的默认路径）
                // 若您之前配置过KUBECONFIG环境变量，kubectl会优先加载KUBECONFIG环境变量，而不是$HOME/.kube/config，使用时请注意
                // ctx.println("k8s集群访问配置：${ctx.KUBECONFIG}")
                // ctx.sh "kubectl version"

                // 部署应用 指定命名空间--namespace=
                ctx.sh """ 
                    kubectl apply -f k8s.yaml
                    """
                // 查看个组件的状态
                ctx.sh """ 
                    kubectl get pod
                    kubectl get svc
                    kubectl get node
                    """
                // 部署service
                // ctx.sh "kubectl apply -f service.yaml"
                // 部署ingress
                // ctx.sh "kubectl apply -f ingress.yaml"

                // 删除服务
                // ctx.sh "kubectl delete -f k8s.yaml"
                // kubectl 停止删除pod 默认等待30秒  删除deployment 命令kubectl delete deployment  删除所有 kubectl delete pods --all  --force
                // kubectl delete pod podName
                // 查看详细信息   kubectl describe pod podName

                // 查看命名空间下pod在哪些node节点运行
                // ctx.sh "kubectl get pod -n default -o wide"
                // 查看node节点当前的节点资源占用情况
                // ctx.sh "kubectl top nodes"

                // K8S健康检查
                // healthDetection(ctx)

                // K8S运行容器方式使用Docker容器时 删除无效镜像 减少磁盘占用 移除所有没有容器使用的镜像 -a
                ctx.sh "whoami && docker version &&  docker images prune -a || true"
            }
        }
    }

    /**
     * 动态替换k8s yaml声明式部署文件
     */
    static def setYamlConfig(ctx, map) {
        def hostPort = "${ctx.SHELL_HOST_PORT}" // 宿主机端口
        def containerPort = "${ctx.SHELL_EXPOSE_PORT}" // 容器内端口

        // 判断是否存在扩展端口
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && ctx.SHELL_EXTEND_PORT != "") {
            containerPort = "${ctx.SHELL_EXTEND_PORT}"
            ctx.println("应用服务扩展端口: " + containerPort)
        }
        ctx.sh "sed -e 's#{IMAGE_URL}#${ctx.DOCKER_REPO_REGISTRY}/${ctx.DOCKER_REPO_NAMESPACE}/${ctx.dockerBuildImageName}#g;s#{IMAGE_TAG}#${Utils.getVersionNum(ctx)}#g;" +
                " s#{APP_NAME}#${ctx.PROJECT_NAME}#g;s#{SPRING_PROFILE}#${ctx.SHELL_ENV_MODE}#g; " +
                " s#{HOST_PORT}#${hostPort}#g;s#{CONTAINER_PORT}#${containerPort}#g; " +
                " s#{MEMORY_SIZE}#${map.docker_memory}#g;s#{K8S_POD_REPLICAS}#${ctx.K8S_POD_REPLICAS}#g; " +
                " s#{K8S_IMAGE_PULL_SECRETS}#${map.k8s_image_pull_secrets}#g; " +
                " ' ${ctx.WORKSPACE}/ci/_k8s/k8s.yaml > k8s.yaml "
        ctx.sh " cat k8s.yaml "
    }

    /**
     * 灰度发布
     */
    static def ingressDeploy(ctx, map) {
        ctx.sh "kubectl apply -f ingress.yaml"
        ctx.sh "kubectl get ingress"
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

}
