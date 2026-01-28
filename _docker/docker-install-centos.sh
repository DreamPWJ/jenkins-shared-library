#!/usr/bin/env bash
# Author: 潘维吉

echo -e "\033[32mCentOS系统Docker初始化安装  📥 \033[0m"
# chmod +x docker-install-centos.sh　给shell脚本执行文件可执行权限

if [[ $(command -v docker) ]]; then
  echo -e "\033[34mDocker版本： $(docker --version) ，已经初始化 退出安装  ✘ \033[0m"
  exit
fi

# 检查是否为root
if [ "$(id -u)" -ne 0 ]; then
  echo "请使用 root 或 sudo 运行此脚本"
  exit 1
fi

#echo "[INFO] 检测系统版本..."
OS=""
VERSION_ID=""

if [ -f /etc/os-release ]; then
  . /etc/os-release
  OS=$ID
  VERSION_ID=${VERSION_ID%%.*}  # 取主版本号
else
  echo "[ERROR] 无法识别操作系统版本，脚本退出"
  exit 1
fi
echo "[INFO] 检测到系统为: $OS $VERSION_ID"

# uname -r 验证  Docker要求Linux系统的内核版本高于 3.10
echo "查看linux内核或版本"
lsb_release -a || cat /etc/redhat-release

echo "更新yum系统包到最新、安装Docker相关依赖、设置yum镜像源"
DOCKER_REPO="https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo" # 国内镜像源地址（阿里云）
# 设置yum源 https://download.docker.com/linux/centos/docker-ce.repo 或者直接将docker-ce.repo文件放在/etc/yum.repos.d目录下
sudo curl -o /etc/yum.repos.d/CentOS-Base.repo http://mirrors.aliyun.com/repo/Centos-7.repo
# sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

# 升级centos系统最新小版本和依赖
sudo yum clean all || true
sudo yum update -y || true

echo "安装Docker环境"
sudo yum makecache # 将服务器上的软件包信息 现在本地缓存,以提高 搜索 安装软件的速度
# 统一处理 CentOS / RHEL / Rocky / AlmaLinux
if [[ "$OS" =~ ^(centos|rhel|rocky|almalinux)$ ]]; then
  if [[ "$VERSION_ID" -ge 8 ]]; then
    echo "[INFO] 使用 dnf 安装 docker-ce"
    sudo dnf -y install dnf-plugins-core
    sudo dnf config-manager --add-repo $DOCKER_REPO
    sudo dnf -y install docker-ce --nobest
  else
    echo "[INFO] 使用 yum 安装 docker-ce"
    sudo yum install -y yum-utils device-mapper-persistent-data lvm2
    sudo yum-config-manager --add-repo $DOCKER_REPO # 设置镜像源
    # 查看系统可用版本 yum list docker-ce --showduplicates | sort -r
    sudo yum install -y docker-ce  # 指定版本号如 18.06.3.ce-3.el7 或 docker-ce-26.0.*, 按需排除 --exclude=docker-compose-plugin
  fi
else
  echo "[WARN] 非 RHEL/CentOS 系，尝试 get.docker.com 安装"
  curl -s --connect-timeout 60 --retry 6 https://get.docker.com/ | sh || sudo yum install -y docker-ce
fi

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
  "https://docker.m.daocloud.io",
  "https://docker.1ms.run",
  "https://docker.xuanyuan.me",
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
