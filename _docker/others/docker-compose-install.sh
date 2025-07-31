#!/usr/bin/env bash
# Author: 潘维吉
# Docker Compose编排初始化安装脚本 Docker Compose依靠Docker Engine进行任何有意义的工作，因此请安装Docker Engine
echo -e "\033[32mDocker Compose初始化安装  📥 \033[0m"

if [[ $(command -v docker compose) ]]; then
  echo -e "\033[34mDocker Compose版本： $(docker compose --version) ，已经初始化 退出安装 ✘ \033[0m"
  exit
fi

# 获取Docker Compose最新发布版的Tag
docker_compose_version=$(curl -s https://api.github.com/repos/docker/compose/releases/latest | grep 'tag_name' | cut -d\" -f4)

echo "使用curl方式安装Docker Compose ${docker_compose_version} (网络问题，可能会比较慢 )"
sudo curl -L "https://get.daocloud.io/docker/compose/releases/download/${docker_compose_version}/docker-compose-$(uname -s)-$(uname -m)" \
  -o /usr/local/bin/docker-compose # 将github.com切换成国内get.daocloud.io 加速下载

# 对二进制文件应用可执行权限
sudo chmod +x /usr/local/bin/docker-compose

echo "Docker Compose版本 验证安装是否成功"
docker compose version

echo -e "\033[32mDocker Compose安装完成 ✔ \033[0m"

# 启动编排后台服务：docker compose up -d
# 关闭删除编排服务：docker compose down

# docker compose ps 命令查看启动的服务
# docker compose build 重新构建自定义Dockerfile的镜像
# docker compose start 启动停止的服务
# docker compose stop 停止服务

# docker compose(docker) restart nginx    修改配置文件等重启服务生效
# docker compose build --no-cache service1 service2   不带缓存的构建
# docker compose logs -f nginx            查看nginx的实时日志

# docker compose migrate-to-labels  升级
# pip uninstall docker-compose  pip安装方式卸载
# sudo rm /usr/local/bin/docker-compose  curl安方式装卸载
