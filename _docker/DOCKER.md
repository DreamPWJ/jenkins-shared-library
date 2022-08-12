#### 基于Docker安装部署nginx
docker pull nginx

#### 启动运行nginx容器 
sudo docker run -d --restart=always -p 80:80 -p 443:443 --name proxy-nginx  -v /etc/localtime:/etc/localtime:ro \
-v /my/nginx/config/nginx.conf:/etc/nginx/nginx.conf:ro  -v /my/nginx/config/default.conf:/etc/nginx/conf.d/default.conf:ro \
-v /my/nginx/ssl:/etc/nginx/ssl -v /my/nginx/html:/usr/share/nginx/html  -v /etc/letsencrypt:/etc/letsencrypt   \
--log-opt max-size=1024m --log-opt max-file=1   \
-v /my/nginx/logs:/var/log/nginx  nginx

#### 从Docker Hub里拉取redis镜像来部署
docker pull redis:latest

#### redis docker容器启动 -d开启 守护进程Daemon模式 -v指定数据持久化存储宿主机位置 --requirepass "mypassword"设置密码 --appendonly yes数据持久化
sudo docker run -d --restart=always -p 6379:6379 --name redis -v /my/redis/data:/data -v /etc/localtime:/etc/localtime:
ro   \
redis:latest redis-server --appendonly yes --requirepass "panweiji2020"

#### 从Docker Hub里拉取RabbitMQ镜像来部署
docker pull rabbitmq:management

#### RabbitMQ docker容器启动 -d开启 守护进程Daemon模式 指定--hostname默认为主机名存储数据 image启动容器的时候会自动生成hostname
#### 5672端口对应的是amqp，25672端口对应的是clustering，15672端口对应的是http（也就是登录RabbitMQ后台管理端口）

sudo docker run -d --restart=always -p 5672:5672 -p 15672:15672 --name rabbitmq -v /my/rabbitmq:/var/lib/rabbitmq -v
/etc/localtime:/etc/localtime:ro \
--log-opt max-size=1024m --log-opt max-file=1   \
-e RABBITMQ_DEFAULT_USER=root -e RABBITMQ_DEFAULT_PASS=panweiji2020 rabbitmq:management

#### ZooKeeper分布式应用程序调度服务
docker pull zookeeper

docker run -d --restart=always  -p 2181:2181 \
-e "ZOO_INIT_LIMIT=10"  -e TZ="Asia/Shanghai" \
-v /my/zookeeper/data:/data \
--log-opt max-size=1024m --log-opt max-file=1   \
--privileged=true  --name zookeeper  zookeeper:latest

#### EMQX物联网MQTT服务器
docker pull emqx/emqx

docker run -d --restart=always  -p 18083:18083 -p 1883:1883 \
-e TZ="Asia/Shanghai" \
-v /my/emqx/:/opt/emqx \
--log-opt max-size=1024m --log-opt max-file=1 \
--name emqx  emqx/emqx:latest

#### 安装 分布式任务调度平台XXL-JOB服务 在浏览器中使用http://47.105.198.77:8081/xxl-job-admin/ 默认用户名和密码 admin 123456
docker pull xuxueli/xxl-job-admin:2.1.2

docker run -d --restart=always -p 8081:8080  \
-e PARAMS="--spring.config.location=/application.properties" \
-v /my/xxl-job/applogs:/data/applogs  -v /my/xxl-job/application.properties:/application.properties \
--log-opt max-size=1024m --log-opt max-file=1   \
--name xxl-job-admin xuxueli/xxl-job-admin:2.1.2



