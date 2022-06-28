package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/11/03 13:22
 * @email 406798106@qq.com
 * @description Helm是Kubernetes的包管理工具 类似Node的npm
 * 将K8s中的应用以及依赖服务以包(Chart)的形式组织管理
 */
class Helm implements Serializable {

    /**
     * 安装k8s-prometheus-adpater
     * 参考文档：https://cloud.tencent.com/document/product/457/61114
     */
    static def installPrometheus(ctx) {
        // 安装前需要删除已经注册的 Custom Metrics API
        ctx.sh " kubectl delete apiservice v1beta1.custom.metrics.k8s.io || true "
        // 使用镜像的adapter的
        // ctx.sh " helm repo add prometheus-community https://prometheus-community.github.io/helm-charts "
        // ctx.sh " helm repo update "
        // ctx.sh " helm repo ls "

        // Helm 3安装k8s-prometheus-adapter
        // ctx.sh " helm version "
        def namespace = "kube-system"
        ctx.sh " helm install prometheus-adapter stable/prometheus-adapter -f  ${ctx.WORKSPACE}/ci/_k8s/value.yaml --namespace ${namespace}  || true "
        //ctx.sh " helm list -n ${namespace} "
        //ctx.sh " kubectl get pod -n ${namespace} "
        //ctx.sh " kubectl get apiservice "
        // 若安装正确，可用执行以下命令查询自定义指标
        ctx.sh " kubectl get --raw /apis/custom.metrics.k8s.io/v1beta1 || true "
    }

    /**
     * 下载包
     */
    static def install(ctx) {
        ctx.sh " helm version"
    }

}
