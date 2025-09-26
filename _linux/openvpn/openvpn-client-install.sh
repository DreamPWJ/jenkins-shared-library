#!/usr/bin/env bash
# Author: 潘维吉
# Description:  OpenVPN客户端初始化


# 安装openvpn
sudo apt-get install -y openvpn || true

sudo yum -y install openvpn  || true

# 查看版本号
openvpn --version

# 客户端配置证书信息  client.ovpn是服务端分配的  有权限问题执行 sudo openvpn client.ovpn
# 公网IP的openvpn服务端执行 ./openvpn-install.sh 即可 初始化和生成证书文件等
sudo openvpn --daemon --config /etc/openvpn/client.ovpn --log-append /var/log/openvpn.log

# 查看相关日志
tail -f /var/log/openvpn.log

# 关闭openvpn服务 kill -9 PID
ps -ef | grep openvpn