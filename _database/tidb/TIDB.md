### TiDB分布式数据库

-  使用TiUP初始化部署TiDB集群步骤: https://docs.pingcap.com/zh/tidb/stable/production-deployment-using-tiup
-  执行部署TiDB集群(首先设置ssh免密登录) tiup list tidb 来查看 TiUP 支持的最新可用版本: 
   cd /my && tiup cluster deploy tidb-prod v7.4.0 ./topology.yaml --user root 
-  重复部署的情况， 注意数据库名称重复冲突

#### TiDB运维命令

- 启动TiDB集群  重启机器后需要执行start命令 不会自动重启tidb集群服务  复制数据备份  cp -r /tidb-data /root/tidb-data
tiup cluster start tidb-prod

- 停止TiDB集群
tiup cluster stop tidb-prod

- 查看TiDB集群
tiup cluster display tidb-prod

- 检测TiDB集群模版有效性
tiup cluster check /my/topology.yaml

#### TiUP在不中断线上服务的情况扩容缩容TiDB集群 https://docs.pingcap.com/zh/tidb/stable/scale-tidb-using-tiup

tiup cluster scale-out <cluster-name> scale-out.yml [-p] [-i /home/root/.ssh/gcp_rsa]

#### TiDB 备份与恢复

- 基于 Raft 协议和合理的部署拓扑规划，TiDB 实现了集群的高可用，当集群中少数节点挂掉时，集群依然能对外提供服务