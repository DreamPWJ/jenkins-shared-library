#### 创建所需资源

- 创建configMap
kubectl apply -f mysql-config-map.yaml
- 创建service
kubectl apply -f mysql-services.yaml
- 创建statefulSet
kubectl apply -f mysql-stateful-set.yaml


#### 扩缩容

- 扩容至6副本
kubectl scale statefulset mysql --replicas=6
- 缩容只2副本
kubectl scale statefulset mysql --replicas=2


#### 清理

kubectl delete statefulset mysql
kubectl delete configmap,service,pvc -l app=mysql