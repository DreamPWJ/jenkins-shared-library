#!/bin/bash
# Author: 潘维吉
# Description:  rsync+nohup定时任务同步数据

cd /tidb-data/

nohup rsync -avzP --bwlimit=5120 root@172.16.100.183:/my/backup /tidb-data/ > nohup.out 2>&1 &
sleep 60

nohup rsync -avzP --bwlimit=5120 root@172.16.100.185:/my/backup /tidb-data/ > nohup.out 2>&1 &

chmod -R 777 /tidb-data/backup

# 删除-mtime是几天之前的备份文件
find /tidb-data/backup -name "*.sql.gz" -type f -mtime +3 -exec rm -rf {} \; >/dev/null 2>&1

# 创建定时任务 
# chmod +x scheduled-rsync.sh  给shell脚本执行文件可执行权限
# sudo crontab -e
# 每月执行一次 0 0 1 * *  每天凌晨2点执行一次 0 2 * * *
# 0 2 * * * /bin/bash /my/scheduled-rsync.sh >/my/rsync-crontab.log 2>&1
# service crond restart , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出