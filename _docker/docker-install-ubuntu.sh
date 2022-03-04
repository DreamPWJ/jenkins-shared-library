#!/usr/bin/env bash
# Author: 潘维吉

echo -e "\033[32mUbuntu系统Docker初始化安装  📥 \033[0m"

if [[ $(command -v docker) ]]; then
  echo -e "\033[34mDocker版本： $(docker --version) ，已经初始化 退出安装  ✘ \033[0m"
  exit
fi

echo "查看linux内核或版本"
lsb_release -a

echo "更新包管理器 安装程序包 添加软件源信息"
sudo apt-get update -y || true
sudo apt-get install -y linux-image-generic-lts-xenial || true
#sudo apt-get install -y software-properties-common || true
sudo add-apt-repository "deb [arch=amd64] http://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" || true
# Ubuntu 20 出现The following signatures couldn't be verified because the public key is not available
# 执行 sudo apt-key adv --keyserver mirrors.aliyun.com --recv-keys 7EA0A9C3F273FCD8  将公钥添加到服务器
# sudo vim /etc/apt/sources.list 存储镜像源

# 非设置镜像情况安装Docker 网络原因可能比较慢或者失败
echo "安装Docker"
curl -s --connect-timeout 30 --retry 5 https://get.docker.com/ | sudo sh
#sudo apt-get install -y docker-ce docker-ce-cli containerd.io

echo "启动Docker并加入开机自启动"
sudo systemctl enable docker
sudo systemctl start docker

if [[ "$(whoami)" != "root" ]]; then
  echo "非root用户设置权限 将当前用户$(whoami)添加到docker组 用于与docker服务通信"
  sudo usermod -aG docker $(whoami)
fi

echo "设置国内镜像源 加速docker pull速度"
sudo cat <<EOF >/etc/docker/daemon.json
{
  "registry-mirrors": [
   "https://e6x18rmb.mirror.aliyuncs.com",
   "https://mirror.ccs.tencentyun.com",
   "http://registry.docker-cn.com",
   "http://docker.mirrors.ustc.edu.cn",
   "http://hub-mirror.c.163.com"
  ]
}
EOF
# 重启镜像源生效
sudo systemctl daemon-reload
sudo systemctl restart docker
echo "查看docker信息"
docker info

sleep 1
# docker version 验证安装是否成功(有client和service两部分表示docker安装启动都成功了)
echo "Docker版本 验证安装是否成功 "
docker version

echo -e "\033[32mDocker安装完成 ✔ \033[0m"
