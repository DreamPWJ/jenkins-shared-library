#!/usr/bin/env bash
# Author: 潘维吉
# Description: 宿主机版mysql安装

echo "Ubuntu安装MySQL8.0"

echo "更新apt命令"
sudo apt update
sudo apt-get update

echo "安装MySQL"
sudo apt-get install -y mysql-server

echo "更新配置文件"
sudo cat <<EOF >/etc/mysql/my.cnf
[mysqld]
port=3306
bind-address=0.0.0.0
pid-file=/var/run/mysqld/mysqld.pid
socket=/var/run/mysqld/mysqld.sock
datadir=/var/lib/mysql
secure-file-priv=NULL
user=mysql

default-authentication-plugin=mysql_native_password
skip-grant-tables # 免密 设置完密码后注释 重启mysql 否则端口会为0

group_concat_max_len=1024000000
max_connections=2000
lower_case_table_names=1

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

#echo "修改账号密码并创建数据库"
# 进入MySQL shell  修改密码 新建数据库
# mysql -u root
# use mysql;

# install plugin validate_password soname 'validate_password.so';
# SHOW VARIABLES LIKE 'validate_password%';
# set global validate_password_policy=0;

# ALTER USER 'root'@'localhost' IDENTIFIED WITH MYSQL_NATIVE_PASSWORD BY 'panweiji2021';
# CREATE database if NOT EXISTS property_sales_dev;
# show databases;

# echo "新建远程用户"
# CREATE USER IF NOT EXISTS 'property_sales'@'%' IDENTIFIED WITH MYSQL_NATIVE_PASSWORD BY 'panweiji2021';
# GRANT all privileges ON *.* TO 'property_sales'@'%';
# flush privileges;

# 处理端口问题
# show variables like 'port';
# show variables like 'skip_networking';

# 注意默认3306端口外部ip无法访问, 以下配置放开 !!!
# vim /etc/mysql/my.cnf  添加 [mysqld] 和 bind-address=0.0.0.0

# systemctl restart mysql.service && systemctl status mysql.service

# sudo tail -f /var/log/mysql/error.log

# sudo apt reinstall mysql-server
