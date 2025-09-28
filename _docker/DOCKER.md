#### 基于Docker安装部署nginx
docker pull nginx:stable

#### 启动运行nginx容器  创建目录 mkdir -p /my/nginx/config && mkdir -p /my/nginx/ssl
sudo docker run -d --restart=always -p 80:80 -p 443:443/tcp -p 443:443/udp --name proxy-nginx  -v /etc/localtime:/etc/localtime:ro \
-v /my/nginx/config/nginx.conf:/etc/nginx/nginx.conf:ro  -v /my/nginx/config/default.conf:/etc/nginx/conf.d/default.conf:ro \
-v /my/nginx/ssl:/etc/nginx/ssl  -v /my/nginx/html:/usr/share/nginx/html  -v /etc/letsencrypt:/etc/letsencrypt  -v /my/nginx/logs:/var/log/nginx \
--log-opt max-size=200m --log-opt max-file=1   \
nginx:stable

#### 从Docker Hub里拉取Redis镜像来部署 
docker pull redis:latest

#### Redis docker容器启动 -d开启 守护进程Daemon模式 -v指定数据持久化存储宿主机位置 密码强度要求：8位及以上，包含大小写，字母，特殊符号 --requirepass "mypassword"设置密码 --appendonly yes数据持久化
sudo docker run -d --restart=always -p 6379:6379 --name redis -v /my/redis/data:/data -v /etc/localtime:/etc/localtime:ro  \
--cpus=2 -m 4096m redis:latest redis-server --appendonly yes --requirepass "admin@0633"

#### 从Docker Hub里拉取RabbitMQ镜像来部署
docker pull rabbitmq:management

#### RabbitMQ docker容器启动 -d开启 守护进程Daemon模式 指定--hostname默认为主机名存储数据 image启动容器的时候会自动生成hostname
#### 5672端口对应的是amqp，25672端口对应的是clustering，15672端口对应的是http（也就是登录RabbitMQ后台管理端口）
sudo docker run -d --restart=always -p 5672:5672 -p 15672:15672 --name rabbitmq \
-v /my/rabbitmq:/var/lib/rabbitmq -v /etc/localtime:/etc/localtime:ro \
--cpus=2 -m 2048m --log-opt max-size=200m --log-opt max-file=1   \
-e RABBITMQ_DEFAULT_USER=root -e RABBITMQ_DEFAULT_PASS=root@0633 rabbitmq:management

#### 从Docker Hub里拉取ElasticSearch镜像来部署
docker pull elasticsearch:9.1.4

touch /my/elasticsearch/config/elasticsearch.yml 
sudo docker run -d --restart=always -p 9200:9200 -p 9300:9300 --name elasticsearch \
-v /my/elasticsearch/data:/usr/share/elasticsearch/data -v /my/elasticsearch/logs:/usr/share/elasticsearch/logs  \
-e "ES_JAVA_OPTS=-Xms1024m -Xmx1024m" -e "discovery.type=single-node" -e "xpack.security.enabled=true" -e "ELASTIC_PASSWORD=elastic@0633" \
--cpus=2 -m 4096m  --privileged  elasticsearch:9.1.4

#### ZooKeeper分布式应用程序调度服务
docker pull zookeeper

docker run -d --restart=always  -p 2181:2181 \
-e "ZOO_INIT_LIMIT=10"  -e TZ="Asia/Shanghai" \
-v /my/zookeeper/data:/data \
--cpus=2 -m 2048m --log-opt max-size=200m --log-opt max-file=1   \
--privileged=true  --name zookeeper  zookeeper:latest

#### EMQX物联网MQTT代理服务器 Dashboard地址http://127.0.0.1:18083  用户名 admin 与默认密码 public 建议更换默认密码防止被攻击 容器删除后丢失数据建议更换存储数据源写入 SHA2(CONCAT('salt', 'public')
##### 容器内默认配置文件 变更重启  /opt/emqx/etc/emqx.conf   emqx宿主机卷存储地址 /var/lib/docker/volumes/mqtt-emqx/_data/etc   安全letsencrypt ssl服务证书存放在etc/certs/下面生效
docker pull emqx/emqx:latest

- 启动临时容器并复制配置 先复制容器内默认配置到宿主机
docker run -d --name emqx_temp emqx/emqx:latest
docker cp emqx_temp:/opt/emqx /my/emqx
chmod -R 777 /my/emqx && chown -R 1000:1000 /my/emqx
docker rm -f emqx_temp
docker volume create mqtt-emqx && docker inspect mqtt-emqx

docker run -d --restart=always  -p 18083:18083 -p 1883:1883 -p 8083:8083 -p 8084:8084 -p 8883:8883  \
-e TZ="Asia/Shanghai" \
-v /my/emqx/data:/opt/emqx/data -v /my/emqx/etc:/opt/emqx/etc -v /my/emqx/log:/opt/emqx/log  -v /etc/letsencrypt:/etc/letsencrypt  \
--cpus=2 -m 4096m  --log-opt max-size=200m --log-opt max-file=1  \
--privileged --name emqx  emqx/emqx:latest

#### 安装 分布式任务调度平台XXL-JOB服务 在浏览器中使用http://ip:8081/xxl-job-admin/ 默认用户名 admin 密码 123456
docker pull xuxueli/xxl-job-admin:3.2.0

docker run -d --restart=always -p 8081:8080  \
-e PARAMS="--spring.config.location=/application.properties" \
-v /my/xxl-job/applogs:/data/applogs  -v /my/xxl-job/application.properties:/application.properties \
--cpus=2 -m 2048m --log-opt max-size=200m --log-opt max-file=1   \
--name xxl-job-admin xuxueli/xxl-job-admin:3.2.0

#### RocketMQ消息队列服务  官方文档： https://github.com/apache/rocketmq-docker
#### 已运行的容器动态修改内存限制 docker update --memory 1024m --memory-swap -1 rocketmq-broker
docker pull apache/rocketmq:latest

mkdir -p /my/rocketmq/broker/conf && sudo chmod 777  /my/rocketmq/ -R

docker run -d --restart=always -p 9876:9876 \
-v /my/rocketmq/server/logs:/home/rocketmq/logs \
-e TZ="Asia/Shanghai" -e "JAVA_OPT_EXT=-Xms1024m -Xmx1024m -Xmn128m" --cpus=2 -m 1048m \
--name rocketmq-server  apache/rocketmq:latest  \
sh mqnamesrv

docker run -d --restart=always -p 10909:10909 -p 10911:10911 -p 10912:10912 \
-v /my/rocketmq/broker/conf:/home/rocketmq/conf  -v /my/rocketmq/broker/logs:/home/rocketmq/logs -v /my/rocketmq/broker/store:/home/rocketmq/store \
-e TZ="Asia/Shanghai" -e "NAMESRV_ADDR=172.31.3.120:9876"  --privileged=true \
-e "JAVA_OPT_EXT=-Xms512m -Xmx1500m" --cpus=2 -m 1548m \
--name rocketmq-broker  apache/rocketmq:latest \
sh mqbroker -c /home/rocketmq/conf/broker.conf  && sudo chmod 777  /my/rocketmq/ -R

docker run -d --restart=always -p 6765:8080 \
-e TZ="Asia/Shanghai" -e "JAVA_OPTS=-Drocketmq.namesrv.addr=172.31.3.120:9876 -Xms512m -Xmx512m" --cpus=2 -m 1024m \
--name rocketmq-dashboard  apacherocketmq/rocketmq-dashboard:latest