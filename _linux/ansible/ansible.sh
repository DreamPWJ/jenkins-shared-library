#!/bin/bash
# Author: 潘维吉
# Description:  Ansible自动批量执行Linux命令 同时管理N台机器  在/etc/ansible/hosts
# 参考文章：https://blog.51cto.com/395469372/2133486

# ansible语法
# ansible 主机组或者主机 -m 模块 -a 命令

yum install ansible -y || true
apt-get install ansible -y || true