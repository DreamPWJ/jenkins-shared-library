#!/usr/bin/env bash
# Author: 潘维吉
# 卸载Docker

yum list installed | grep docker

yum -y remove docker-ce.x86_64 docker-ce-cli.x86_64

rm -rf /var/lib/docker

# yum方式安装使用
#sudo yum remove docker \
#                  docker-client \
#                  docker-client-latest \
#                  docker-common \
#                  docker-latest \
#                  docker-latest-logrotate \
#                  docker-logrotate \
#                  docker-engine

# apt卸载方式
sudo apt-get remove docker-ce docker-ce-cli containerd.io
