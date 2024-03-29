#!/usr/bin/env bash
# Author: 潘维吉
# 清理Docker日志

echo "======== 开始自动清理Docker日志 ========"

sudo sh -c "truncate -s 0 /var/lib/docker/containers/*/*-json.log"
cd /my && rm -rf /*/logs

# crontab -e
# 0 2 * * *  /bin/bash /my/docker-logs-clean.sh
# service crond restart , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出
