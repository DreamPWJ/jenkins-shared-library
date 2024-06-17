#!/bin/bash
# Author: 潘维吉
# Description: 通用Shell脚本复用和模块化  随着Shell编写复杂度提高, 需要大量的复用和模块化情况

# 其他shell脚本  复用执行
# ./common/docker-common.sh

# 可以通过函数名调用内部函数的脚本模板
# 示例方法 其他脚本调用   ./docker-common.sh show_args 1 2 3
function show_args() {
  echo "方法名称: $FUNCNAME"
  echo "第一个参数: $1"
  echo "所有的参数: $@"
}

# 部署前操作检查容器是否存在 停止容器
function stop_docker() {
  if [ "$(docker ps -a | grep $1)" ]; then
    echo "停止指定的容器"
    docker stop $1 --time=1 || true
    echo "删除指定的容器"
    sudo docker rm -f $1
  fi
}

# 判断端口号是否存在  存在退出
function exist_port() {
  port_exist=$(lsof -i:"$1")
  if [[ ${port_exist} ]]; then
    # kill -9  PID  # 直接杀掉进程
    echo -e "\033[31m当前应用部署服务端口$1已存在, Shell自动化脚本退出运行, 端口冲突停止部署 ❌ \033[0m "
    exit 1 # 1:非正常运行导致退出程序
  fi
}

# 根据镜像创建时间判断镜像是否构建成功
function is_success_images() {
  docker_image_create_time=$(docker inspect -f '{{.Created}}' $1)
  # 时间字符串转时间戳
  docker_image_create_time_stamp=$(date -d "${docker_image_create_time}" +%s)
  # 当前时间和镜像创建时间差
  docker_image_time_diff=$((($(date -d "$(date)" +%s)) - ${docker_image_create_time_stamp}))
  # 时间差大于等于多少秒 说明不是最新镜像
  if [[ ${docker_image_time_diff} -ge 60 && $2 == false ]]; then
    #echo "当前时间与创建镜像的时间差: ${docker_image_time_diff}秒"
    echo -e "\033[31mDocker镜像构建失败  ❌  \033[0m " # 镜像可能不是最新镜像 可能为同一台服务器并发构建导致父镜像层被删除
#    echo "======== 开始自动清理Docker日志 ========"
#    sudo sh -c "truncate -s 0 /var/lib/docker/containers/*/*-json.log"
#    cd /my && rm -rf /*/logs
    echo "请查看错误日志后, 再次尝试部署 🤪 "
    exit 1
  fi
}

# 删除所有悬空的镜像
function remove_docker_dangling_images() {
  # dangling表示未被打tag的镜像
  docker_dangling_images=$(docker image ls -f dangling=true -q)
  if [[ ${docker_dangling_images} ]]; then
    echo "删除所有悬空的镜像"
    # 并发构建会有问题  导致正在构建镜像的父镜像层被删除
    # --no-prune : 不移除该镜像的过程镜像 默认移除 移除导致并发构建找不到父镜像层
    docker rmi ${docker_dangling_images} --no-prune || true
  fi
}

# 根据镜像名称获取所有ID并删除旧镜像  不适合远程镜像情况
function remove_docker_image() {
  if [[ $1 ]]; then
    # 根据镜像名称查询镜像ID组
    #docker_image_ids=$(docker images -q --filter reference=docker_image_name)
    echo "根据镜像ID删除无效的旧镜像: $1"
    docker rmi $1 || true
  fi
}

