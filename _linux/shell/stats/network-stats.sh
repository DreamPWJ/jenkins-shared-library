#!/usr/bin/env bash
# 网络流量使用情况

#通过/proc/net/dev获取执行网卡的信息
if [ $# -lt 2 ]; then
  echo "Useage : $(basename $0) eth0 1"
  exit 1
fi
#网卡名
eth=$1
#时间间隔（频率）
interval=$2

in_last=$(cat /proc/net/dev | grep ${eth} | sed -e "s/\(.*\)\:\(.*\)/\2/g" | awk '{print $1 }')
out_last=$(cat /proc/net/dev | grep ${eth} | sed -e "s/\(.*\)\:\(.*\)/\2/g" | awk '{print $9 }')

while true; do
  sleep ${interval}
  in=$(cat /proc/net/dev | grep $eth | sed -e "s/\(.*\)\:\(.*\)/\2/g" | awk '{print $1 }')
  out=$(cat /proc/net/dev | grep $eth | sed -e "s/\(.*\)\:\(.*\)/\2/g" | awk '{print $9 }')

  traffic_in=$(echo ${in} ${in_last} | awk '{printf "%.2f", ($1-$2)/1024}')
  traffic_out=$(echo ${out} ${out_last} | awk '{printf "%.2f", ($1-$2)/1024}')
  current_time=$(date +"%F %H:%M:%S")

  echo "${current_time} -- IN: ${traffic_in} KByte/s     OUT: ${traffic_out} KByte/s"

  in_old=${in}
  out_old=${out}
done
