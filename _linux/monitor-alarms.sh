#!/bin/bash
# Author: 潘维吉
# Description:  脚本来监控CPU、内存和磁盘的使用率，并在使用率超过预设阈值时发送告警
# 使用Ansible中心化统一批量管理多机器脚本

# 项目服务器名称
PROJECT_SERVER_NAME="服务器"
# 钉钉机器人的Webhook地址
DING_TALK_WEBHOOK="https://oapi.dingtalk.com/robot/send?access_token=383391980b120c38f0f9a4a398349739fa67a623f9cfa834df9c5374e81b2081"
# 钉钉通知KEY
DING_TALK_KEY="蓝能科技"

# 定义告警阈值
CPU_THRESHOLD=95
MEMORY_THRESHOLD=95
DISK_USAGE_THRESHOLD=95
# 指定要监控的网卡名称 ifconfig查看网卡名称
# eth0="enp1s0"
# NETWORK_THRESHOLD=100  # 单位M

# 获取主机名
HOSTNAME=$(hostname)

# 获取CPU使用率（百分比）
CPU_USAGE=$(top -bn1 | grep "Cpu(s)" | awk '{print $2 + $4}' | cut -d. -f1)
# 获取cpu使用最高的进程
CPU_USAGE_MAX=$(ps aux --sort=-%cpu | head -n 2)

# 获取内存使用率（百分比）
MEMORY_USAGE=$(free -m | awk 'NR==2{printf "%.2f%%", $3*100/$2}')
# 获取内存使用最高的进程
MEMORY_USAGE_MAX=$(ps aux --sort=-%mem | head -n 2)

