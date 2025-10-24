#!/bin/bash
#
# https://github.com/Nyr/openvpn-install
#
# Copyright (c) 2013 Nyr. Released under the MIT License.

# VPN证书在root目录下  .ovpn格式  直接给客户端导入即可
# 在服务端设置生成客户端ovpn证书的有效期  vim /etc/openvpn/server/easy-rsa/pki/vars 中 set_var EASYRSA_CERT_EXPIRE	3650  默认825
# 测试UDP服务连通性 如果UDP无效可选择TCP协议  nc -vuz 172.16.100.177 19271
# 查看服务器连接情况 cat /run/openvpn-server/status-server.log  查看进程  ps -ef | grep openvpn

# 执行 ./openvpn-install.sh 即可 初始化和生成证书文件等
wget https://git.io/vpn -O openvpn-install.sh && bash openvpn-install.sh

# WireGuard新网络 初始化和生成证书文件等
# wget https://git.io/wireguard -O wireguard-install.sh && bash wireguard-install.sh