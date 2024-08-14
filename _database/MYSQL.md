#### MYSQL一主一从复制和双主双从配置  简单的使用主备配置方式即可

- 文档地址: https://www.cnblogs.com/cao-lei/p/13603043.html

#### 慢SQL优化最佳实战

- 文档地址: https://help.aliyun.com/document_detail/290038.html

#### 开启MySql的binlog日志   Galera可以做数据库多主集群配置(需要MySQL Galera集群版本)

- 查看 show variables like '%log_bin%'; 设置在my.inf主文件配置 log_bin=on; 后重启mysql
- https://developer.aliyun.com/article/400581

#### Mysql查询哪些表数据量最大  ShardingSphere零侵入性的分库分表、读写分离、分布式事务

use information_schema;
select TABLE_SCHEMA, table_name, table_rows, ENGINE, DATA_LENGTH, MAX_DATA_LENGTH, DATA_FREE
from tables
where table_schema='database_name'
order by table_rows desc
limit 10;

#### 查看 MySQL「所有库」的容量大小
select
    table_schema as '数据库',
    sum(table_rows) as '记录数',
    sum(truncate(data_length/1024/1024/1024, 2)) as '数据容量(GB)',
    sum(truncate(index_length/1024/1024/1024, 2)) as '索引容量(GB)',
    sum(truncate(DATA_FREE/1024/1024/1024, 2)) as '碎片占用(GB)'
from information_schema.tables
group by table_schema
order by sum(data_length) desc, sum(index_length) desc;

#### 处理大量正在提交的事务导致CPU和内存彪满100%或者不可访问

show processlist;

#### 查看事务

select * from information_schema.INNODB_TRX;

#### 查询组装kill事务sql

select concat('KILL ', id, ';')
from information_schema.processlist p
inner join information_schema.INNODB_TRX x on p.id = x.trx_mysql_thread_id
where db = 'panweiji';

批量KILL执行正在提交的事务
DataGrip批量导出CSV格式数据批量执行kill
查看MySql运行日志:  sudo grep mysql /var/log/syslog

#### MySql压力性能测试

执行一次测试，分别50和100个并发，执行1000次总查询  mysqlslap是版本高于5.1的mysql自带的工具
mysqlslap -a --concurrency=50,100 --number-of-queries 1000  -uroot -p123456

#### MySQL通过binlog日志恢复数据

- show variables like '%log_bin%';  查看binlog是否开启 通常是my.cnf或my.ini设置log-bin=mysql-bin
- lock tables 表名 read;  锁表 防止数据被污染  根据需求选择 不阻塞业务情况    
- show master status;  查询binlog最新日志
- show binlog events in '最新日志文件'  FROM pos LIMIT 0, 10;  查看binlog日志和pos位置  可根据表名等关键字搜索到pos位置的日志
- 下载 binlog日志到本地 一般在 /var/lib/mysql/binlog.000001
- 在Window上 先进入 D:\Program Files\MySQL\MySQL Server 8.1\bin 目录 再Powershell 输入  .\mysqlbinlog.exe
- 根据pos位置恢复 BEGIN开始  COMMIT的结束位置  生成可以恢复的sql文件 查看数据使用--base64-output=decode-rows -v 查看设置好编码  datagrip 直接run sql script执行 recovery.sql
- mysqlbinlog --no-defaults --skip-gtids=true --start-position='起始pos' --stop-position='结束end_log_pos' local_log_bin_file_path > recovery.sql ; 
- sudo docker exec mysql mysqlbinlog –start-datetime='2022-04-20 10:01:00' –stop-datetime='2022-04-20 10:05:59' /path/to/mysql-bin.000001 | mysql -u username -p ;  根据时间段恢复

####  MySQL书写顺序: select... from... join... on... where.... group by... having... order by... limit [offset,] (rows)
####  MySQL执行顺序: from...  on... join... where...group by... having.... select ... order by... limit