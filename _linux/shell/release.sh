#!/usr/bin/env bash
# Author: 潘维吉
# Description: 执行宿主机发布部署shell脚本

echo -e "\033[32m执行宿主机部署Java语言脚本  👇 \033[0m"
# chmod +x release.sh 给shell脚本执行文件可执行权限
# 可采用$0,$1,$2..等方式获取脚本命令行传入的参数  执行脚本  sudo ./release.sh

echo "使用getopts的方式进行shell参数传递"
while getopts ":a:b:c:d:e:f:g:h:i:k:l:m:n:o:p:q:y:z:" opt; do
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

# 非生产环境限制容器CPU占用 防止同时部署情况导致其他服务无法访问
if [[ ${is_prod} == false ]]; then
  cup_num=$(cat /proc/cpuinfo | grep "processor" | wc -l)
  echo "逻辑CPU的个数: ${cup_num}"
  # limit_cup_num=$(echo "scale=4; ${cup_num} - 0.3" | bc) # 浮点数计算  bc命令可能不存在
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
echo "运行动态参数: ${docker_java_opts} ${docker_memory} ${docker_log_opts} ${dynamic_run_args}"
echo "远程调试参数: ${remote_debugging_param}"

# 根据镜像名称查询镜像ID 用于删除无效的镜像
docker_image_ids=$(docker images -q --filter reference=${docker_image_name})

# 获取系统CPU使用率 如果CPU占用高 则排队延迟部署 避免并发部署等导致资源阻塞
cd /${deploy_folder} && ./docker-common.sh get_cpu_rate && cd /${deploy_file}

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
  -e "REMOTE_DEBUGGING_PARAM=${remote_debugging_param}" -e HOST_NAME=$(hostname)  \
  -v /${deploy_folder}/${project_name}/logs:/logs \
  --name ${docker_container_name} ${docker_image_name}

# 根据镜像名称获取所有ID并删除镜像
cd /${deploy_folder} && ./docker-common.sh remove_docker_image ${docker_image_ids}

#可变参数变量
languageType="javac" #支持 java,javac,netcore 发布
#参数值由pom文件传递
baseZipName="${package-name}-${activeProfile}" #压缩包名称 publish-test.zip的publish
packageName="${package-name}"                  #命令启动包名 xx.jar的xx
mainclass="${boot-main}"                       #java -cp启动时，指定main入口类;命令：java -cp conf;lib\*.jar;${packageName}.jar ${mainclass}

#例子
# baseZipName="publish-test" #压缩包名称 publish-test.zip的publish
# packageName="publish" #命令启动包名 publish.jar的xx

#固定变量
basePath=$(
  cd $(dirname $0)/
  pwd
)
baseZipPath="${basePath}/${baseZipName}.zip" #压缩包路径
baseDirPath="${basePath}"                    #解压部署磁盘路径
pid=                                         #进程pid

#解压
function unzip() {
  echo "解压---------------------------------------------"
  echo "压缩包路径：${baseZipPath}"
  if [ ! $(find ${baseZipPath}) ]; then
    echo "不存在压缩包：${baseZipPath}"
  else
    echo "解压磁盘路径：${baseDirPath}/${baseZipName}"
    echo "开始解压..."

    #解压命令
    unzip -od ${baseDirPath}/${baseZipName} ${baseZipPath}

    #设置执行权限
    chmod +x ${baseDirPath}/${baseZipName}/${packageName}

    echo "解压完成。"
  fi
}

#检测pid
function getPid() {
  echo "检测状态---------------------------------------------"
  pid=$(ps -ef | grep -n ${packageName} | grep -v grep | awk '{print $2}')
  if [ ${pid} ]; then
    echo "运行pid：${pid}"
  else
    echo "未运行"
  fi
}

#启动程序
function start() {
  #启动前，先停止之前的
  stop
  if [ ${pid} ]; then
    echo "停止程序失败，无法启动"
  else
    echo "启动程序---------------------------------------------"

    #选择语言类型
    read -p "输入程序类型(java,javac,netcore)，下一步按回车键(默认：${languageType})：" read_languageType
    if [ ${read_languageType} ]; then
      languageType=${read_languageType}
    fi
    echo "选择程序类型：${languageType}"

    #进入运行包目录
    cd ${baseDirPath}/${baseZipName}

    #分类启动
    if [ "${languageType}" == "javac" ]; then
      if [ ${mainclass} ]; then
        nohup java -cp conf:lib\*.jar:${packageName}.jar ${mainclass} >${baseDirPath}/${packageName}.out 2>&1 &
        #nohup java -cp conf:lib\*.jar:${packageName}.jar ${mainclass} >/dev/null 2>&1 &
      fi
    elif [ "${languageType}" == "java" ]; then
      nohup java -jar ${baseDirPath}/${baseZipName}/${packageName}.jar >/dev/null 2>&1 &
      # java -jar ${baseDirPath}/${baseZipName}/${packageName}.jar
    elif [ "${languageType}" == "netcore" ]; then
      #nohup dotnet run ${baseDirPath}/${baseZipName}/${packageName} >/dev/null 2>&1 &
      nohup ${baseDirPath}/${baseZipName}/${packageName} >/dev/null 2>&1 &
    fi

    #查询是否有启动进程
    getPid
    if [ ${pid} ]; then
      echo "已启动"
      #nohup日志
      tail -n 50 -f ${baseDirPath}/${packageName}.out
    else
      echo "启动失败"
    fi
  fi
}

#停止程序
function stop() {
  getPid
  if [ ${pid} ]; then
    echo "停止程序---------------------------------------------"
    kill -9 ${pid}

    getPid
    if [ ${pid} ]; then
      #stop
      echo "停止失败"
    else
      echo "停止成功"
    fi
  fi
}

#启动时带参数，根据参数执行
if [ ${#} -ge 1 ]; then
  case ${1} in
  "start")
    start
    ;;
  "restart")
    start
    ;;
  "stop")
    stop
    ;;
  "unzip")
    #执行解压
    unzip
    #执行启动
    start
    ;;
  *)
    echo "${1}无任何操作"
    ;;
  esac
else
  echo "
    command如下命令：
    unzip：解压并启动
    start：启动
    stop：停止进程
    restart：重启

    示例命令如：./release start
    "
fi
