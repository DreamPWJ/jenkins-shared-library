#### 设置镜像源  解决pull下载慢卡住问题  注意：镜像源不维护了可能导致latest不是最新的版本 
##### 可使用CloudFlare Workers方案做Docker镜像源网络代理  或阿里云账号下专属镜像加速但只针对阿里云产品有效 或者临时镜像源设置 如: docker pull docker.lanneng.tech/repo-name/images:version 
docker info
sudo cat <<EOF >/etc/docker/daemon.json
{
"registry-mirrors": [
"https://docker.m.daocloud.io",
"https://docker.1ms.run",
"https://docker.xuanyuan.me",
"https://docker.lanneng.tech",
"https://em1sutsj.mirror.aliyuncs.com"
],
"log-driver": "json-file",
"log-opts": {
"max-size": "10m",
"max-file": "3"
},
"data-root": "/var/lib/docker",
"storage-driver": "overlay2",
"live-restore": true,
"exec-opts": ["native.cgroupdriver=systemd"]
}
EOF
sudo systemctl reload docker # reload 不会重启 Docker 服务，但会使新的配置生效
sudo systemctl daemon-reload && sudo systemctl restart docker  # 导致当前运行的容器短暂中断 reload命令无效再执行
sudo journalctl -u docker.service  # 排查启动失败的原因

#### 还原Docker容器的启动run命令完整参数
get_command_4_run_container（完美方案）
docker pull cucker/get_command_4_run_container
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock cucker/get_command_4_run_container [容器ID]

#### 从Docker Hub里拉取mysql镜像来部署 密码强度要求：8位及以上，包含大小写，字母，特殊符号
docker pull mysql

#### MySql Docker容器启动 创建数据库密码等 跟宿主机器同样的时区配置 -v指定数据持久化存储宿主机位置  添加mysql配置参数 -v 
#### GROUP_CONCAT函数可拼接某个字段值成字符串 默认的分隔符是"," 默认最大长度为1024字节超过则会被截断 （-1为最大值或根据实际需求设置长度） 
#### convert(数据,char) CONCAT解决乱码

#### CREATE USER IF NOT EXISTS 'panweiji'@'%' IDENTIFIED WITH caching_sha2_password BY 'PanWeiJi@20250818!#' ;
#### GRANT all privileges ON *.* TO 'panweiji'@'%' ; flush privileges;

docker run -d --restart=always -p 3306:3306 --name mysql \
-e MYSQL_DATABASE=design -e MYSQL_ROOT_PASSWORD=Root@20250818!# \
-v /etc/localtime:/etc/localtime:ro -v /my/mysql/data:/var/lib/mysql \
mysql --group_concat_max_len=1024000000 --max_connections=6000 --max_connect_errors=2000 --lower_case_table_names=1

#### 安装Postgres数据库
docker pull postgres

docker run -d --restart=always  -p 5432:5432 --name postgres  -v /my/postgresql:/var/lib/postgresql -v /etc/localtime:/etc/localtime:ro  \
-e POSTGRES_USER=root -e POSTGRES_PASSWORD=123456 -e POSTGRES_DB=design  postgres

#### 安装MongoDB数据库
docker pull mongo:latest

docker run -d --restart=always -p 27017:27017 \
--name mongodb -v /etc/localtime:/etc/localtime:ro  -v /my/mongodb/data:/data/db \
-e MONGO_INITDB_ROOT_USERNAME=root -e MONGO_INITDB_ROOT_PASSWORD=123456 -e MONGO_INITDB_DATABASE=design \
mongo

#### 从Docker Hub里拉取Jenkins镜像最新LTS版来部署
docker pull jenkins/jenkins:lts

#### 添加挂载映射本地数据卷权限 sudo chown -R 1000:1000 /my/jenkins  将宿主机的docker命令挂载到容器中  -v /my/jenkins/plugins.txt:/var/jenkins_home/plugins.txt
sudo docker run -d --restart=always -p 8000:8080 -p 50000:50000 \
-u root --cpus=2 -m 4096m  \
-v /etc/localtime:/etc/localtime:ro -v $(which bash):/bin/bash  \
-v $(which docker):/usr/bin/docker -v /var/run/docker.sock:/var/run/docker.sock \
-v /my/jenkins:/var/jenkins_home -v /my/jenkins/ssh:/root/.ssh  \
-e JAVA_OPTS="-Duser.timezone=Asia/Shanghai"  \
-v "$HOME":/home --privileged --name jenkins jenkins/jenkins:lts \
&& sudo chown -R 1000:1000 /my/jenkins

#### 可使用Jenkins Configuration as Code Plugin保存复用配置 与 一键安装所有插件 jenkins-plugin-cli --plugin-file plugins.txt
docker exec -it jenkins bash -c "jenkins-plugin-cli --plugin-file /var/jenkins_home/plugins.txt" && docker restart jenkins

#### 基于Docker安装部署GitLab系统镜像
#### 从Docker Hub里拉取GitLab镜像最新社区版来部署
docker pull gitlab/gitlab-ce

#### 启动运行容器
sudo docker run -d --restart=always -p 8000:80  --cpus=2 -m 4096m --name gitlab-ce \
-v /my/gitlab/config:/etc/gitlab -v /my/gitlab/logs:/var/log/gitlab -v /my/gitlab/data:/var/opt/gitlab  \
gitlab/gitlab-ce:latest

