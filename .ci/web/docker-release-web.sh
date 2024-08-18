#!/bin/bash
# Author: 潘维吉
# Description: 执行Docker发布部署shell脚本

echo -e "\033[32m执行Docker部署Web脚本  👇 \033[0m"
# 可采用$0,$1,$2..等方式获取脚本命令行传入的参数  执行脚本  sudo ./docker-release-web.sh project-name app 4200 80 test
# 项目名称
#project_name_prefix=$1

echo "使用getopts的方式进行shell参数传递"
while getopts ":a:b:c:d:e:f:g:h:i:k:" opt; do
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
    echo "deploy_folder=$OPTARG"
    deploy_folder=$OPTARG # 服务器上部署所在的文件夹名称
    ;;
  g)
    echo "npm_package_folder=$OPTARG"
    npm_package_folder=$OPTARG # Web项目NPM打包代码所在的文件夹名
    ;;
  h)
    echo "web_strip_components=$OPTARG"
    web_strip_components=$OPTARG # Web项目解压到指定目录层级
    ;;
  i)
    echo "is_push_docker_repo=$OPTARG"
    is_push_docker_repo=$OPTARG # 是否上传镜像到docker容器仓库
    ;;
  k)
    echo "docker_repo_registry_and_namespace=$OPTARG"
    docker_repo_registry_and_namespace=$OPTARG # docker容器仓库地址和命名空间拼接
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

# 复制配置文件
cd /${deploy_folder}/web && cp -p default.conf ${deploy_file}/
cd /${deploy_folder}/web && cp -p nginx.conf ${deploy_file}/
#cp -r ssl/ ${deploy_file}/

# 进入部署文件
cd ${deploy_file}
pwd
# 进入部署文件所在目录并解压部署资源
# tar -xzvf ${npm_package_folder}.tar.gz  && rm -f ${npm_package_folder}.tar.gz

# 根据镜像名称查询镜像ID 用于删除无效的镜像
docker_image_ids=$(docker images -q --filter reference=${docker_image_name})

# 获取系统CPU使用率 如果CPU占用高 则排队延迟部署 避免并发部署等导致资源阻塞
cd /${deploy_folder} && ./docker-common.sh get_cpu_rate && cd /${deploy_file}
# 获取系统磁盘资源 如果硬盘资源不足 停止容器构建或自动清理空间
cd /${deploy_folder} && ./docker-common.sh get_disk_space && cd /${deploy_file}

set -x # 开启shell命令打印模式

# 是否是远程镜像仓库方式
if [[ ${is_push_docker_repo} == false ]]; then
  echo "🏗️  开始构建Docker镜像(无缓存构建)"
  docker build -t ${docker_image_name} \
    --build-arg DEPLOY_FOLDER=${deploy_folder} --build-arg NPM_PACKAGE_FOLDER=${npm_package_folder} \
    --build-arg PROJECT_NAME=${project_name} --build-arg WEB_STRIP_COMPONENTS=${web_strip_components} \
    -f /${deploy_folder}/web/Dockerfile . --no-cache
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

echo -e "\033[32m 👨‍💻 启动运行Docker容器  映射端口: ${host_port}:${expose_port} \033[0m"
docker run -d --restart=on-failure:6 -p ${host_port}:${expose_port} \
  --log-opt max-size=100m --log-opt max-file=1  \
  -m 4G --name ${docker_container_name} ${docker_image_name}

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

#  前端命令和shell命令都可使用 && 将多个命令连接执行
#  npm install && npm run build
#  tar -zcvf dist.tar.gz dist


# 手动单独部署情况 不依赖自动化CI/CD和自定义Dockerfile情况
# docker run -d --restart=always -p 8008:80 --name project-name-web \
# -v /my/project-name-web/default.conf:/etc/nginx/conf.d/default.conf:ro \
# -v /my/project-name-web/dist:/usr/share/nginx/html:ro  nginx:stable