#!/usr/bin/env bash
# Author: 潘维吉
# Description: Ubuntu宿主机版mysql安装

echo "Ubuntu安装MySQL8.0"

echo "更新apt命令"
sudo apt update
sudo apt-get update

echo "安装MySQL"
sudo apt-get install -y mysql-server

echo "更新主配置文件"
sudo cat <<EOF >/etc/mysql/my.cnf
[mysqld]
port=3306
bind-address=0.0.0.0
datadir=/var/lib/mysql
user=mysql

# 主从复制-主机配置
# 主服务器唯一ID
server-id=1
# 启用二进制日志
log-bin=mysql-bin

default-authentication-plugin=caching_sha2_password
#skip-grant-tables # 免密 设置完密码后注释 重启mysql 否则端口会为0

group_concat_max_len=1024000000
max_connections=2000
#lower_case_table_names=1

[mysqld_safe]
log-error=/var/log/mysqld.log
pid-file=/var/run/mysqld/mysqld.pid
EOF

echo "更新从配置文件"
sudo cat <<EOF >/etc/mysql/my.cnf
[mysqld]
port=3306
bind-address=0.0.0.0
datadir=/var/lib/mysql
user=mysql

# 主从复制-从机配置
# 从服务器唯一ID
server-id=2
# 开启二进制日志功能，以备Slave作为的Master时使用
log-bin=mysql-slave-bin
# 启用中继日志
relay-log=mysql-relay

default-authentication-plugin=caching_sha2_password
#skip-grant-tables # 免密 设置完密码后注释 重启mysql 否则端口会为0

group_concat_max_len=1024000000
max_connections=2000
#lower_case_table_names=1

[mysqld_safe]
log-error=/var/log/mysqld.log
pid-file=/var/run/mysqld/mysqld.pid

EOF

echo "启动服务"
sudo systemctl start mysql.service

echo "设置开机自启动"
sudo systemctl enable mysql.service
sudo systemctl daemon-reload

echo "查看版本信息和状态"
mysql --version
systemctl status mysql.service

# MySQL服务器应该自动启动。您可以在终端提示符后运行以下命令来检查 MySQL 服务器是否正在运行
sudo netstat -tap | grep mysql
lsof -i:3306

#echo "修改账号密码并创建数据库"
# 进入MySQL shell  修改密码 新建数据库
# mysql -u root
# use mysql;

# install plugin validate_password soname 'validate_password.so';
# SHOW VARIABLES LIKE 'validate_password%';
# set global validate_password_policy=0;

# ALTER USER 'root'@'%' IDENTIFIED WITH MYSQL_NATIVE_PASSWORD BY 'panweiji2021';
# CREATE database if NOT EXISTS database_name_test;
# show databases;

# echo "新建远程用户"
# CREATE USER IF NOT EXISTS 'user_name'@'%' IDENTIFIED WITH MYSQL_NATIVE_PASSWORD BY 'panweiji2021';
# GRANT all privileges ON *.* TO 'user_name'@'%';
# flush privileges;

# 处理端口问题
# show variables like 'port';
# show variables like 'skip_networking';

# 注意默认3306端口外部ip无法访问, 以下配置放开 !!!
# vim /etc/mysql/my.cnf  添加 [mysqld] 和 bind-address=0.0.0.0

# systemctl restart mysql.service && systemctl status mysql.service

# sudo tail -f /var/log/mysql/error.log
# sudo mkdir -p /var/log/mysql/ && sudo chown mysql:mysql -R /var/log/mysql

# sudo apt reinstall mysql-server

# 从库执行命令 做主从复制  先在主库节点上执行 show master status\G   获取下面master_log_file和master_log_pos参数信息
# change master to master_host ='172.16.100.185',master_port =3306,master_user ='root',master_password ='password',master_log_file ='mysql-bin.000001',master_log_pos =156;
# 开始同步
# start slave;
# 查询Slave状态
# show slave status\G