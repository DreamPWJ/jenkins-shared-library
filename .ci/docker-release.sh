#!/usr/bin/env bash
# Author: 潘维吉
# Description: 执行Docker发布部署shell脚本

echo -e "\033[32m执行Docker部署Java语言脚本  👇 \033[0m"
# chmod +x docker-release.sh 给shell脚本执行文件可执行权限
# 可采用$0,$1,$2..等方式获取脚本命令行传入的参数  执行脚本  sudo ./docker-release.sh

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
    env_mode=$OPTARG # 环境模式 如 dev test prod等
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
  p)
    echo "docker_file_run_command=$OPTARG"
    docker_file_run_command=$OPTARG                             # Dockerfile内 run命令动态传入
    docker_file_run_command=${docker_file_run_command//'~'/' '} # 字符串替换
    echo "docker_file_run_command=$docker_file_run_command"
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

# 复制字体文件
#if [[ ! -f "msyh.ttc" ]]; then
#  touch msyh.ttc && chmod +x msyh.ttc # 兼容不需字体的项目 创建空文件  临时方案
#fi
#cp -p msyh.ttc ${deploy_file}/

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

# 动态调试端口 IDEA断点默认会挂起整个VM ,任何一个线程进入断点都会导致所有的请求被阻塞。可能影响生产环境的正常使用
remote_debugging_param=""
if [[ ${is_prod} == false && ${remote_debug_port} ]]; then
  build_expose_ports=${build_expose_ports}" 5005 "
  dynamic_run_args=${dynamic_run_args}" -p ${remote_debug_port}:5005 "
  # 从Java9以来，JDWP默认只支持到本地，address=5005 只能在本地进行调试，并不能连接到远程 远程进行调试则在address参数之前增加*:
  remote_debugging_param="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address="
  # JDK版本大于或等于9
  if [[ ${jdk_version} -ge 9 ]]; then
    remote_debugging_param=${remote_debugging_param}"*:5005"
  else
    remote_debugging_param=${remote_debugging_param}":5005"
  fi
fi

# 非生产环境限制容器CPU占用 防止同时部署情况导致其他服务无法访问
if [[ ${is_prod} == false ]]; then
  cup_num=$(cat /proc/cpuinfo | grep "processor" | wc -l)
  echo "逻辑CPU的个数: ${cup_num}"
  limit_cup_num=$(echo "scale=4; ${cup_num} - 0.3" | bc) # 浮点数计算
  dynamic_run_args=${dynamic_run_args}" --cpus=${limit_cup_num} "
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
echo "运行动态参数: ${docker_java_opts} ${docker_memory}  ${docker_log_opts} ${dynamic_run_args}"
echo "远程调试参数: ${remote_debugging_param}"

# 根据镜像名称查询镜像ID 用于删除无效的镜像
docker_image_ids=$(docker images -q --filter reference=${docker_image_name})

# 获取系统CPU使用率 如果CPU占用高 则排队延迟部署 避免并发部署等导致资源阻塞
cd /${deploy_folder} && ./docker-common.sh get_cpu_rate && cd /${deploy_file}

if [[ ${is_push_docker_repo} == false ]]; then
  echo "🏗️  开始构建Docker镜像(无缓存构建)"
  docker build -t ${docker_image_name} \
    --build-arg DEPLOY_FOLDER=${deploy_folder} --build-arg PROJECT_NAME=${project_name} \
    --build-arg EXPOSE_PORT="${build_expose_ports}" --build-arg JDK_VERSION=${jdk_version} \
    -f /${deploy_folder}/Dockerfile . --no-cache
else
  docker_image_name=${docker_repo_registry_and_namespace}/${project_name_prefix}-${project_type}-${env_mode}
fi

# 根据镜像创建时间判断镜像是否构建成功
cd /${deploy_folder} && ./docker-common.sh is_success_images ${docker_image_name}
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
# 动态修改数据库连接 -e PARAMS="--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/health?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowMultiQueries=true&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true  --spring.datasource.username=root"
docker run -d --restart=always -p ${host_port}:${expose_port} \
  -e "SPRING_PROFILES_ACTIVE=${env_mode}" -e "PROJECT_NAME=${project_name}" \
  -e "JAVA_OPTS=-Xms128m ${docker_java_opts}" -m ${docker_memory} --log-opt ${docker_log_opts} --log-opt max-file=1 ${dynamic_run_args} \
  -e "REMOTE_DEBUGGING_PARAM=${remote_debugging_param}" \
  -v /${deploy_folder}/${project_name}/logs:/logs \
  --name ${docker_container_name} ${docker_image_name}

#docker_exited_container=$(docker ps --all -q -f status=exited)
#if [[ ${docker_exited_container} ]]; then
#  echo "删除具有exited状态的容器"
#  docker rm ${docker_exited_container} || true
#fi

# 根据镜像名称获取所有ID并删除镜像
cd /${deploy_folder} && ./docker-common.sh remove_docker_image ${docker_image_ids}

# 删除所有悬空的镜像
# cd /${deploy_folder} && ./docker-common.sh remove_docker_dangling_images

# 并发构建镜像删除none的镜像可能导致错误
#docker_none_images=$(docker images | grep "none" | awk '{print $3}')
#if [[ ${docker_none_images} ]]; then
#  echo "删除名称或标签为none的镜像"
#  docker rmi ${docker_none_images} --no-prune
#fi

#echo "清除所有未使用或悬挂的图像 容器 卷和网络"
#docker system prune -a

#echo "删除2小时以上未被使用的镜像"
#docker image prune -a --force --filter "until=2h"

#  Jenkins单独指定模块构建 -pl指定项目名 -am 同时构建依赖项目模块
#  (mvn) clean install -pl app -am -Dmaven.test.skip=true  跳过单元测试打包

# 提交检出均不转换
# git config --global core.autocrlf false