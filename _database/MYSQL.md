###  DataX离线数据同步工具/平台 异构数据源之间高效的数据同步功能

#### 安装DataX
cd /my
wget http://datax-opensource.oss-cn-hangzhou.aliyuncs.com/datax.tar.gz
tar -xzvf datax.tar.gz

cd  /my/datax/bin && python datax.py /my/datax/job/datax_job.json

#### Mysql查询哪些表数据量最大  ShardingSphere零侵入性的分库分表、读写分离、分布式事务
use information_schema;
select TABLE_SCHEMA, table_name, table_rows, ENGINE, DATA_LENGTH, MAX_DATA_LENGTH, DATA_FREE
from tables
where table_schema='database_name'
order by table_rows desc
limit 10;

#### 处理大量正在提交的事务导致CPU和内存彪满100%或者不可访问
show processlist;

#### 查看事务
select * from information_schema.INNODB_TRX;

#### 查询组装kill事务sql
select concat('KILL ', id, ';')
from information_schema.processlist p
inner join information_schema.INNODB_TRX x on p.id = x.trx_mysql_thread_id
where db = 'anjia';

批量KILL执行正在提交的事务
DataGrip批量导出CSV格式数据批量执行kill
