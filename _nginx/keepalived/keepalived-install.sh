#!/usr/bin/env bash
# Author: 潘维吉
# Description: Keepalived+Nginx高可用 Keepalived是以VRRP（Virtual Router Redundancy Protocol，虚拟路由冗余协议）协议

cat /etc/redhat-release

yum -y update

echo "安装keepalived"
yum -y install keepalived

# vim /etc/keepalived/keepalived.conf

echo "启动keepalived"
systemctl enable keepalived   # 开机自启动
systemctl start keepalived

echo "查看keepalived是否安装成功"
rpm -q -a keepalived

echo "查看网卡及IP"
ip addr show | grep inet

# 卸载keepalived
# sudo yum -y remove keepalived
