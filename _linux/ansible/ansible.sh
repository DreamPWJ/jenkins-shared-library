#!/bin/bash
# Author: 潘维吉
# Description:  Ansible自动批量执行Linux命令 同时管理N台机器  在 sudo vim /etc/ansible/hosts
# 参考文章：https://blog.51cto.com/395469372/2133486

# 配置ssh免密码登录
# ssh-keygen -t rsa  #一直按回车即可
# ssh-copy-id -i .ssh/id_rsa.pub root@192.168.0.10 #上传公钥到服务器

# ansible语法
# ansible 主机组或者主机 -m 模块 -a 命令

yum install ansible -y || true
apt-get install ansible -y || true
apt-get install sshpass -y || true # 需要hosts设置用户名密码情况 如 ansible_ssh_user=root ansible_ssh_pass=123456

ansible --version

# 查看帮助命令  ansible-doc -l
ansible GROUP_NAME -m ping
ansible GROUP_NAME -m command -a "pwd"

# src指定本地的文件 dest指定远程主机的目录或者文件
ansible GROUP_NAME -m copy -a " src=/home/panweiji/test.zip dest=/home/orangepi/ "
ansible GROUP_NAME -m command -a " unzip -o -d /home/orangepi/test /home/orangepi/test.zip "
# ansible GROUP_NAME -m command -a " /usr/sbin/reboot "

# 把脚本下发到所有的服务器，然后再运行脚本。相当于copy+shell+delete
chmod a+x /usr/local/src/script
ansible testgroup -m script -a "/usr/local/src/script"