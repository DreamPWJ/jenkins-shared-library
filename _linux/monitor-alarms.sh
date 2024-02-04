#!/bin/bash
# Author: 潘维吉
# Description:  脚本来监控CPU、内存和磁盘的使用率，并在使用率超过预设阈值时发送告警

# 钉钉机器人的Webhook地址
DING_TALK_WEBHOOK="https://oapi.dingtalk.com/robot/send?access_token=383391980b120c38f0f9a4a398349739fa67a623f9cfa834df9c5374e81b2081"

# 定义告警阈值
CPU_THRESHOLD=90
MEMORY_THRESHOLD=90
DISK_USAGE_THRESHOLD=95

# 获取主机名
HOSTNAME=$(hostname)

# 获取CPU使用率（百分比）
CPU_USAGE=$(top -bn1 | grep "Cpu(s)" | awk '{print $2 + $4}' | cut -d. -f1)

# 获取内存使用率（百分比）
MEMORY_USAGE=$(free -m | awk 'NR==2{printf "%.2f%%", $3*100/$2}')

# 获取指定磁盘分区的使用率（例如根目录/的磁盘使用率，这里替换"/"为你想监控的磁盘分区）
DISK_PARTITION="/"
DISK_USAGE=$(df -h "${DISK_PARTITION}" | awk 'NR==2{print $(NF-1)}' | sed 's/%//g')

# 获取内网IP地址
local_ip=$(ip -4 addr show | grep -oP '(?<=inet\s)\d+(\.\d+){3}(?=/)' | grep -v '^127\.')
#echo "Local IP: $local_ip"

# 获取公网IP地址
public_ip=$(curl -s ifconfig.me)
#echo "Public IP: $public_ip"

# 获取当前格式化日期时间（年-月-日 时:分:秒）
current_datetime=$(date +'%Y-%m-%d %H:%M:%S')

# 检查并发送告警
if [ ${CPU_USAGE} -ge ${CPU_THRESHOLD} ]; then
   # echo "警告：${HOSTNAME}上的CPU使用率已达到${CPU_USAGE}%！超过阈值${CPU_THRESHOLD}%。" | mail -s "CPU告警" admin@example.com
    DATA='{
        "msgtype": "markdown",
        "markdown": {
            "title": "🚨CPU告警-蓝能科技",
            "text": "# 🚨 CPU警告：'"${HOSTNAME}"'主机上的CPU使用率已达到'"${CPU_USAGE}"'%！超过阈值'"${CPU_THRESHOLD}"'% \n - 外网IP: '"${public_ip}"' \n - 内网IP: '"${local_ip}"' \n - 告警时间: '"${current_datetime}"' @18863302302"
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

if [ ${MEMORY_USAGE%.*} -ge ${MEMORY_THRESHOLD} ]; then
    # echo "警告：${HOSTNAME}上的内存使用率已达到${MEMORY_USAGE}！超过阈值${MEMORY_THRESHOLD}%。" | mail -s "内存告警" admin@example.com
        DATA='{
            "msgtype": "markdown",
            "markdown": {
                "title": "🚨内存告警-蓝能科技",
                "text": "# 🚨 内存警告：'"${HOSTNAME}"'主机上的内存使用率已达到'"${MEMORY_USAGE}"'%！超过阈值'"${MEMORY_THRESHOLD}"'% \n - 外网IP: '"${public_ip}"' \n - 内网IP: '"${local_ip}"' \n - 告警时间: '"${current_datetime}"' @18863302302"
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

if [ ${DISK_USAGE} -ge ${DISK_USAGE_THRESHOLD} ]; then
   # echo "警告：${HOSTNAME}上${DISK_PARTITION}分区的磁盘使用率已达到${DISK_USAGE}%！超过阈值${DISK_USAGE_THRESHOLD}%。" | mail -s "磁盘告警" admin@example.com
        DATA='{
            "msgtype": "markdown",
            "markdown": {
                "title": "🚨磁盘告警-蓝能科技",
                "text": "# 🚨 磁盘警告：'"${HOSTNAME}"'主机上'"${DISK_PARTITION}"'分区的磁盘使用率已达到'"${DISK_USAGE}"'%！超过阈值'"${DISK_USAGE_THRESHOLD}"'% \n - 外网IP: '"${public_ip}"' \n - 内网IP: '"${local_ip}"' \n - 告警时间: '"${current_datetime}"' @18863302302"
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


exit 0


# 执行授权  chmod +x /my/monitor-alarms.sh
# crontab -e
# */10 * * * * /bin/bash /my/monitor-alarms.sh
# service crond restart  , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表