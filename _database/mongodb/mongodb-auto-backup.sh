#!/usr/bin/env bash
# Author: 潘维吉
# Linux的crontab自动定时任务 自动化备份mongodb数据库

#mongodump命令路径
DUMP=/usr/local/mongodb/bin/mongodump
#临时备份目录
OUT_DIR=/data/mongodb_bak/mongodb_bak_now
#备份存放路径
TAR_DIR=/data/mongodb_bak/mongodb_baklist
#获取当前系统时间
DATE=$(date +%Y%m_%d)
#备份主机IP
HOST=127.0.0.1
#备份数据库
DB=data01
#数据库账号
DB_USER=root
#数据库密码
DB_PASS=123456
#DAYS=15代表删除15天前的备份，即只保留近15天的备份
DAYS=15
#最终保存的数据库备份文件
TAR_BAK="mongodbbak$DATE.tar.gz"

#判断备份命令文件、备份目录是否存在
if [ ! -f "$DUMP" ]; then
  echo "mongodump the command does not exist, check the correct path."
  exit 0
elif [ ! -d "$OUT_DIR" ]; then
  echo "Create tmp backup dir"
  mkdir -p $OUT_DIR
elif [ ! -d "$TAR_DIR" ]; then
  echo "Create Backup dir"
  mkdir -p $TAR_DIR
  els
  echo "Start Backup"
fi

cd $OUT_DIR
rm -rf $OUT_DIR/*
mkdir -p $OUT_DIR/$DATE
#备份单个数据库
$DUMP -h $HOST -u $DB_USER -p $DB_PASS -d $DB -o $OUT_DIR/$DATE

#备份全部数据库
#$DUMP -h $HOST -u $DB_USER -p $DB_PASS --authenticationDatabase "admin" -o $OUT_DIR/$DATE

#压缩为.tar.gz格式
tar -zcvf $TAR_DIR/$TAR_BAK $OUT_DIR/$DATE

#删除15天前的备份文件
find $TAR_DIR/ -mtime +$DAYS -delete

# 备份恢复数据命令
# mongorestore -h 127.0.0.1 --port 27017 -u root -p 123456 -d db_name --authenticationDatabase admin  /my/mongodb/backup/db_name/

# crontab -e
# MySQL数据库自动化定时备份
# 0 */1 * * * /bin/bash /my/backup/mongodb-auto-backup.sh
# service crond restart , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表