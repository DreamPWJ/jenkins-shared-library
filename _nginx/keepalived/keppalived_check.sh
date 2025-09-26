#!/usr/bin/env bash
# Author: 潘维吉
# Description: 定时检测Keepalived的服务状态，如果Keepalived停止，会尝试重新启动Keepalived
# chmod 755 /etc/keepalived/keppalived_check.sh && systemctl restart keepalived  给shell脚本执行文件可执行权限

# 检测服务是否正常或存在  0 不正常不存在
check_res=$(ps -C nginx --no-header | wc -l)

# -ne 不等于0
if [[ check_res -ne 0 ]]; then
     # 如果keepalived服务不存在 启动keepalived服务  因为当前keepalived已停止无法再检测
     keepalived_check_res=$(ps -C keepalived --no-header | wc -l)
     if [[ keepalived_check_res -eq 0 ]]; then
        echo $(date)  "keepalived is not running, start..." >> /etc/keepalived/check_keepalived.log
        systemctl start keepalived
        # systemctl status keepalived
     fi
fi

# sudo crontab -e
# 每多少秒 * * * * * sleep 5;  每分钟 */1 * * * *
# * * * * * sleep 5; /bin/bash /etc/keepalived/keppalived_check.sh
# service crond restart , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表
# chmod 755 /etc/keepalived/keppalived_check.sh 给shell脚本执行文件可执行权限
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出