#!/usr/bin/env bash
# Author: 潘维吉
# Description: 定时检测Nginx的服务状态，如果Nginx停止，会尝试重新启动Nginx，如果启动失败，会将Keepalived服务停止，使IP漂移到备用节点上

A=$(docker exec proxy-nginx ps -C nginx --no-header | wc -l)
if [ $A -eq 0 ]; then
  # systemctl start nginx
  docker start proxy-nginx
  sleep 2
  if [ $(docker exec proxy-nginx ps -C nginx --no-header | wc -l) -eq 0 ]; then
    systemctl stop keepalived
  fi
fi
