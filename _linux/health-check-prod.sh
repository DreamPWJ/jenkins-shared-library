#!/usr/bin/env bash
# Author: 潘维吉
# Description: 生产环境 停机 钉钉机器人通知

DING_TALK_URL='https://oapi.dingtalk.com/robot/send?access_token='
KEY_WORD='乐享科技'

PROJECT_NAME=$1 # 项目名
URL=$2          # 项目检测的URL

function sendMsg() {
  curl ${DING_TALK_URL} \
    -X POST \
    -H 'Content-Type: application/json' \
    -d '{"msgtype":"markdown",
    "markdown": {
    "title":"'${KEY_WORD}'停机通知",
    "text":"## '${PROJECT_NAME}' \n #### 无法正常访问服务 ❌ \n ####  请及时处理!!! 🏃 [查看]('${URL}') @18863302302 "
   },
    "at": {
    "atMobiles": ["18863302302"],
    "isAtAll": false}
    }'
}

for i in $(seq 1 60); do
  result=$(curl --globoff "${URL}" | grep -E "200|404|401|403|unauthorized")

  if [[ ! ${result} ]]; then
    echo -e "\033[31m无法正常访问服务 ❌ \033[0m "
    sendMsg
    break
  fi
  sleep 60s
done

# crontab -e
# 定时健康检测 which crontab   */1 * * * *
#0 */1 * * *  /bin/bash /my/health-check-prod.sh  用户APP服务端  https://app-api.panweiji.com
#0 */1 * * *  /bin/bash /my/health-check-prod.sh  管理APP服务端  https://mapp-api.panweiji.com
#0 */1 * * *  /bin/bash /my/health-check-prod.sh  平台服务端  https://admin-api.panweiji.com
# service crond restart , Ubuntu 使用 sudo service cron start# 重启crond生效
# crontab -l # 查看crond列表
# tail -f /var/log/cron  如果定时任务执行失败通过以下命令查看任务日志
