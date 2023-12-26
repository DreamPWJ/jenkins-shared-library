#!/bin/bash
# Author: 潘维吉
# Description:  rsync+nohup定时任务同步数据

cd /mnt/nfs_data/ParkPicture/stor1/2023/12/

nohup rsync -avzP --bwlimit=5120 --include "12/" --exclude "/*"  root@119.188.90.222:/nfsdata/ParkPicture/stor1/2023/ /mnt/nfs_data/ParkPicture/stor1/2023/ > nohup.out 2>&1 &

#cd /mnt/nfs_data/ParkPicture/stor1/2024/1/
#nohup rsync -avzP --bwlimit=5120 --include "1/" --exclude "/*"  root@119.188.90.222:/nfsdata/ParkPicture/stor1/2024/ /mnt/nfs_data/ParkPicture/stor1/2024/

sleep 30

cd /mnt/nfs_data/ParkPicture/stor1/epark/

nohup rsync -avzP --bwlimit=5120  root@119.188.90.222:/nfsdata/ParkPicture/stor1/epark/ /mnt/nfs_data/ParkPicture/stor1/epark/ > nohup.out 2>&1 &


# 创建定时任务 
# chmod +x scheduled-rsync.sh  给shell脚本执行文件可执行权限
# sudo crontab -e
# 每月执行一次 0 0 1 * *  每天凌晨2点执行一次 0 2 * * *
# 0 2 * * * /bin/bash /my/scheduled-rsync.sh >/my/rsync-crontab.log 2>&1
# service crond restart , Ubuntu 使用 sudo service cron start # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出