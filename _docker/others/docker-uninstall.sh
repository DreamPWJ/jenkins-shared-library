#!/usr/bin/env bash
# Author: 潘维吉
# 卸载Docker

# yum卸载方式
yum list installed | grep docker
yum -y remove docker-ce docker-ce-cli
rm -rf /var/lib/docker

# apt卸载方式
sudo apt-get autoremove docker docker-ce docker-engine docker.io containerd runc
dpkg -l | grep docker && sudo apt-get autoremove docker-ce-*
sudo rm -rf /etc/systemd/system/docker.service.d
sudo rm -rf /var/lib/docker
docker --version
