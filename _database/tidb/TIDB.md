### TiDB分布式数据库

-  PD为总调度中心  TiDB整个项目分为两层，TiDB 作为SQL 层，采用Go 语言开发， TiKV 作为下边的分布式存储引擎，采用Rust 语言开发
-  使用TiUP初始化部署TiDB集群步骤: https://docs.pingcap.com/zh/tidb/stable/production-deployment-using-tiup
-  执行部署TiDB分布式集群(首先设置ssh免密登录) : 
   cd /my && tiup cluster deploy cluster-name v8.5.0 ./topology.yaml --user root 和 tiup cluster start cluster-name --init
-  重复部署的情况， 注意数据库名称重复冲突 重命名集群  tiup cluster rename old-name new-name
-  MYSQL迁移TiDB兼容性问题(TiDB不支持函数、存储过程、触发器等): https://docs.pingcap.com/zh/tidb/stable/mysql-compatibility

#### TiDB运维命令 https://docs.pingcap.com/zh/tidb/stable/maintain-tidb-using-tiup

- 查看TiDB集群
  tiup cluster display cluster-name

- 启动TiDB集群  重启机器后需要执行start命令 不会自动重启tidb集群服务  复制数据备份  cp -r /tidb-data /root/tidb-data
  tiup cluster start cluster-name

- 停止TiDB集群
  tiup cluster stop cluster-name

- 重新加载TiDB集群  滚动升级所有的tidb组件
  tiup cluster reload cluster-name -R tidb

- 重启TiDB集群或部分Node节点
  tiup cluster restart cluster-name
  tiup cluster restart cluster-name --node ip:port

- 检测TiDB集群模版有效性
  tiup cluster check /my/topology.yaml

- 修改配TiDB集群置参数 比如将日志 max-days默认不过期更改过期等
  tiup cluster edit-config cluster-name

- 销毁TiDB集群 ！！！
  tiup cluster destroy cluster-name

#### TiUP在不中断线上服务的情况扩容缩容TiDB集群 https://docs.pingcap.com/zh/tidb/stable/scale-tidb-using-tiup

tiup cluster scale-out <cluster-name> scale-out.yml [-p] [-i /home/root/.ssh/gcp_rsa]

#### TiDB 备份与恢复 https://docs.pingcap.com/zh/tidb/stable/backup-and-restore-overview

- 基于 Raft 协议和合理的部署拓扑规划，TiDB 实现了集群的高可用，当集群中少数节点挂掉时，集群依然能对外提供服务
- tiup br backup full 命令来备份集群快照到备份存储 例如在每天零点进行集群快照备份

#### 连接到 TiDB 控制台 用于查看和备份等

sudo apt-get install -y mysql-client

mysql --host 172.0.0.1 --port 4000 -u root -p 123456 --comments