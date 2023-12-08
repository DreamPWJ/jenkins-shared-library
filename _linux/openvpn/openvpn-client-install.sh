#!/bin/bash
# Author: 潘维吉
# Description:  OpenVPN客户端初始化


# 安装openvpn
apt-get install -y openvpn

# 客户端配置证书信息  有权限问题执行  udo openvpn client.ovpn
sudo openvpn  --config /etc/openvpn/config/client.ovpn --daemon --log-append /var/log/openvpn.log