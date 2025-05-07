#!/bin/bash
# Author: 潘维吉
# Description: 执行Docker发布部署shell脚本

echo -e "\033[32m执行Docker部署Java语言脚本  👇 \033[0m"
# chmod +x docker-release.sh 给shell脚本执行文件可执行权限
# 可采用$0,$1,$2..等方式获取脚本命令行传入的参数  执行脚本  sudo ./docker-release.sh

echo "使用getopts的方式进行shell参数传递"
while getopts ":a:b:c:d:e:f:g:h:i:k:l:m:n:o:p:q:r:s:t:u:v:w:x:y:z:" opt; do
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
    docker_memory=$OPTARG # 容器最大内存限制
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
  q)
    echo "java_framework_type=$OPTARG"
    java_framework_type=$OPTARG # Java框架类型 1. Spring Boot 2. Spring MVC
    ;;
  r)
    echo "tomcat_version=$OPTARG"
    tomcat_version=$OPTARG # 自定义Tomcat版本
    ;;
  s)
    echo "jdk_publisher=$OPTARG"
    jdk_publisher=$OPTARG # jdk版本发行商
    ;;
  t)
    echo "is_spring_native=$OPTARG"
    is_spring_native=$OPTARG # 是否打包Spring Native原生镜像
    ;;
  u)
    echo "is_source_code_deploy=$OPTARG"
    is_source_code_deploy=$OPTARG # 是否源码直接部署 无需打包 只需要压缩上传到服务器上执行命令启动
    ;;
  v)
    echo "custom_startup_command=$OPTARG"
    custom_startup_command=$OPTARG # 自定义服务部署启动命令
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
# docker镜像名称 如果未指定标签，则默认使用`latest`
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

# 是否开启BuildKit新引擎
cd /${deploy_folder} && ./docker-common.sh is_enable_buildkit

# 复制字体文件
#if [[ ! -f "*.ttc" ]]; then
#  touch *.ttc && chmod +x *.ttc # 兼容不需字体的项目 创建空文件  临时方案
#fi
#cp -p *.ttc ${deploy_file}/

echo "进入部署文件目录构建镜像: ${deploy_file}"
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

# 动态调试端口 IDEA远程调试断点默认会挂起整个VM, 任何一个线程进入断点都会导致所有的请求被阻塞。影响程序的正常使用, 生产环境禁用
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

# 限制容器CPU占用 防止同时部署情况导致其他服务无法访问
#if [[ ${is_prod} == false ]]; then
  # cup_num=$(cat /proc/cpuinfo | grep "processor" | wc -l)
  # echo "逻辑CPU的个数: ${cup_num}"
  cpu_cores=$(nproc)
  echo "物理CPU的个数: ${cpu_cores}"
  cpus_limit=$(awk "BEGIN {print $cpu_cores * 0.8}") # 使用百分多少的资源 防止整个服务器资源被占用停机
  # limit_cup_num=$(echo "scale=4; ${cup_num} - 0.3" | bc) # 浮点数计算  bc命令可能不存

  dynamic_run_args=${dynamic_run_args}" --cpus=${cpus_limit} "
#fi

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
echo "运行动态参数: ${docker_java_opts} ${docker_memory} ${docker_log_opts} ${dynamic_run_args}"
echo "远程调试参数: ${remote_debugging_param}"

# 根据镜像名称查询镜像ID 用于删除无效的镜像
docker_image_ids=$(docker images -q --filter reference=${docker_image_name})

# 获取系统CPU使用率 如果CPU占用高 则排队延迟部署 避免并发部署等导致资源阻塞
cd /${deploy_folder} && ./docker-common.sh get_cpu_rate && cd /${deploy_file}
# 获取系统磁盘资源 如果硬盘资源不足 停止容器构建或自动清理空间
cd /${deploy_folder} && ./docker-common.sh get_disk_space && cd /${deploy_file}
# 重命名上一个版本镜像tag 用于回滚版本控制策略
cd /${deploy_folder} && ./docker-common.sh set_docker_rollback_tag ${docker_image_name} && cd /${deploy_file}

set -x # 开启shell命令打印模式

