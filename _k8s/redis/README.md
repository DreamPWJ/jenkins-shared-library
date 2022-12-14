## 在Kubernetes上部署一套 Redis 集群

- 参考文章: https://github.com/sobotklp/kubernetes-redis-cluster

### 创建所需资源

cat >redis.conf<<\EOF

EOF

- 创建Service
kubectl apply -f redis-cluster.yaml
- 创建ConfigMap
kubectl create configmap redis-conf --from-file=redis.conf


### 扩缩容

- 扩容至6副本
kubectl scale -n default statefulset redis-cluster --replicas=6
- 缩容只2副本
kubectl scale -n default statefulset redis-cluster --replicas=2


### 清理
kubectl delete statefulset redis-cluster
kubectl delete configmap,service,pvc -l app=redis-cluster