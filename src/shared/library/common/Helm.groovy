package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2021/11/03 13:22
 * @email 406798106@qq.com
 * @description Helm是Kubernetes的包管理工具 类似Node的npm
 * 将K8s中的应用以及依赖服务以包(Chart)的形式组织管理
 */
class Helm implements Serializable {

    /**
     * 安装k8s-prometheus
     */
    static def installPrometheus(ctx) {
        // 安装前需要删除已经注册的 Custom Metrics API 每次更新完rule文件后，需要重启custom-metrics-apiserver服务才可以生效
        ctx.sh " kubectl delete apiservice v1beta1.custom.metrics.k8s.io || true "

        def mirrorSourceName = "stable"
        if (!Utils.getShEchoResult(ctx, "helm repo ls").contains(mirrorSourceName)) {
            // 使用镜像的adapter的
            // ctx.sh " helm repo add ${mirrorSourceName} https://prometheus-community.github.io/helm-charts "
            // ctx.sh " helm repo add aliyunhub https://aliacs-app-catalog.oss-cn-hangzhou.aliyuncs.com/charts-incubator/ "
            ctx.sh " helm repo add stable https://charts.helm.sh/stable "
            ctx.sh " helm repo update "
        }

        // Helm 3安装k8s-prometheus
        // ctx.sh " helm version "
        def namespace = "default"  // 命名空间 不同空间是隔离的
        ctx.sh " helm delete -n ${namespace} prometheus-adapter || true "
        // 安装k8s-prometheus
        // ctx.sh " helm install prometheus ${mirrorSourceName}/prometheus "
        // prometheus-adapter安装文档: https://artifacthub.io/packages/helm/prometheus-community/prometheus-adapter
        ctx.sh " helm install prometheus-adapter ${mirrorSourceName}/prometheus-adapter -f ${ctx.WORKSPACE}/ci/_k8s/prometheus/prometheus-adapter-values.yaml --namespace ${namespace} "

        // 若已安装 prometheus-operator，则可通过创建 ServiceMonitor 的 CRD 对象配置 Prometheus
        def yamlName = "prometheus-service-monitor.yaml"
        ctx.sh "sed -e ' s#{APP_NAME}#${ctx.FULL_PROJECT_NAME}#g; " +
                " ' ${ctx.WORKSPACE}/ci/_k8s/prometheus/${yamlName} > ${yamlName} "
        // ctx.sh " cat ${yamlName} "

        def monitorNamespace = "monitoring"  // 命名空间 不同空间是隔离
        // ctx.sh " kubectl create namespace ${monitorNamespace}"
        ctx.sh " helm install prometheus-operator --set rbacEnable=true ${mirrorSourceName}/prometheus-operator -n ${monitorNamespace} "
        ctx.sh " kubectl apply -f ${yamlName} "


        //ctx.sh " helm list -n ${namespace} "
        //ctx.sh " kubectl get pod -n ${namespace} "
        //ctx.sh " kubectl get apiservice "
    }

    /**
     * 下载包
     */
    static def install(ctx) {
        ctx.sh " helm version "
        ctx.sh " helm install "
    }

}
