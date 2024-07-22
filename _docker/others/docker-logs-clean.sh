#!/usr/bin/env bash
# Author: 潘维吉
# 清理Docker日志
# 获取占用磁盘最高的目录列表  如 /* 根目录命令:  du -hsx /* | sort -hr | head -n 5

echo "======== 开始自动清理Docker日志 ========"

TOTAL_FREE=$(df -h  / | awk '/\// {print $4}' | sed 's/G//')
echo " Free space is $TOTAL_FREE GB! "

sudo sh -c "truncate -s 0 /var/lib/docker/containers/*/*-json.log"
cd /my && rm -rf /*/logs
rm -rf /var/log/nginx/*.log

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
