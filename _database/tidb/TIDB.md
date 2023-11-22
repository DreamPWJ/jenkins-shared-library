### TiDB分布式数据库

-  PD为总调度中心  TiDB整个项目分为两层，TiDB 作为SQL 层，采用Go 语言开发， TiKV 作为下边的分布式存储引擎，采用Rust 语言开发
-  使用TiUP初始化部署TiDB集群步骤: https://docs.pingcap.com/zh/tidb/stable/production-deployment-using-tiup
-  执行部署TiDB分布式集群(首先设置ssh免密登录) : 
   cd /my && tiup cluster deploy tidb-prod v7.1.2 ./topology.yaml --user root 和 tiup cluster start tidb-prod --init
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

- 销毁TiDB集群！！！
  tiup cluster destroy tidb-prod

#### TiUP在不中断线上服务的情况扩容缩容TiDB集群 https://docs.pingcap.com/zh/tidb/stable/scale-tidb-using-tiup

tiup cluster scale-out <cluster-name> scale-out.yml [-p] [-i /home/root/.ssh/gcp_rsa]

#### TiDB 备份与恢复

- 基于 Raft 协议和合理的部署拓扑规划，TiDB 实现了集群的高可用，当集群中少数节点挂掉时，集群依然能对外提供服务