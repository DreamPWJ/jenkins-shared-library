#!/usr/bin/env bash
# Author: 潘维吉
# Description: 自动化添加到定时任务crontab

# 导出项目通用环境变量 其他shell脚本可直接${}获取
# env命令查看所有的环境变量
export COMMON_PASSWORD=

# 统一提交配置 统一重启生效
crontab crontab.cron  # 提交到配置
service crond restart # Ubuntu 使用 sudo service cron start 重启crond生效
crontab -l            # 查看crond列表

# crontab -e
# crontab -l > temp.cron # 下载配置文件
# tail -f /var/log/cron  如果定时任务执行失败通过以下命令查看任务日志
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出
