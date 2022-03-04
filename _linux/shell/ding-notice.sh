#!/usr/bin/env bash
# Author: 潘维吉
# Description: 通用的钉钉机器人各种通知脚本 动态传参 支持 text，markdown 两种类型消息
#================================================================
#%选项(OPTIONS)
#%     * -a <value>                 钉钉机器人 Webhook 地址的 access_token
#%     * -t <value>                 消息类型：text，markdown
#%     & -T <value>                 title，首屏会话透出的展示内容；消息类型（-t）为：markdown 时
#%     * -c <value>                 消息内容，content或者text
#%     & -m <value>                 被@人的手机号（在 content 里添加@人的手机号），多个参数用逗号隔开；如：138xxxx6666，182xxxx8888；与是否@所有人（-A）互斥，仅能选择一种方式
#%     & -A                         是否@所有人，即 isAtAll 参数设置为 ture；与被@人手机号（-m）互斥，仅能选择一种方式
#%
#%     * 表示必输，& 表示条件必输，其余为可选
#%
#%示例(EXAMPLES)
#%
#%       1. 发送 text 消息类型，并@指定人
#%       sh ${SCRIPT_NAME} -a xxx -t text -c "我就是我, 是不一样的烟火" -m "138xxxx6666,182xxxx8888"
#%
#%       2. 发送 markdown 消息类型，并@所有人
#%       sh ${SCRIPT_NAME} -a xxx -t markdown -T "markdown 测试标题" -c "# 我就是我, 是不一样的烟火" -A
#%
#================================================================
# END_OF_HEADER
#================================================================

# header 总行数
SCRIPT_HEADSIZE=$(head -200 "${0}" | grep -n "^# END_OF_HEADER" | cut -f1 -d:)
# 脚本名称
SCRIPT_NAME="$(basename "${0}")"
# 版本
VERSION="1.0.0"

# usage
function usage() {
  head -"${SCRIPT_HEADSIZE:-99}" "${0}" |
    grep -e "^#%" |
    sed -e "s/^#%//g" -e "s/\${SCRIPT_NAME}/${SCRIPT_NAME}/g" -e "s/\${VERSION}/${VERSION}/g"
}

# 发送 ding 消息
function sendDingMessage() {
  curl -s "${1}" -H 'Content-Type: application/json' -d "${2}"
}

# 检查参数输入合法性
function checkParameters() {
  # -a，-t，-c 参数必输校验
  if [ -z "${ACCESS_TOKEN}" ] || [ -z "${MSG_TYPE}" ] || [ -z "${CONTENT}" ]; then
    printf "Parameter [-a,-t,-c] is required!\n"
    exit 1
  fi

  # -t 为：markdown 时，检验参数 -T 必输
  if [ "X${MSG_TYPE}" = "Xmarkdown" ] && [ -z "${TITLE}" ]; then
    printf "When [-t] is 'markdown', you must enter the parameter [-T]!\n"
    exit 1
  fi

  # -A 和 -m 互斥，仅能选择一种方式
  if [ "X${IS_AT_ALL}" = "Xtrue" ] && [ -n "${MOBILES}" ]; then
    printf "Only one of the parameters [-A] and [-m] can be entered!\n"
    exit 1
  fi
}

# markdown 消息内容
function markdownMessage() {
  # 标题
  title=${1}
  # 消息内容
  text=${2}
  # @ 方式
  at=${3}

  # 判断是@所有人，还是指定人
  if [ -z "${at}" ]; then
    atJson=""
  elif [ "X${at}" = "Xtrue" ]; then
    atJson='"at": {
        "isAtAll": true }'
  else
    # 判断是否多个手机号
    result=$(echo "${at}" | grep ",")

    # N 个手机号
    if [ "X${result}" != "X" ]; then
      # 转换为手机号数组
      mobileArray=(${at//,/ })
      # 循环遍历数组，组织 json 格式字符串
      for mobile in "${mobileArray[@]}"; do
        mobiles="${mobile}",${mobiles}
        # @ 指定人
        atMobiles="@${mobile}",${atMobiles}
      done

    # 1 个手机号
    else
      mobiles="${at}"
      # @ 指定人
      atMobiles="@${at}"
    fi

    # @ json内容
    atJson='"at": {
        "atMobiles": [
            '${mobiles/%,/}'
        ]
    }'

    # 内容信息添加 @指定人
    text="${text}\n${atMobiles/%,/}"
  fi

  message='{
       "msgtype": "markdown",
       "markdown": {
           "title":"'${title}'",
           "text": "'${text}'"},
        '${atJson}'
   }'

  echo "${message}"
}

