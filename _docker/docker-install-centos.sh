#!/usr/bin/env bash
# Author: 潘维吉

echo -e "\033[32mCentOS系统Docker初始化安装  📥 \033[0m"
# chmod +x docker-install-centos.sh　给shell脚本执行文件可执行权限

if [[ $(command -v docker) ]]; then
  echo -e "\033[34mDocker版本： $(docker --version) ，已经初始化 退出安装  ✘ \033[0m"
  exit
fi

# uname -r 验证  Docker要求Linux系统的内核版本高于 3.10
echo "查看linux内核或版本"
lsb_release -a || cat /etc/redhat-release

echo "更新yum包到最新、安装Docker相关依赖、设置yum源"
# 设置yum源 https://download.docker.com/linux/centos/docker-ce.repo
#sudo yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
sudo yum update -y || true
# 安装需要的软件包， yum-util 提供yum-config-manager功能，另外两个是devicemapper驱动依赖的
sudo yum install -y yum-utils device-mapper-persistent-data lvm2

echo "安装Docker环境"
sudo yum makecache # 将服务器上的软件包信息 现在本地缓存,以提高 搜索 安装软件的速度
# sudo yum install -y docker-ce  # 对于老旧系统可手动执行命令安装
# sudo dnf -y install docker-ce --nobest # CentOS8 dnf新包方式
# sudo yum -y downgrade docker-ce-cli-19.03.8-3.el7 # 兼容 错误error response from daemon: client version 1.40 is too new. Maximum supported API version is 1.39
curl -s --connect-timeout 60 --retry 6 https://get.docker.com/ | sudo sh || sudo yum install -y docker-ce # curl方式下载docker

echo "启动Docker并加入开机自启动"
#sudo systemctl enable --now docker
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
  "https://docker.lanneng.tech",
  "https://em1sutsj.mirror.aliyuncs.com"
],
"log-driver":"json-file",
"log-opts": {
"max-size": "100m",
"max-file": "2"
}
}
EOF

# 让容器配置服务生效
sudo systemctl reload docker  # reload 不会重启 Docker 服务，但会使新的配置生效
#sudo systemctl daemon-reload && sudo systemctl restart docker

echo "查看docker信息"
docker info

sleep 1
# docker version 验证安装是否成功(有client和service两部分表示docker安装启动都成功了)
echo "Docker版本 验证安装是否成功 "
docker version

if [[ $(command -v docker) ]]; then
  echo -e "\033[32mDocker安装成功 ✔ \033[0m"
else
  echo -e "\033[31mDocker安装失败 ❌ \033[0m"
  exit 1
fi
