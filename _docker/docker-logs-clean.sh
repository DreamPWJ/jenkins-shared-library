#!/usr/bin/env bash
# Author: 潘维吉
# 清理Docker日志
# 获取占用磁盘最高的目录列表  如 /* 根目录命令 * 当前目录 :  du -hsx * | sort -hr | head -n 5
# 隐藏占用情况 查找进程没有关闭导致内核无法回收占用空间的隐藏要删除的文件:
# lsof -w | grep deleted  执行释放 kill -9 PID  或一条命令执行 lsof -w | grep 'deleted' | awk '{print $2}' | xargs kill -9

echo "======== 开始自动清理Docker日志 ========"

TOTAL_FREE=$(df -h  / | awk '/\// {print $4}' | sed 's/G//')
echo " Free space is $TOTAL_FREE GB! "

sudo sh -c "truncate -s 0 /var/lib/docker/containers/*/*-json.log" || true
rm -rf /my/**/log* && rm -f /my/**/*.log || true
# 删除所有 .log 文件
find /my -type f -name "*.log" -exec rm -f {} + || true
# 删除所有 log* 的目录
find /my -type d -name "log*" -exec rm -rf {} + || true
rm -f /var/log/nginx/*.log || true
rm -f /usr/local/nginx/logs/*.log || true
rm -f /var/lib/docker/overlay2/*/diff/var/log/nginx/*.log || true
rm -f /var/lib/docker/overlay2/*/diff/etc/nginx/on || true
# 隐藏占用情况 查找进程没有关闭导致内核无法回收占用空间的隐藏要删除的文件
lsof -w | grep 'deleted' | awk '{print $2}' | xargs kill -9  || true

# 移除 Docker 构建缓存  CI/CD服务器或服务端构建镜像显著有效
docker builder prune --force  || true

# 清除所有未使用或悬挂的图像 容器 卷和网络
docker system prune -a --force || true

# 移除所有未使用的镜像（包括没有被任何容器使用的镜像） 如/var/lib/docker/overlay2占用  注意将删除所有images镜像
docker image prune -a --force || true
# 移除所有未使用的卷
docker volume prune --force  || true

AFTER_TOTAL_FREE=$(df -h  / | awk '/\// {print $4}' | sed 's/G//')
echo " After clean free space is $AFTER_TOTAL_FREE GB! "



# crontab -e
# 0 2 * * *  /bin/bash /my/docker-logs-clean.sh
# service crond restart , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出
