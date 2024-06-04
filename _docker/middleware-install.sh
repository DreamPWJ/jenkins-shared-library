#!/usr/bin/env bash
# Author: 潘维吉
# Description: 中间件服务初始化

# 自动创建中间件配置目录并授权
mkdir -p /my/nginx/config && mkdir -p /my/nginx/ssl && mkdir -p /my/xxl-job
chmod +x /my/nginx/config && chmod +x /my/xxl-job

echo -e "\033[34m 🚨 是否将相应配置文件放在服务器规定的目录下, 如/my/nginx/config/default.conf、nginx.conf配置、ssl证书、job平台/my/xxl-job/application.properties等, 并进行中间件服务安装工作[y/n]: \033[0m"
read answer
case ${answer} in
Y | y)
  if [[ ! $(command -v docker) ]]; then
    echo -e "\033[34m请先初始化Docker环境  ✘ \033[0m"
    exit
  fi

  echo -e "\033[32m中间件服务安装开始  \033[0m"

  echo "基于Docker安装部署Nginx"
  docker pull nginx
  # 放开端口范围  -p 7000-8000:7000-8000  -p 8080:443
  sudo docker run -d --restart=always -p 80:80 -p 443:443 -p 4000:4000 --name proxy-nginx -v /etc/localtime:/etc/localtime:ro \
    -v /my/nginx/config/nginx.conf:/etc/nginx/nginx.conf:ro -v /my/nginx/config/default.conf:/etc/nginx/conf.d/default.conf:ro \
    -v /my/nginx/ssl:/etc/nginx/ssl -v /my/nginx/html:/usr/share/nginx/html -v /etc/letsencrypt:/etc/letsencrypt \
    --log-opt max-size=1024m --log-opt max-file=1 \
    -v /my/nginx/logs:/var/log/nginx nginx

  echo "安装Let's Encrypt客户端Certbot"
  sudo apt-get install -y certbot || true
  sudo yum install -y certbot || true
  # sudo pip install certbot || true
  certbot --version

  echo "基于Docker安装部署Redis"
  docker pull redis:latest

  sudo docker run -d --restart=always -p 6379:6379 --name redis -v /my/redis/data:/data -v /etc/localtime:/etc/localtime:ro \
    -m 4096m --log-opt max-size=1024m --log-opt max-file=1 \
    redis:latest redis-server --appendonly yes --requirepass "panweiji2020"

  echo "基于Docker安装部署RabbitMQ"
  docker pull rabbitmq:3.8-management

  sudo docker run -d --restart=always -p 5672:5672 -p 15672:15672 --name rabbitmq -v /my/rabbitmq:/var/lib/rabbitmq -v /etc/localtime:/etc/localtime:ro \
    -m 2048m --log-opt max-size=1024m --log-opt max-file=1 \
    -e RABBITMQ_DEFAULT_VHOST=/ -e RABBITMQ_DEFAULT_USER=root -e RABBITMQ_DEFAULT_PASS=panweiji2020 rabbitmq:3.8-management

  echo "基于Docker安装部署Zookeeper"
  docker pull zookeeper

  docker run -d --restart=always -p 2181:2181 \
    -e "ZOO_INIT_LIMIT=10" -e TZ="Asia/Shanghai" \
    -v /my/zookeeper/data:/data \
    -m 2048m --log-opt max-size=1024m --log-opt max-file=1 \
    --privileged=true --name zookeeper zookeeper:latest

  echo "基于Docker安装部署分布式任务调度平台XXL-JOB"
  docker pull xuxueli/xxl-job-admin:2.4.1

  # 默认密码是admin  123456  访问添加/xxl-job-admin路径
  docker run -d --restart=always -p 8081:8080 \
    -e PARAMS="--spring.config.location=/application.properties" \
    -v /my/xxl-job/applogs:/data/applogs -v /my/xxl-job/application.properties:/application.properties \
    -m 2048m --log-opt max-size=1024m --log-opt max-file=1 \
    --name xxl-job-admin xuxueli/xxl-job-admin:2.4.1

  echo -e "\033[32m中间件服务全部安装结束  ✔ \033[0m"
  ;;
N | n)
  echo -e "\033[35m不进行中间件服务安装工作 \033[0m"
  ;;
*)
  echo -e "\033[31m输入错误，请输入[y/n] or [Y/N] \033[0m"
  ;;
esac
