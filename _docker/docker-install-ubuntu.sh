#!/usr/bin/env bash
# Author: 潘维吉

echo -e "\033[32mUbuntu系统Docker初始化安装  📥 \033[0m"
# chmod +x docker-install-ubuntu.sh　给shell脚本执行文件可执行权限

if [[ $(command -v docker) ]]; then
  echo -e "\033[34mDocker版本： $(docker --version) ，已经初始化 退出安装  ✘ \033[0m"
  exit
fi

# uname -r 验证  Docker要求Linux系统的内核版本高于3.10
echo "查看linux内核或版本"
lsb_release -a

echo "更新包管理器 安装程序包 添加软件源信息"
#sudo add-apt-repository "deb [arch=amd64] http://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" || tru
sudo apt-get update -y || true
sudo apt-get upgrade -y || true

# Ubuntu 20以后 出现The following signatures couldn't be verified because the public key is not available: NO_PUBKEY
# 执行 sudo apt-key adv --keyserver  hkp://keyserver.ubuntu.com:80 --recv-keys 7EA0A9C3F273FCD8  将公钥添加到服务器
# sudo vim /etc/apt/sources.list 存储镜像源
if [[ $(lsb_release -r --short | sed "s/\..*//g") -ge 20 ]]; then
  sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 7EA0A9C3F273FCD8 || true
fi
sudo apt-get install -y software-properties-common || true
sudo apt-get install -y linux-image-generic-lts-xenial || true

# 非设置镜像情况安装Docker 网络原因可能比较慢或者失败
echo "安装Docker"
if [[ $(command -v curl) ]]; then
  curl -s --connect-timeout 60 --retry 6 https://get.docker.com/ | sudo sh
else
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io
fi

echo "启动Docker并加入开机自启动"
sudo systemctl enable docker
sudo systemctl start docker

if [[ "$(whoami)" != "root" ]]; then
  echo "非root用户设置权限 将当前用户$(whoami)添加到docker组 用于与docker服务通信"
  sudo usermod -aG docker $(whoami)
  # 非root用户可能需要在安装完成后重启服务器 Docker服务引擎生效
fi

echo "设置国内镜像源 加速docker pull速度"
sudo cat <<EOF >/etc/docker/daemon.json
{
  "registry-mirrors": [
   "https://registry.docker-cn.com"
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

if [[ $(command -v docker) ]]; then
  echo -e "\033[32mDocker安装成功 ✔ \033[0m"
else
  # 第一次安装失败可再重试
  echo -e "\033[31mDocker安装失败 ❌ \033[0m"
  exit 1
fi
