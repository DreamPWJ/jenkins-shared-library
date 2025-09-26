#!/usr/bin/env bash
# Author: 潘维吉
# Description: CentOS宿主机版MySql卸载

echo "数据库重新初始化"
systemctl stop mysqld
# 删除数据目录 (注意备份)
rm -rf /var/lib/mysql/*
# 重新初始化
mysqld --initialize --user=mysql --datadir=/var/lib/mysql
systemctl start mysqld


echo "检查是否存在mysql"
rpm -qa | grep mysql

echo "宿主机版mysql卸载..."
yum remove -y mysql

echo "查看残留mysql相关文件  rm -rf 删除"
find / -name mysql
rm -rf /var/lib/mysql /var/lib/mysql/mysql /usr/lib64/mysql


# Ubuntu版卸载MySql
sudo apt-get remove -y mysql-common
sudo apt-get autoremove -y mysql-server-8.0
dpkg -l |grep ^rc|awk '{print $2}' |sudo xargs dpkg -P # 清除残留数据
rm -rf /etc/mysql
