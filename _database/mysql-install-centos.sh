#!/usr/bin/env bash
# Author: 潘维吉
# Description: CentOS宿主机版mysql安装

echo "CentOS安装MySQL"

# 设置MySQL版本（可选：5.7 或 8.0）
MYSQL_VERSION="8.0"

echo " 添加MySQL Yum源"
#rpm -Uvh https://repo.mysql.com/mysql80-community-release-el7-9.noarch.rpm
# 国内镜像源
#sudo rpm -Uvh https://mirrors.tuna.tsinghua.edu.cn/mysql/repo/mysql80-community-release-el7-9.noarch.rpm
sudo rpm -Uvh https://mirrors.aliyun.com/mysql/repo/mysql80-community-release-el7-9.noarch.rpm

#  根据选择的版本启用对应的源
if [[ "$MYSQL_VERSION" == "5.7" ]]; then
    yum-config-manager --disable mysql80-community
    yum-config-manager --enable mysql57-community
elif [[ "$MYSQL_VERSION" == "8.0" ]]; then
    echo "Using MySQL 8.0 community repo"
else
    echo "Unsupported MySQL version: $MYSQL_VERSION"
    exit 1
fi

echo "更新yum命令"
yum clean all && yum makecache
sudo yum update -y || true

echo "安装MySQL服务器和客户端"
yum install -y mysql-community-server

echo "更新配置文件"
sudo cat <<EOF >/etc/mysql/my.cnf
[mysqld]
port=3306
bind-address=0.0.0.0
datadir=/var/lib/mysql
socket=/var/lib/mysql/mysql.sock
user=mysql

# 缓存表数据和索引的内存区域。通常应将其设置为系统可用内存的50%-80%，以减少磁盘I/O
# 临时设置 SET GLOBAL innodb_buffer_pool_size = 4 * 1024 * 1024 * 1024;
innodb_buffer_pool_size=4G

default-authentication-plugin=caching_sha2_password
skip-grant-tables # 免密 设置完密码后注释 重启mysql 否则端口会为0

group_concat_max_len=1024000000
max_connections=6000
max_connect_errors=2000
lower_case_table_names=1

[mysqld_safe]
log-error=/var/log/mysqld.log
pid-file=/var/run/mysqld/mysqld.pid
EOF

echo "启动服务"
systemctl start mysqld

echo "设置开机自启动"
systemctl enable mysqld
systemctl daemon-reload

echo "查看版本信息和状态"
mysql -V
systemctl status mysqld

#echo "修改账号密码并创建数据库"
# 进入MySQL shell  修改密码 新建数据库
# mysql -u root
# use mysql;

# install plugin validate_password soname 'validate_password.so';
# SHOW VARIABLES LIKE 'validate_password%';
# set global validate_password_policy=0;

# ALTER USER 'root'@'%' IDENTIFIED WITH caching_sha2_password BY 'panweiji2020!@#';
# CREATE database if NOT EXISTS panweiji;
# show databases;

#echo "新建远程用户"
# CREATE USER IF NOT EXISTS 'panweiji'@'%' IDENTIFIED WITH caching_sha2_password BY 'panweiji2020!@#';
# GRANT all privileges ON *.* TO 'panweiji'@'%';
# flush privileges;

# 处理端口问题
# show variables like 'port';
# show variables like 'skip_networking';

# 注意默认3306端口外部ip无法访问, 以下配置放开 !!!
# vim /etc/mysql/my.cnf  添加 [mysqld] 和 bind-address=0.0.0.0

# systemctl restart mysqld && systemctl status mysqld

# sudo tail -f /var/log/mysql/mysql.lo
