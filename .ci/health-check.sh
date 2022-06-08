#! /bin/bash
# Author: 潘维吉
# Description: 健康检测应用服务是否启动成功

# 使用getopts的方式进行shell参数传递
while getopts ":a:b:" opt; do
  case $opt in
  a)
    project_type=$OPTARG # 1. 前端 2. 后端
    ;;
  b)
    check_url=$OPTARG # 项目检测的URL
    ;;
  ?)
    echo "未知参数"
    exit 1
    ;;
  esac
done

if [[ ! $(command -v curl) ]]; then
  echo "curl命令不存在，安装curl命令"
  yum install -y curl || true
  apt-get install -y curl || true
fi

if [[ ${project_type} == 1 ]]; then
  # 循环检测次数
  front_end_loop_num=60
  # 循环检测前端启动健康状态
  for i in $(seq 1 ${front_end_loop_num}); do
    if [[ ${i} -ge ${front_end_loop_num} ]]; then
      echo "Web前端启动失败" # 必须包含"失败"字样 pipeline内判断
      break
      exit 0
    else
      result=$(curl --connect-timeout 120 --max-time 120 -I ${check_url} | grep OK)
      if [[ ${result} =~ OK ]]; then
        echo "Web前端启动成功" # 必须包含"成功"字样 pipeline内判断
        break
      fi
      sleep 2s
    fi
  done
elif [[ ${project_type} == 2 ]]; then
  # 循环检测次数
  back_end_loop_num=180
  # 循环检测服务端启动健康状态
  for i in $(seq 1 ${back_end_loop_num}); do
    if [[ ${i} -ge ${back_end_loop_num} ]]; then
      echo "服务端启动失败" # 必须包含"失败"字样 pipeline内判断
      break
      exit 0
    else
      # 注意部分服务可能不是http请求服务 如socket 后面兼容这种模式的健康探测
      code=$(curl -sIL -w "%{http_code}" -o /dev/null "${check_url}")
      result=$(curl --connect-timeout 600 --max-time 600 --globoff "${check_url}" | grep -E "200|404|401|403|unauthorized|Bad Request")
      if [[ ${code} == 200 || ${result} ]]; then
        # echo "状态码: ${code} 响应结果: ${result}"
        echo "服务端启动成功" # 必须包含"成功"字样 pipeline内判断
        break
      fi
      sleep 2s
    fi
  done
fi

#function sendMsg() {
#  start_time=$(date +'%Y-%m-%d %H:%M:%S')
#  start_seconds=$(date --date="${start_time}" +%s)
#  end_time=$(date +'%Y-%m-%d %H:%M:%S')
#  end_seconds=$(date --date="${end_time}" +%s)
#  seconds=$((end_seconds - start_seconds + 1))
#  seconds_format=$(date -d@"${seconds}" -u +%Mmin%Ss) # 格式化时间显示
#  seconds_format=${seconds_format#"00min"}            # 去掉0分钟情况前缀
#}
