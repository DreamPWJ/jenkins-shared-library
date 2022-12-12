## 在Kubernetes上部署一套 Redis 集群

- 参考文章: https://juejin.cn/post/7042173689948798989 与  https://github.com/sobotklp/kubernetes-redis-cluster

### 创建所需资源

cat >redis.conf<<\EOF

EOF

- 创建ConfigMap
kubectl create configmap redis-conf --from-file=redis.conf
- 创建Service
kubectl apply -f redis-headless-service.yaml
- 创建StatefulSet
kubectl apply -f redis-stateful-set.yaml


### 扩缩容

- 扩容至5副本
kubectl scale statefulset redis  --replicas=5
- 缩容只2副本
kubectl scale statefulset redis  --replicas=2


### 清理
kubectl delete statefulset redis
kubectl delete configmap,service,pvc -l app=redis