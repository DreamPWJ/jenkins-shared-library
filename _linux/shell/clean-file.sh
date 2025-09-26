#!/bin/bash

# 设置要清理的目录，请修改为你的实际目录
TARGET_DIR="/my/*/logs"

# 设置日志文件路径（可选）
LOG_FILE="/my/cleanup_old_files.log"

# 判断目标目录是否存在
if [ ! -d "$TARGET_DIR" ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Error: Target directory $TARGET_DIR does not exist." | tee -a "$LOG_FILE"
    exit 1
fi

# 执行清理操作，并记录日志
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Start cleaning files older than N days in directory: $TARGET_DIR" | tee -a "$LOG_FILE"

# 使用find命令查找并删除文件
# -type f: 只查找文件
# -mtime +180: 查找180天前修改的文件
# -exec rm -f {} \;: 对找到的文件执行强制删除操作
find "$TARGET_DIR" -type f -mtime +200 -exec rm -f {} \;

# 检查上一条命令的执行状态
if [ $? -eq 0 ]; then
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Cleanup completed successfully." | tee -a "$LOG_FILE"
else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Error: Cleanup process encountered an error." | tee -a "$LOG_FILE"
fi

# 创建定时任务 自动续期SSL证书 默认证书有效期是90天
# chmod +x /my/clean-file.sh  给shell脚本执行文件可执行权限  先手动执行测试一下
# sudo crontab -e
# 每月执行一次 0 0 1 * *  每天凌晨2点执行一次 0 2 * * *
# 0 2 * * * /bin/bash /my/clean-file.sh
# service crond restart , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出
