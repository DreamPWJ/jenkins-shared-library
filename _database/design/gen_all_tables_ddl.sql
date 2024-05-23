SELECT CONCAT('SHOW CREATE TABLE `', table_name, '`;')
#INTO OUTFILE '/tmp/show_create_tables.sql'
FROM information_schema.tables
WHERE table_schema = '你的数据库名';


SHOW CREATE TABLE `user`;

# 导出全库DDL  定时提交到Git版本控制  用于数据库版本控制
# mysql -u 用户名 -p 密码  你的数据库名 < /tmp/show_create_tables.sql > /tmp/gen_all_tables_ddl.sql
