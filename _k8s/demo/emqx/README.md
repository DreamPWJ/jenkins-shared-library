## K8s 上轻松搭建百万连接的 MQTT 服务

- 参考文章: https://docs.emqx.com/zh/emqx-operator/latest/getting-started/getting-started.html

### 创建所需资源 EMQX Operator
#### Kubernetes 版本1.25无需指定版本  在1.20-1.24版本指定 EMQX Operator为 --version 1.0.0

helm repo add ali-stable https://kubernetes.oss-cn-hangzhou.aliyuncs.com/charts
helm repo add emqx https://repos.emqx.io/charts
helm repo update
helm search repo emqx/emqx-operator --versions
helm upgrade --install emqx-operator emqx/emqx-operator --version 1.0.12  --namespace emqx-operator-system --create-namespace 
kubectl get pods -n emqx-operator-system -l "control-plane=controller-manager"

### 部署 EMQX

kubectl apply -f emqx-k8s.yaml

kubectl get emqx

### 扩缩容


### 清理
