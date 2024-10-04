#!/usr/bin/env bash
# Author: 潘维吉
# 清理Docker日志
# 获取占用磁盘最高的目录列表  如 /* 根目录命令:  du -hsx /* | sort -hr | head -n 5
# 隐藏占用情况 查找进程没有关闭导致内核无法回收占用空间隐藏删除的文件:  lsof -n | grep deleted  执行释放 kill -9 PID

echo "======== 开始自动清理Docker日志 ========"

TOTAL_FREE=$(df -h  / | awk '/\// {print $4}' | sed 's/G//')
echo " Free space is $TOTAL_FREE GB! "

sudo sh -c "truncate -s 0 /var/lib/docker/containers/*/*-json.log"
rm -rf /my/**/log* && rm -f /my/**/*.log || true
rm -f /var/log/nginx/*.log || true
rm -f /usr/local/nginx/logs/*.log || true
rm -f /var/lib/docker/overlay2/*/diff/var/log/nginx/*.log || true

# 清除所有未使用或悬挂的图像 容器 卷和网络
docker system prune -a --force || true

# 移除所有未使用的镜像（包括没有被任何容器使用的镜像） 如/var/lib/docker/overlay2占用
docker image prune -a --force || true
# 移除所有未使用的卷
docker volume prune --force  || true
# 移除 Docker 构建缓存
docker builder prune --force  || true

AFTER_TOTAL_FREE=$(df -h  / | awk '/\// {print $4}' | sed 's/G//')
echo " After clean free space is $AFTER_TOTAL_FREE GB! "



# crontab -e
# 0 2 * * *  /bin/bash /my/docker-logs-clean.sh
# service crond restart , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出