# text 消息内容
function textMessage() {
  # 消息内容
  text=${1}
  # @ 方式
  at=${2}

  # 判断是@所有人，还是指定人
  if [ -z "${at}" ]; then
    atJson=""
  elif [ "X${at}" = "Xtrue" ]; then
    atJson='"at": {
        "isAtAll": true }'
  else
    # 判断是否多个手机号
    result=$(echo "${at}" | grep ",")

    # N 个手机号
    if [ "X${result}" != "X" ]; then
      # 转换为手机号数组
      mobileArray=(${at//,/ })
      # 循环遍历数组，组织 json 格式字符串
      for mobile in "${mobileArray[@]}"; do
        mobiles="${mobile}",${mobiles}
        # @ 指定人
        atMobiles="@${mobile}",${atMobiles}
      done

    # 1 个手机号
    else
      mobiles="${at}"
      # @ 指定人
      atMobiles="@${at}"
    fi

    # @ json内容
    atJson='"at": {
        "atMobiles": [
            '${mobiles/%,/}'
        ]
    }'

    # 内容信息添加 @指定人
    text="${text}\n${atMobiles/%,/}"
  fi

  message='{
       "msgtype": "text",
       "text": {
           "content": "'${text}'"},
        '${atJson}'
   }'

  echo "${message}"
}

# 主方法
function main() {

  # 检查参数输入合法性
  checkParameters

  # 判断发送消息类型
  case ${MSG_TYPE} in
  markdown)
    # 判断 @ 方式
    if [ -n "${MOBILES}" ]; then
      DING_MESSAGE=$(markdownMessage "${TITLE}" "${CONTENT}" "${MOBILES}")
    elif [ -n "${IS_AT_ALL}" ]; then
      DING_MESSAGE=$(markdownMessage "${TITLE}" "${CONTENT}" "${IS_AT_ALL}")
    else
      DING_MESSAGE=$(markdownMessage "${TITLE}" "${CONTENT}")
    fi
    ;;
  text)
    if [ -n "${MOBILES}" ]; then
      DING_MESSAGE=$(textMessage "${CONTENT}" "${MOBILES}")
    elif [ -n "${IS_AT_ALL}" ]; then
      DING_MESSAGE=$(textMessage "${CONTENT}" "${IS_AT_ALL}")
    else
      DING_MESSAGE=$(textMessage "${CONTENT}")
    fi
    ;;
  *)
    printf "Unsupported message type, currently only [text, markdown] are supported!"
    exit 1
    ;;
  esac

  sendDingMessage "${DING_URL}" "${DING_MESSAGE}"
}

# 判断参数个数
if [ $# -eq 0 ]; then
  usage
  exit 1
fi

# getopt 命令行参数
if ! ARGS=$(getopt -o vAa:t:T:c:m: --long help,version -n "${SCRIPT_NAME}" -- "$@"); then
  # 无效选项，则退出
  exit 1
fi

# 命令行参数格式化
eval set -- "${ARGS}"

while [ -n "$1" ]; do
  case "$1" in
  -a)
    # Webhook access_token
    ACCESS_TOKEN=$2
    # 钉钉机器人 url 地址
    DING_URL="https://oapi.dingtalk.com/robot/send?access_token=${ACCESS_TOKEN}"
    shift 2
    ;;

  -t)
    MSG_TYPE=$2
    shift 2
    ;;

  -T)
    TITLE=$2
    shift 2
    ;;

  -c)
    CONTENT=$2
    shift 2
    ;;

  -m)
    MOBILES=$2
    shift 2
    ;;

  -A)
    IS_AT_ALL=true
    shift 2
    ;;

  -v | --version)
    printf "%s version %s\n" "${SCRIPT_NAME}" "${VERSION}"
    exit 1
    ;;

  --help)
    usage
    exit 1
    ;;

  --)
    shift
    break
    ;;

  *)
    printf "%s is not an option!" "$1"
    exit 1
    ;;

  esac
done

main
