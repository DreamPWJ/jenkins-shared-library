#!/usr/bin/env bash
# Author: 潘维吉
# Description: Linux宿主机自动化安装MongoDB脚本
# MongoDB 是一个文档数据库，旨在简化开发和扩展  是基于分布式文件存储的数据库，由 C++语言编写，专为 WEB 应用提供可扩展性、高性能和高可用性的数据存储解决方案
# 官方安装文档: https://docs.mongodb.com/manual/tutorial/install-mongodb-on-red-hat/

echo "Red Hat或CentOS宿主机安装MongoDB"

echo "配置包管理系统"
sudo cat <<EOF >/etc/yum.repos.d/mongodb-org-5.0.repo
[mongodb-org-5.0]
name=MongoDB Repository
baseurl=https://mirrors.aliyun.com/mongodb/yum/redhat/8/mongodb-org/5.0/x86_64/
gpgcheck=1
enabled=1
gpgkey=https://www.mongodb.org/static/pgp/server-5.0.asc
EOF

# 检查系统最大可打开文件数  文档 https://docs.mongodb.com/manual/reference/ulimit/
# MongoDB 对 Linux 系统的最大可打开文件数也有要求，自 MongoDB4.4 版本开始，系统最大可打开文件数若在 64000 以下，启动将会报错
ulimit -n
# ulimit -n 65535  # 设置ulimit的值

echo "开始安装MongoDB"
# 安装最新稳定社区版的 MongoDB
sudo yum install -y mongodb-org

echo "启动MongoDB"
sudo systemctl daemon-reload
sudo systemctl enable mongod # 设置随系统启动
sudo systemctl start mongod

sleep 1s

echo "查看MongoDB状态"
which mongod
sudo systemctl status mongod

# 进入 MongoDB 命令行  MongoDB Shell 是 MongoDB 自带的交互式 Javascript shell，是用来对 MongoDB 进行操作和管理的交互式环境
# 执行 mongosh
# 后查看所有数据库 show dbs

# MongoDB 设置用户名密码
# use design # 创建design名称的数据库
# use admin
#db.createUser({
#  user: 'root',  // 用户名
#  pwd: '123456',  // 密码
#  roles:[{
#    role: 'root',  // 角色
#    db: 'admin'  // 数据库
#  }]
#})

# mongodb的27017端口telnet不通解决方法
# vim /etc/mongod.conf 中bindIp改成0.0.0.0  重启mongodb服务即可 如果启动失败看下方说明
# security:
#  authorization: enabled # 开启用户访问控制

# sudo systemctl restart mongod


# 如果启动失败错误  --config /etc/mongod.conf (code=exited, status=14) 解决方法如下
# chown -R mongodb:mongodb /var/lib/mongodb
# chown mongodb:mongodb /tmp/mongodb-27017.sock
# sudo service mongod restart
