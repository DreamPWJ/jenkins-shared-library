#!/usr/bin/env bash
# Author: 潘维吉
# Description:  设置Github的DNS 加速访问速度和解决访问失败等问题

echo "设置Github的DNS"
# [IPAddress](https://www.ipaddress.com/) 查询DNS对应IP
# 写入数据到文件输出重定向 双 >> 是追加 , 单 > 是覆盖
sudo cat <<EOF >>/etc/hosts
140.82.112.4 github.com
199.232.5.194 github.global.ssl.fastly.net
185.199.108.133 raw.githubusercontent.com
EOF

# 在终端在输以下指令刷新DNS
# sudo killall -HUP mDNSResponder;say DNS cache has been flushed
