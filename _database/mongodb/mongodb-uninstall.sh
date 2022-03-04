#!/usr/bin/env bash
# Author: 潘维吉
# Description: 卸载 MongoDB 数据库

sudo service mongod stop
sudo apt-get purge -y mongodb-org* || true
sudo yum erase $(rpm -qa | grep mongodb-org) || true

# 要删除数据库和日志文件 （确保备份之前数据库数据！）
sudo rm -r /var/log/mongodb && sudo rm -r /var/lib/mongodb && sudo rm -r /var/lib/mongo || true

