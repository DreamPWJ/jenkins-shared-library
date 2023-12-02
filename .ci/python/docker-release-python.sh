#!/bin/bash
# Author: 潘维吉
# Description: 执行Docker发布部署shell脚本

echo -e "\033[32m执行Docker部署Python语言脚本  👇 \033[0m"
# chmod +x docker-release.sh 给shell脚本执行文件可执行权限
# 可采用$0,$1,$2..等方式获取脚本命令行传入的参数  执行脚本

echo "使用getopts的方式进行shell参数传递"
while getopts ":a:b:c:d:e:f:g:h:i:k:l:m:n:o:p:y:z:" opt; do
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
    echo "python_version=$OPTARG"
    python_version=$OPTARG # Python版本号
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
    echo "python_start_file=$OPTARG"
    python_start_file=$OPTARG # Python启动文件名称
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
  p)
    echo ""
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

# 当前日期格式
date=$(date '+%Y%m%d-%H%M')
# docker镜像名称
docker_image_name=${project_name_prefix}/${project_type}-${env_mode}
# docker容器名称
docker_container_name=${project_name_prefix}-${project_type}-${env_mode}
# 项目全名称
project_name=${project_name_prefix}-${project_type}
# 部署文件夹
deploy_file=/${deploy_folder}/${project_name}

# 判断是否存在docker
if [[ ! $(command -v docker) ]]; then
  echo -e "\033[31mDocker环境不存在, 退出执行服务部署脚本 ❌ \033[0m "
  exit 1 # 1:非正常运行导致退出程序
fi

# 检测是否存在Dockerfile文件 不存在退出执行
cd /${deploy_folder} && ./docker-common.sh exist_docker_file

# 检测是否存在部署文件夹 如果不存在创建一个
cd /${deploy_folder} && ./docker-common.sh mkdir_deploy_file ${deploy_file}

# 进入部署文件所在目录
cd ${deploy_file}
pwd

# docker构建和运行动态参数处理
build_expose_ports=${expose_port}
dynamic_run_args=""

# 动态扩展端口
if [[ ${extend_port} ]]; then
  build_expose_ports="${expose_port} ${extend_port}"
  dynamic_run_args=" -p ${extend_port}:${extend_port} -e "EXTEND_PORT=${extend_port}" "
fi

# 非生产环境限制容器CPU占用 防止同时部署情况导致其他服务无法访问
if [[ ${is_prod} == false ]]; then
  cup_num=$(cat /proc/cpuinfo | grep "processor" | wc -l)
  echo "逻辑CPU的个数: ${cup_num}"
  # limit_cup_num=$(echo "scale=4; ${cup_num} - 0.3" | bc) # 浮点数计算 bc命令可能不存在
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

echo "构建暴露端口: ${build_expose_ports}"
echo "运行动态参数: ${docker_memory}  ${docker_log_opts} ${dynamic_run_args}"
echo "远程调试参数: ${remote_debugging_param}"

# 根据镜像名称查询镜像ID 用于删除无效的镜像
docker_image_ids=$(docker images -q --filter reference=${docker_image_name})

set -x # 开启shell命令打印模式

# 是否是远程镜像仓库方式
if [[ ${is_push_docker_repo} == false ]]; then
  echo "🏗️  开始构建Docker镜像(无缓存构建)"
  docker build -t ${docker_image_name} \
    --build-arg PROJECT_NAME=${project_name} \
    --build-arg DEPLOY_FOLDER=${deploy_folder} \
    --build-arg EXPOSE_PORT="${build_expose_ports}" \
    --build-arg PYTHON_VERSION="${python_version}" \
    --build-arg PYTHON_START_FILE="${python_start_file}" \
    -f /${deploy_folder}/python/Dockerfile . --no-cache
else
  docker_image_name=${docker_repo_registry_and_namespace}/${project_name_prefix}-${project_type}-${env_mode}
fi

# 根据镜像创建时间判断镜像是否构建成功
cd /${deploy_folder} && ./docker-common.sh is_success_images ${docker_image_name} ${is_push_docker_repo}
# 子shell退出父shell
is_success_images_code=$?
if [[ "${is_success_images_code}" == 1 ]]; then
  exit 1
fi

# 检查容器是否存在 停止容器
cd /${deploy_folder} && ./docker-common.sh stop_docker ${docker_container_name}

# 判断是否存在lsof 不存在安装
cd /${deploy_folder} && ./docker-common.sh exist_lsof

# 判断端口号是否存在  存在退出
cd /${deploy_folder} && ./docker-common.sh exist_port ${host_port}
# 子shell退出父shell
exist_port_code=$?
if [[ "${exist_port_code}" == 1 ]]; then
  exit 1
fi

# 调试端口已存在退出
if [[ ${is_prod} == false && ${remote_debug_port} ]]; then
  remote_debug_port_exist=$(lsof -i:"${remote_debug_port}")
  if [[ ${remote_debug_port_exist} ]]; then
    echo -e "\033[31m远程调试端口${remote_debug_port}已存在, Shell自动化脚本退出运行, 端口冲突停止部署 ❌ \033[0m "
    exit 1 # 1:非正常运行导致退出程序
  fi
fi

echo "👨‍💻 启动运行Docker容器 环境: ${env_mode} 映射端口: ${host_port}:${expose_port}"
docker run -d --restart=always -p ${host_port}:${expose_port} \
  -e "PROJECT_NAME=${project_name}" \
  -m ${docker_memory} --log-opt ${docker_log_opts} --log-opt max-file=1 ${dynamic_run_args} \
  -e "REMOTE_DEBUGGING_PARAM=${remote_debugging_param}" \
  -v /${deploy_folder}/${project_name}/logs:/logs \
  --name ${docker_container_name} ${docker_image_name}

set +x # 关闭shell命令打印模式

# 根据镜像名称获取所有ID并删除镜像
cd /${deploy_folder} && ./docker-common.sh remove_docker_image ${docker_image_ids}

# 删除所有悬空的镜像
if [[ ${is_push_docker_repo} == true ]]; then
  cd /${deploy_folder} && ./docker-common.sh remove_docker_dangling_images
fi
