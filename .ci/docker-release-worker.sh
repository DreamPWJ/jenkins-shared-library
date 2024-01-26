#!/bin/bash
# Author: 潘维吉
# Description: 执行Docker发布部署shell脚本 同一台服务器主从分布式部署

echo -e "\033[32m执行Docker部署Java 同一台服务器主从分布式部署  👇 \033[0m"

echo "使用getopts的方式进行shell参数传递"
while getopts ":a:b:c:d:e:f:g:h:i:k:l:m:n:o:y:z:" opt; do
  case $opt in
  a)
    echo "project_name_prefix=$OPTARG"
    project_name_prefix=$OPTARG # 项目名称
    ;;
  b)
    echo "project_type=$OPTARG"
    project_type=$OPTARG # 项目类型
    ;;
  c)
    echo "host_port=$OPTARG"
    host_port=$OPTARG # 宿主机端口
    ;;
  d)
    echo "expose_port=$OPTARG"
    expose_port=$OPTARG # 容器内暴露端口
    ;;
  e)
    echo "env_mode=$OPTARG"
    env_mode=$OPTARG # 环境模式 如 dev sit test prod等
    ;;
  f)
    echo "is_prod=$OPTARG"
    is_prod=$OPTARG # 是否生产环境
    ;;
  g)
    echo "docker_java_opts=$OPTARG"
    docker_java_opts=$OPTARG # JVM内存设置
    ;;
  h)
    echo "docker_memory=$OPTARG"
    docker_memory=$OPTARG # docker内存限制
    ;;
  i)
    echo "docker_log_opts=$OPTARG"
    docker_log_opts=$OPTARG # docker日志限制
    ;;
  k)
    echo "deploy_folder=$OPTARG"
    deploy_folder=$OPTARG # 服务器上部署所在的文件夹名称
    ;;
  l)
    echo "jdk_version=$OPTARG"
    jdk_version=$OPTARG # jdk版本
    ;;
  m)
    echo "is_push_docker_repo=$OPTARG"
    is_push_docker_repo=$OPTARG # 是否上传镜像到docker容器仓库
    ;;
  n)
    echo "docker_repo_registry_and_namespace=$OPTARG"
    docker_repo_registry_and_namespace=$OPTARG # docker容器仓库地址和命名空间拼接
    ;;
  o)
    echo "docker_volume_mount=$OPTARG"
    docker_volume_mount=$OPTARG # docker挂载映射
    ;;
  y)
    echo "remote_debug_port=$OPTARG"
    remote_debug_port=$OPTARG # 远程调试端口
    ;;
  z)
    echo "extend_port=$OPTARG"
    extend_port=$OPTARG # 扩展端口
    ;;
  ?)
    echo "未知参数"
    exit 1
    ;;
  esac
done

# 从服务标记名称
worker_name_sign="worker"
# docker镜像名称
docker_image_name=${project_name_prefix}/${project_type}-${env_mode}
# docker容器名称
docker_container_name=${project_name_prefix}-${project_type}-${worker_name_sign}-${env_mode}
# 项目全名称
project_name=${project_name_prefix}-${project_type}-${worker_name_sign}

# 判断是否存在docker
if [[ ! $(command -v docker) ]]; then
  # 跳过执行 关键字不要更改 流水线使用
  echo "Docker环境未初始化, 跳过执行服务部署脚本 ✘"
  exit 0 # 0 正常退出
fi

# 初始化没有镜像跳过处理
if [[ ! $(docker images -a | grep ${docker_image_name}) ]]; then
  # 跳过执行 关键字不要更改 流水线使用
  echo "没有Docker镜像存在, 跳过执行服务部署脚本 ✘"
  exit 0 # 0 正常退出
fi

# 非生产环境限制容器CPU占用 防止同时部署情况导致其他服务无法访问
if [[ ${is_prod} == false ]]; then
  cup_num=$(cat /proc/cpuinfo | grep "processor" | wc -l)
  echo "逻辑CPU的个数: ${cup_num}"
  # limit_cup_num=$(echo "scale=4; ${cup_num} - 0.6" | bc) # 浮点数计算 bc命令可能不存在
  dynamic_run_args=${dynamic_run_args}" --cpus=${cup_num} "
fi

# 动态参数
if [[ ${docker_volume_mount} ]]; then
  docker_volume_mount_array=(${docker_volume_mount//,/ })
  docker_volume_mounts=""
  echo "容器Docker数据卷挂载映射动态参数"
  # shellcheck disable=SC2068
  for var in ${docker_volume_mount_array[@]}; do
    echo $var
    docker_volume_mounts=${docker_volume_mounts}" -v ${var} "
  done
  dynamic_run_args=${dynamic_run_args}"${docker_volume_mounts} "
fi

# 检查容器是否存在 停止容器
cd /${deploy_folder} && ./docker-common.sh stop_docker ${docker_container_name}

# 判断端口号是否存在  存在退出
cd /${deploy_folder} && ./docker-common.sh exist_port ${host_port}
exist_port_code=$?
if [[ "${exist_port_code}" == 1 ]]; then
  exit 1
fi

# 获取系统CPU使用率 如果CPU占用高 则排队延迟部署 避免并发部署等导致资源阻塞
cd /${deploy_folder} && ./docker-common.sh get_cpu_rate && cd /${deploy_file}

echo "👨‍💻 启动运行Docker容器 环境: ${env_mode} 映射端口: ${host_port}:${expose_port}"
set -x # 开启shell命令打印模式
docker run -d --restart=always -p ${host_port}:${expose_port} \
  -e "SPRING_PROFILES_ACTIVE=${env_mode}" -e "PROJECT_NAME=${project_name}" -e HOST_NAME=$(hostname)  \
  -e "JAVA_OPTS=-Xms128m ${docker_java_opts}" --cpus=0.8 -m ${docker_memory} --log-opt ${docker_log_opts} --log-opt max-file=1 ${dynamic_run_args} \
  -v /${deploy_folder}/${project_name}/logs:/logs \
  --name ${docker_container_name} ${docker_image_name}
