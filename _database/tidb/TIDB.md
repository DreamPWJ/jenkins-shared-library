### TiDB分布式数据库

- 使用TiUP初始化部署TiDB集群步骤: https://docs.pingcap.com/zh/tidb/stable/production-deployment-using-tiup

- 参考文章: https://blog.csdn.net/weixin_40592911/article/details/126997741

-  执行部署TiDB集群(设置ssh免密登录): cd /my && tiup cluster deploy tidb-prod v6.5.2 ./topology.yaml --user root 
-  重复部署的情况， 注意数据库名称重复冲突

### TiDB运维命令
- 启动TiDB集群
tiup cluster start tidb-prod

- 停止TiDB集群
tiup cluster stop  tidb-prod

- 查看TiDB集群
tiup cluster display tidb-prod

- 检测TiDB集群模版有效性
tiup cluster check ./topology.yaml

### TiUP扩容缩容TiDB集群

tiup cluster scale-out <cluster-name> scale-out.yml [-p] [-i /home/root/.ssh/gcp_rsa]

### TiDB 备份与恢复

- 基于 Raft 协议和合理的部署拓扑规划，TiDB 实现了集群的高可用，当集群中少数节点挂掉时，集群依然能对外提供服务