# 获取指定磁盘分区的使用率（例如根目录/的磁盘使用率，这里替换"/"为你想监控的磁盘分区）
DISK_PARTITION="/"
DISK_USAGE=$(df -h "${DISK_PARTITION}" | awk 'NR==2{print $(NF-1)}' | sed 's/%//g')
# 获取占用磁盘最高的目录列表  如 /* 根目录
DISK_USAGE_MAX=$(du -hsx /* | sort -hr | head -n 2)

# 获取内网IP地址
local_ip=$(ip -4 addr show | grep -oP '(?<=inet\s)\d+(\.\d+){3}(?=/)' | grep -v '^127\.')
#echo "Local IP: $local_ip"

# 获取公网IP地址
public_ip=$(curl -s ifconfig.me)
#echo "Public IP: $public_ip"

# 获取当前格式化日期时间（年-月-日 时:分:秒）
current_datetime=$(date +'%Y-%m-%d %H:%M:%S')

# 检查并发送告警
if [ ${CPU_USAGE} -gt ${CPU_THRESHOLD} ]; then
   # echo "警告：${HOSTNAME}上的CPU使用率已达到${CPU_USAGE}%！超过阈值${CPU_THRESHOLD}%。" | mail -s "CPU告警" admin@example.com
    DATA='{
        "msgtype": "markdown",
        "markdown": {
            "title": "🚨CPU告警-'"${DING_TALK_KEY}"'",
            "text": "# 🚨 CPU警告：'"${PROJECT_SERVER_NAME} ${HOSTNAME}"'主机上的CPU使用率已达到'"${CPU_USAGE}"'%！超过阈值'"${CPU_THRESHOLD}"'% \n - 最大CPU进程: '"${CPU_USAGE_MAX}"' \n - 外网IP: '"${public_ip}"' \n - 内网IP: '"${local_ip}"' \n - 告警时间: '"${current_datetime}"' @18863302302"
        },
        "at": {
                "isAtAll": false,
                "atMobiles": ["18863302302"]
        }
    }'
    # 发送POST请求到钉钉机器人接口
    curl -sS --request POST \
         --url $DING_TALK_WEBHOOK \
         --header 'Content-Type: application/json' \
         --data-raw "$DATA"
fi

if [ ${MEMORY_USAGE%.*} -gt ${MEMORY_THRESHOLD} ]; then
    # echo "警告：${HOSTNAME}主机上的内存使用率已达到${MEMORY_USAGE}！超过阈值${MEMORY_THRESHOLD}%。" | mail -s "内存告警" admin@example.com
        DATA='{
            "msgtype": "markdown",
            "markdown": {
                "title": "🚨内存告警-'"${DING_TALK_KEY}"'",
                "text": "# 🚨 内存警告：'"${PROJECT_SERVER_NAME} ${HOSTNAME}"'主机上的内存使用率已达到'"${MEMORY_USAGE}"'！超过阈值'"${MEMORY_THRESHOLD}"'%  \n - 最大内存进程: '"${MEMORY_USAGE_MAX}"' \n - 外网IP: '"${public_ip}"' \n - 内网IP: '"${local_ip}"' \n - 告警时间: '"${current_datetime}"' @18863302302"
            },
            "at": {
                    "isAtAll": false,
                     "atMobiles": ["18863302302"]
            }
        }'
        # 发送POST请求到钉钉机器人接口
        curl -sS --request POST \
             --url $DING_TALK_WEBHOOK \
             --header 'Content-Type: application/json' \
             --data-raw "$DATA"
fi

if [ ${DISK_USAGE} -gt ${DISK_USAGE_THRESHOLD} ]; then
   # echo "警告：${HOSTNAME}主机上${DISK_PARTITION}分区的磁盘使用率已达到${DISK_USAGE}%！超过阈值${DISK_USAGE_THRESHOLD}%。" | mail -s "磁盘告警" admin@example.com
        DATA='{
            "msgtype": "markdown",
            "markdown": {
                "title": "🚨磁盘告警-'"${DING_TALK_KEY}"'",
                "text": "# 🚨 磁盘警告：'"${PROJECT_SERVER_NAME} ${HOSTNAME}"'主机上'"${DISK_PARTITION}"'分区的磁盘使用率已达到'"${DISK_USAGE}"'%！超阈值'"${DISK_USAGE_THRESHOLD}"'% \n - 最大磁盘占用: '"${DISK_USAGE_MAX}"' \n - 外网IP: '"${public_ip}"' \n - 内网IP: '"${local_ip}"' \n - 告警时间: '"${current_datetime}"' @18863302302"
            },
            "at": {
                     "isAtAll": false,
                     "atMobiles": ["18863302302"]
            }
        }'
        # 发送POST请求到钉钉机器人接口
        curl -sS --request POST \
             --url $DING_TALK_WEBHOOK \
             --header 'Content-Type: application/json' \
             --data-raw "$DATA"
        # TODO 记录已发送的记录 防止重复发送  并自动执行脚本清理磁盘空间

        # 自动清理磁盘空间命令
        sudo sh -c "truncate -s 0 /var/lib/docker/containers/*/*-json.log" || true
        find /my -type f -name "*.log" -exec rm -f {} + || true
        find /my -type d -name "log*" -exec rm -rf {} + || true
        docker builder prune --force  || true
        rm -f /var/lib/docker/overlay2/*/diff/etc/nginx/on || true
        lsof -w | grep 'deleted' | awk '{print $2}' | xargs kill -9  || true

        # ./my/clean_disk.sh
        # STATUS_FILE="/tmp/monitor_status"
        # echo "disk_status=0" >> $STATUS_FILE
fi



# # 获取当前的接收和发送字节数
# RX_CURRENT=$(cat /proc/net/dev | grep $eth0 | awk '{print $2}')
# TX_CURRENT=$(cat /proc/net/dev | grep $eth0 | awk '{print $10}')
#
# # 判断接收流量是否超过阈值
# RX_THRESHOLD=$(($NETWORK_THRESHOLD*1024*1024)) # 阈值
# if [ $RX_CURRENT -gt $RX_THRESHOLD ]; then
#     echo "警告：网卡 $eth0 的接收流量已超过$NETWORK_THRESHOLD MB ！当前接收流量为: $($RX_CURRENT/1024/1024) M"
# fi
#
# # 判断发送流量是否超阈值
# TX_THRESHOLD=$(($NETWORK_THRESHOLD*1024*1024)) # 阈值
# if [ $TX_CURRENT -gt $TX_THRESHOLD ]; then
#     echo "警告：网卡 $eth0 的发送流量已超过$NETWORK_THRESHOLD MB ！当前发送流量为: $(echo "$TX_CURRENT / 1024.0 / 1024.0" | awk '{printf "%.2f\n", $1}') M"
# fi


exit 0


# 执行授权  chmod +x /my/monitor-alarms.sh
# crontab -e
# */10 * * * * /bin/bash /my/monitor-alarms.sh
# service crond restart  , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表