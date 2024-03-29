#!/usr/bin/env bash
# Author: 潘维吉
# Description: Keepalived+Nginx高可用 Keepalived是以VRRP（Virtual Router Redundancy Protocol，虚拟路由冗余协议）协议

cat /etc/redhat-release || true

yum update -y  || true
sudo apt-get update -y || true

echo "安装keepalived"
yum  install -y keepalived
sudo apt-get install -y keepalived

# 先配置keepalived文件  再启动keepalived  注意查看ifconfig配置网卡名配置interface参数！！！
# vim /etc/keepalived/keepalived.conf

echo "启动keepalived"
systemctl enable keepalived   # 开机自启动
systemctl start keepalived

echo "查看keepalived状态"
systemctl status keepalived

echo "查看网卡及IP"
ip addr show | grep inet

# 卸载keepalived
# sudo yum -y remove keepalived
# sudo apt-get remove -y keepalived
# sudo apt-get remove --auto-remove -y keepalived