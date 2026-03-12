#!/usr/bin/env bash
# Author: 潘维吉
# Description: 通过域名获取证书的过期时间 检测ssl有效期并提醒

DING_TALK_URL='https://oapi.dingtalk.com/robot/send?access_token='
KEY_WORD='乐享科技'

# 脚本所在目录即脚本名称
script_dir=$(cd "$(dirname "$0")" && pwd)

readFile="${script_dir}/domain-ssl.info"
copywriter=""
is_send_msg=false

grep -v '^#' ${readFile} > temp.txt
while read line # 读取存储了需要监测的域名的文件
 do
  domain=$(echo "${line}" | awk -F ':' '{print $1}')
  port=$(echo "${line}" | awk -F ':' '{print $2}')

  # 使用openssl获取域名的证书情况，然后获取其中的到期时间
  END_TIME=$(echo | openssl s_client -servername ${domain} -connect ${domain}:${port} 2>/dev/null | openssl x509 -noout -dates | grep 'After' | awk -F '=' '{print $2}' | awk -F ' +' '{print $1,$2,$4 }')
  END_TIME=$(date +%s -d "$END_TIME")                                # 将日期转化为时间戳
  NOW_TIME=$(date +%s -d "$(date | awk -F ' +' '{print $2,$3,$6}')") # 将当前的日期也转化为时间戳

  expires_days=$(($((END_TIME - $NOW_TIME)) / (60 * 60 * 24))) # 到期时间减去目前时间再转化为天数
  if [[ ${expires_days} -le 0 ]]; then
    copywriter_tmp="【域名**${domain}**安全证书已过期**${expires_days#"-"}**天!!!】"
  else
    copywriter_tmp="【域名**${domain}**安全证书还有**${expires_days}**天过期】"
  fi
  echo "${copywriter_tmp}"

  if [[ ${expires_days} -le 15 ]]; then
    is_send_msg=true
    copywriter="${copywriter}${copywriter_tmp}"
  fi
done<temp.txt

function sendMsg() {
  curl ${DING_TALK_URL} \
    -X POST \
    -H 'Content-Type: application/json' \
    -d '{"msgtype":"markdown",
    "markdown": {
    "title":"SSL证书有效期提醒 '${KEY_WORD}'",
    "text":"## 域名SSL证书有效期提醒 ⚠️ \n '$1' \n #### 请及时处理  🏃 "
   },
    "at": {
    "atMobiles": [],
    "isAtAll": false}
    }'
}

if [[ ${is_send_msg} == true ]]; then
  sendMsg "${copywriter}"
fi

# crontab -e
# 定时检测 which crontab
# 0 7 * * * /bin/bash /my/check-ssl.sh
# service crond restart , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+X 直接保存并退出