# 是否是远程镜像仓库方式
if [[ ${is_push_docker_repo} == false ]]; then
  echo "🏗️  开始构建Docker镜像(无缓存构建)"

  docker_pull_image_name=${jdk_publisher}:${jdk_version}
  if [[ ${java_framework_type} == 1 ]]; then
     docker_file_name="Dockerfile" # 默认Spring Boot框架 jar包
  fi
  if [[ ${java_framework_type} == 2 ]]; then
     docker_file_name="Dockerfile.mvc" # Spring MVC框架 war包
     docker_pull_image_name=tomcat:${tomcat_version}-jre8
  fi
  if [[ ${is_spring_native} == true ]]; then
     docker_file_name="Dockerfile.native" # Spring Native原生镜像直接执行文件
  fi
  if [[ ${is_source_code_deploy} == true ]]; then
     docker_file_name="Dockerfile.code" # 源码直接部署 无需打包 只需要压缩上传到服务器上执行命令启动
  fi

   # 拉取基础镜像避免重复下载
  [ -z "$(docker images -q ${docker_pull_image_name})" ] && docker pull ${docker_pull_image_name} || echo "基础镜像 ${docker_pull_image_name} 已存在 无需重新pull拉取镜像"

  # 对于简单项目无需重复构建镜像  将部署文件 docker run -v 做挂载映射 直接重启容器即可
    docker build -t ${docker_image_name} \
    --build-arg DEPLOY_FOLDER=${deploy_folder} --build-arg PROJECT_NAME=${project_name} \
    --build-arg EXPOSE_PORT="${build_expose_ports}" --build-arg JDK_PUBLISHER=${jdk_publisher} --build-arg JDK_VERSION=${jdk_version} \
    --build-arg TOMCAT_VERSION=${tomcat_version} --build-arg JAVA_OPTS="-Xms128m ${docker_java_opts}" \
    -f /${deploy_folder}/${docker_file_name} . --no-cache
else
  docker_image_name=${docker_repo_registry_and_namespace}/${project_name_prefix}/${project_type}-${env_mode}
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

echo -e "\033[32m 👨‍💻  启动运行Docker容器 环境: ${env_mode} 映射端口: ${host_port}:${expose_port} \033[0m"
# --pid=host 使用宿主机命名空间 方便容器获取宿主机所有进程 解决多个docker节点RocketMQ重复消费消息等问题
docker run -d --restart=on-failure:16 -p ${host_port}:${expose_port} --privileged=true --pid=host \
  -e "SPRING_PROFILES_ACTIVE=${env_mode}" -e "PROJECT_NAME=${project_name}" -e "DOCKER_SERVICE_PORT=${build_expose_ports}" \
  -e "JAVA_OPTS=-Xms128m ${docker_java_opts}" -m ${docker_memory} --log-opt ${docker_log_opts} --log-opt max-file=1  ${dynamic_run_args} \
  -e "REMOTE_DEBUGGING_PARAM=${remote_debugging_param}" -e HOST_NAME=$(hostname) \
  -e "CUSTOM_STARTUP_COMMAND=${custom_startup_command}" \
  -v /${deploy_folder}/${project_name}/logs:/logs \
  --name ${docker_container_name} ${docker_image_name}

set +x # 关闭shell命令打印模式

#docker_exited_container=$(docker ps --all -q -f status=exited)
#if [[ ${docker_exited_container} ]]; then
#  echo "删除具有exited状态的容器"
#  docker rm ${docker_exited_container} || true
#fi

# 根据镜像名称获取所有ID并删除镜像
cd /${deploy_folder} && ./docker-common.sh remove_docker_image ${docker_image_ids}

# 删除所有悬空的镜像
if [[ ${is_push_docker_repo} == true ]]; then
  cd /${deploy_folder} && ./docker-common.sh remove_docker_dangling_images
fi

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


# 👉 手动单独部署Docker应用场景 不依赖自动化CI/CD和自定义Dockerfile情况 更高版本JDK使用镜像 如 amazoncorretto:21
# docker run -d --restart=always -p 8080:8080 --name project-name-java \
# -v "$(pwd)/app.jar:/app/app.jar"  \
# java -jar /app/app.jar

