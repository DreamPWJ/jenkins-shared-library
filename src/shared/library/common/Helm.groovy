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
     * 参考文档：https://intl.cloud.tencent.com/zh/document/product/457/38941
     *         https://atbug.com/kubernetes-pod-autoscale-on-prometheus-metrics/
     *         https://www.cnblogs.com/LiuChang-blog/p/15410541.html
     */
    static def installPrometheus(ctx) {
        // 安装前需要删除已经注册的 Custom Metrics API
        ctx.sh " kubectl delete apiservice v1beta1.custom.metrics.k8s.io || true "
        // 使用镜像的adapter的
        ctx.sh " helm repo add aliyunhub https://aliacs-app-catalog.oss-cn-hangzhou.aliyuncs.com/charts-incubator/ "
        // ctx.sh " helm repo update "
        // ctx.sh " helm repo ls "

        // Helm 3安装k8s-prometheus-adapter
        // ctx.sh " helm version "
        def namespace = "default"  // 命名空间 不同空间是隔离的
        ctx.sh " helm delete -n ${namespace} prometheus-adapter || true "
        // 安装文档: https://artifacthub.io/packages/helm/prometheus-community/prometheus-adapter
        ctx.sh " helm install prometheus-adapter stable/prometheus-adapter -f ${ctx.WORKSPACE}/ci/_k8s/prometheus/value-adapter.yaml --namespace ${namespace}  || true "
        //ctx.sh " helm install ack-prometheus-operator aliyunhub/ack-prometheus-operator -n ${namespace}  || true "
        //ctx.sh " helm list -n ${namespace} "
        //ctx.sh " kubectl get pod -n ${namespace} "
        //ctx.sh " kubectl get apiservice "
    }

    /**
     * 下载包
     */
    static def install(ctx) {
        ctx.sh " helm version "
    }

}
