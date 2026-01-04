#!/bin/bash
set -e

# 配置
SRC_DIR="/my/jenkins"
BACKUP_DIR="/my/backup/jenkins"
BACKUP_FILE="$BACKUP_DIR/jenkins-latest.tar.gz"
LOG_FILE="$BACKUP_DIR/backup.log"

# 确保目录存在
mkdir -p "$BACKUP_DIR"

# 开始备份
echo "[$(date '+%F %T')] Jenkins backup start" >> "$LOG_FILE"

# 打包 Jenkins
tar --exclude='workspace/*' \
    --exclude='caches/*' \
    --exclude='logs/*' \
    -czf "$BACKUP_FILE" -C "$SRC_DIR" .

echo "[$(date '+%F %T')] Jenkins backup success: $BACKUP_FILE" >> "$LOG_FILE"

# 执行授权  chmod +x /my/jenkins-backup.sh
# crontab -e
# 0 2 * * * /bin/bash /my/jenkins-backup.sh
# GNU nano编辑器CTRL+X 直接保存并退出
# service crond restart  , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表