#### 基于Docker安装部署大模型VLLM、Ollama、Open WebUI、Dify容器镜像
docker pull vllm/vllm-openai:latest
docker pull ollama/ollama
docker pull ghcr.io/open-webui/open-webui:main

##### VLLM高性能部署大模型
docker run -d --restart=always -p 8008:8000 --name deepseek-vllm --gpus all --shm-size=1g -v /my/deepseek/cache:/root/.cache/huggingface -e HF_ENDPOINT=https://hf-mirror.com  \
vllm/vllm-openai:v0.16.0-cu130  --model deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B --trust-remote-code --host 0.0.0.0 --port 8000 --gpu-memory-utilization 0.3 --dtype half --max-model-len 2048
##### Ollama只有CPU模式部署
docker run -d --restart always -p 11434:11434 --cpus=8 -m 16096m -v /my/ollama:/root/.ollama --name ollama ollama/ollama
docker exec -it ollama  ollama run deepseek-r1:7b
##### Open WebUI部署
docker run -d --restart always -p 3100:8080 --cpus=2 -m 4096m --add-host=host.docker.internal:host-gateway -v /my/ollama:/root/.ollama \
 -v /my/open-webui:/app/backend/data --name open-webui ghcr.io/open-webui/open-webui:main

#### 安装 sonar代码质量检测服务 默认用户名密码都是admin  如果docker启动报错宿主机执行 sysctl -w vm.max_map_count=262144 
sudo docker pull sonarqube:community  

sudo docker run -d --restart=always --name sonarqube -p 9100:9000 --cpus=2 -m 4096m \
-v /etc/localtime:/etc/localtime:ro \
-e SONARQUBE_JDBC_USERNAME=root -e SONARQUBE_JDBC_PASSWORD=123456   \
-e SONARQUBE_JDBC_URL="jdbc:postgresql://127.0.0.1:5432/sonar?useUnicode=true&characterEncoding=utf8&rewriteBatchedStatements=true&useConfigs=maxPerformance&useSSL=false"  \
sonarqube:community  && sysctl -w vm.max_map_count=262144

#### 搭建私有docker仓库 http://ip:5000/v2
docker run -d --restart=always -p 5000:5000 -v /my/docker_registry:/var/lib/registry --name docker-registry registry:2

#### 使用 Cloudflare Workers 自建节点科学上网 https://xiaowangye.org/posts/using-cloudflare-workers-build-proxy-for-internet-access/
#### 自建订阅地址: https://tunnel.lanneng.tech/09268ef9-d1d1-4b58-a542-8b9f4857f65a

#### 基于Docker安装部署ShadowSocks基于Socks5代理方式的加密传输协议件(翻墙)
#### 从Docker Hub里拉取ShadowSocks镜像最新版来部署 Dream2021 注意端口要开放出去
#### Github客户端地址: https://github.com/shadowsocks
docker pull mritd/shadowsocks

#### 启动运行容器 -m 加密方式 -k 密码
sudo docker run -dt --restart=always -p 8888:6433 --name shadowsocks-server mritd/shadowsocks \
-s "-s 0.0.0.0 -p 6433 -m chacha20-ietf-poly1305 -k guigu321"

#### 搭建OPEN VPN服务 参考步骤: https://github.com/Nyr/openvpn-install
./openvpn-install.sh

systemctl restart openvpn-server@server.service

#### 已启动容器动态修改时区 进入容器 docker exec 执行命令  date命令验证
ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

#### Docker容器数据迁移部署说明
rsync -avzP root@192.168.1.100:/source/jenkins/ /target/jenkins

tar -zcvf my.tar.gz /my
- 本地复制到远程 scp -r my.tar.gz root@ip:/
- 远程复制到本地 scp -r root@ip:/my/jenkins.tar.gz ~/

tar -xzvf my.tar.gz && rm -f my.tar.gz

#### 清理Docker占用的磁盘空间
  df -h

  docker 占用的空间命令查看
  docker system df
  docker system df -v    查看更详细信息

- /var/lib/docker/containers/ID目录，如果容器使用了默认的日志模式，他的所有日志都会以JSON形式保存到此目录下
- /var/lib/docker/overlay2 目录下含有容器的读写层，如果容器使用自己的文件系统保存了数据，那么就会写到此目录下

- 当停止容器后，容器占用的空间就会变为可回收的
- 一键删除所有已经停止的容器 删除容器时会删除其关联的读写层占用的空间
  docker container prune
  docker image prune -a # 清理所有未被容器引用的镜像
  docker run --log-opt max-size=10m --log-opt max-file=3 日志大小限制
  
  sudo sh -c "truncate -s 0 /var/lib/docker/containers/*/*-json.log"
  cd /my && rm -rf /*/logs
  
#### Docker内Nginx占用流量和磁盘空间分析
- docker exec -it 容器id bash  命令进入容器
- df -h  查看磁盘占用情况
- cd /etc/nginx 进入访问日志目录
- ls -lh 查看每个文件的占用情况
- tail -f  文件名称  查看文件实时内容

#### Linux磁盘空间分析
- df -h 查看整体占用情况
- du -sh 查看整个当前文件的占用情况
- ls -lh 查看每个文件的占用情况
- sudo du -h --max-depth=1 /  根目录每个文件的大小
- du -lh --max-depth=1 查看当前目录子目录占用情况

#### 处理网络不通问题
systemctl restart network || service network restart && sudo systemctl daemon-reload && sudo systemctl restart docker