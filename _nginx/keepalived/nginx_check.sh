#!/usr/bin/env bash
# Author: 潘维吉
# Description: 定时检测Nginx的服务状态，如果Nginx停止，会尝试重新启动Nginx，如果启动失败，会将Keepalived服务停止，使IP漂移到备用节点上

# 检测nginx是否启动  容器化检测设置  pidof nginx
pids=$(pidof nginx) && echo $pids
if [[ ! ${pids} ]]; then
  # /usr/sbin/nginx  # 宿主机启动
  # systemctl start nginx
  docker start proxy-nginx
  sleep 3
  if [[ ! ${pids} ]]; then
    # /usr/sbin/keepalived -s stop
    systemctl stop keepalived
  fi
fi


# 创建定时任务 检测nginx是否存活来控制keepalived切换  ！！！在keepalived.conf已经配置了检测机制 无需再做定时任务
# sudo crontab -e
# 每多少秒 * * * * * sleep 5;  每分钟 */1 * * * *
# * * * * * sleep 5; /bin/bash /etc/keepalived/nginx_check.sh >/etc/keepalived/crontab.log 2>&1
# service crond restart , Ubuntu 使用 sudo service cron start # 重启crond生效
# crontab -l # 查看crond列表
# chmod +x nginx_check.sh  给shell脚本执行文件可执行权限
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出