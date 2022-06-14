###  DataX离线数据同步工具/平台 异构数据源之间高效的数据同步功能

#### 安装DataX
cd /my
wget http://datax-opensource.oss-cn-hangzhou.aliyuncs.com/datax.tar.gz
tar -xzvf datax.tar.gz

cd /my/datax/bin && python datax.py /my/datax/job/datax_job.json

#### Mysql查询哪些表数据量最大  ShardingSphere零侵入性的分库分表、读写分离、分布式事务

use information_schema;
select TABLE_SCHEMA, table_name, table_rows, ENGINE, DATA_LENGTH, MAX_DATA_LENGTH, DATA_FREE
from tables
where table_schema='database_name'
order by table_rows desc
limit 10;

### 查看 MySQL「所有库」的容量大小

SELECT
table_schema as '数据库',
sum(table_rows) as '记录数',
sum(truncate(data_length/1024/1024, 2)) as '数据容量(MB)',
sum(truncate(index_length/1024/1024, 2)) as '索引容量(MB)',
sum(truncate(DATA_FREE/1024/1024, 2)) as '碎片占用(MB)'
from information_schema.tables
group by table_schema
order by sum(data_length) desc, sum(index_length) desc;

### binlog回滚 按时间点进行恢复 主要参数 ［–start-datetime –stop-datetime］ 指定日期间隔内的所有日志

mysqlbinlog –start-datetime='2022-04-20 10:01:00' –stop-datetime='2022-04-20 9:59:59'
/usr/local/mysql/data/binlog.123456 | mysql -u root -p

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