# 获取系统CPU使用率 如果CPU占用高 则排队延迟部署 避免并发部署等导致资源阻塞
function get_cpu_rate() {
  cpu_rate=$(sudo echo $((100 - $(vmstat 1 2 | tail -1 | awk '{print $15}'))) || true)
  # -ge 大于等于  大于阈值警告并等待
  if [[ ${cpu_rate} -ge 60 ]]; then
    echo " 🚨 当前应用部署服务器CPU占用率高达: ${cpu_rate}% , 避免并发部署等导致资源阻塞, 排队延迟部署, 等待10秒中... ☕ (如果CPU长时间占用高, 请去服务器查看资源占用情况)"
    # CPU资源占用过高可能是因为有些项目启动失败 在不断的重启Docker容器 这里可以做容器健康状态判断自动停掉服务
    # 可以filter过滤的Docker运作状态: created, restarting, running, removing, paused, exited, dead
    docker_restarting_container=$(docker ps --all -q -f status=restarting)
    if [[ ${docker_restarting_container} ]]; then
      echo "当前应用部署服务器CPU资源占用过高, 自动删除具有restarting状态的Docker容器"
      docker stop ${docker_restarting_container} || true && docker rm ${docker_restarting_container} || true
    fi
    # 等待时间
    sleep 10s
    # 递归调用探测 如果CPU一直高, 则一直探测, 保证资源低于设置值后才继续进行部署
    get_cpu_rate
  else
    echo -e "\033[32m当前应用部署服务器CPU占用率为: ${cpu_rate}% \033[0m"
  fi

  # rate=${cpu_rate//'sy'/'' } # 替换字符串
  # rate2=$(echo ${rate} | sed 's/ //g') # 去掉所有空格
  # echo "结果2: ${rate2}"
  # rate3=$(echo ${rate2} | sed 's/\..*//g') # 去除掉小数点之后的字符并
  # echo "结果3: ${rate3}"
  # rate4=$(echo "$rate3" | bc)  # string转int
  # echo "结果4: ${rate4}"
}

# 获取系统磁盘资源 如果硬盘资源不足 停止容器构建或自动清理空间
function get_disk_space() {
    # 设置所需的最小可用空间（单位GB）
    MIN_FREE_SPACE=2

    # 获取总的可用空间（单位GB）
    TOTAL_FREE=$(df -h | awk '/\// {print $4}' | sed 's/G//')

    # 将KB转换成GB（如果需要的话）
    if [[ $TOTAL_FREE =~ ^[0-9]+\.[0-9]+K ]]; then
        TOTAL_FREE=$(echo "scale=2; $TOTAL_FREE / 1024" | bc)
    elif [[ $TOTAL_FREE =~ ^[0-9]+\.[0-9]+M ]]; then
        TOTAL_FREE=$(echo "scale=2; $TOTAL_FREE / 1024 / 1024" | bc)
    fi

    # 判断可用空间是否低于最小需求
    if (( $(echo "$TOTAL_FREE < $MIN_FREE_SPACE" | bc -l) )); then
        echo "🚨 Warning: Free space is below $MIN_FREE_SPACE GB!"
        echo -e "\033[31m当前系统磁盘空间不足, 可能导致Docker镜像构建失败  ❌  \033[0m"
        echo "开始自动清理Docker日志"
        sudo sh -c "truncate -s 0 /var/lib/docker/containers/*/*-json.log"
        cd /my && rm -rf /*/logs
        #exit 1
    fi
}

# 检测是否存在Dockerfile文件 不存在退出执行
function exist_docker_file() {
  if [[ ! -f "Dockerfile" ]]; then
    echo -e "\033[31mDockerfile文件不存在 无法构建docker镜像 shell命令退出 构建镜像失败 ❌ \033[0m "
    exit 1
  fi
}

# 检测是否存在部署文件夹 如果不存在创建一个
function mkdir_deploy_file() {
  if [[ ! -d $1 ]]; then
    mkdir -p $1
    echo "创建部署文件夹: $1"
  fi
}

# 判断是否存在lsof 不存在安装
function exist_lsof() {
  if [[ ! $(command -v lsof) ]]; then
    yum install -y lsof || true
    apt-get install -y lsof || true
  fi
}

# 获取本机外网ip
function get_public_ip() {
  str=$(
    wget http://ipecho.net/plain -O - -q
    echo
  )
  # echo "本机外网ip为: $str"
  public_ip = $str
}

if [ $# -ne 0 ]; then
  funcname="$1"
  shift 1
  $funcname "$@"
fi
