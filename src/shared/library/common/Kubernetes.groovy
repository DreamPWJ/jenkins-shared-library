package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/8/18 13:22
 * @email 406798106@qq.com
 * @description 部署Kubernetes云原生应用
 * Kubernetes 是一个开源系统，用于容器化应用的自动部署、扩缩和管理。它将构成应用的容器按逻辑单位进行分组以便于管理和发现。
 * 参考文档: https://minikube.sigs.k8s.io/docs/start/
 */
class Kubernetes implements Serializable {

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
        // kubectl 停止删除pod 先删除deployment 命令kubectl delete deployment  删除所有 kubectl delete pods --all  --force
        // kubectl delete pod podName
        // 查看详细信息   kubectl describe pod podName
    }

    /**
     * 设置yaml配置
     */
    static def setYamlConfig(ctx) {
        ctx.sh "sed -i 's/<BUILD_TAG>/${ctx.dockerBuildTag}/' k8s.yaml"
        ctx.sh "sed -i 's/<BRANCH_NAME>/${ctx.env.BRANCH_NAME}/' k8s.yaml"
    }

    /**
     * 声明式执行部署
     */
    static def deploy(ctx) {
        ctx.sh "kubectl apply -f k8s.yaml"
    }

    /**
     * K8s健康探测
     */
    static def healthDetection(ctx) {
        ctx.sh ""
    }

}
