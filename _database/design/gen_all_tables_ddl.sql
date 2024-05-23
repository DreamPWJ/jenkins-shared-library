SELECT CONCAT('SHOW CREATE TABLE `', table_name, '`;')
#INTO OUTFILE '/tmp/show_create_tables.sql'
FROM information_schema.tables
WHERE table_schema = '你的数据库名';


SHOW CREATE TABLE `user`;

# 导出全库DDL  定时提交到Git版本控制  用于数据库版本控制
# mysql -u 用户名 -p 密码  你的数据库名 < /tmp/show_create_tables.sql > /tmp/gen_all_tables_ddl.sql


# 查询所有表
# SELECT GROUP_CONCAT('"',table_name SEPARATOR '", ') AS table_names FROM information_schema.tables  WHERE table_schema =  DATABASE();

# 查看表结构
# SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'design' AND TABLE_NAME = 'base_design_table' ;

# 查看表字段信息
# SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'design' AND TABLE_NAME = 'base_design_table' ORDER BY ORDINAL_POSITION
