# Author: 潘维吉
# Version 1.0.0
# Description: 自定义构建JDK Spring Boot项目镜像

# 构建镜像docker build --build-arg PROJECT_NAME=app 或 docker-compose.yaml args参数传入
ARG JDK_VERSION
ARG DEPLOY_FOLDER
ARG PROJECT_NAME
ARG EXPOSE_PORT

# Dockerfile多阶段构建 参考文档: https://docs.docker.com/develop/develop-images/multistage-build/
# 示例 FROM golang:1.16 下行注释的KEY值动态替换
#FROM-MULTISTAGE-BUILD-IMAGES

# JDK基础镜像 默认值变量方式 ${JDK_VERSION:-11}
FROM openjdk:${JDK_VERSION}-jre

# 多阶段构建镜像 从上一个镜像内复制环境到本镜像
# 示例 COPY --from=0 / /   下行注释的KEY值动态替换
#COPY-MULTISTAGE-BUILD-IMAGES

# 设置镜像元数据，利用 docker inspect [镜像名称|镜像ID],即可查看
#LABEL author="潘维吉"

# 挂载数据卷 docker删除后数据不丢失 docker run -v 映射
VOLUME /logs

# 指定切换用户
USER root

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 动态下载安装相关依赖 下行注释的KEY值动态替换
#DOCKER-FILE-RUN-COMMAND

# RUN apt-get upgrade && apt-get add bash \
# && apt-get install -y curl  &&  curl -SL http://example.com/big.tar.xz

# 配置字体 如果file-which-may-exist目标文件存在，才会被复制  未来新方案可考虑字体目录映射挂载方式更易维护和代码更通用
# COPY msyh.ttc file-which-may-exist? /usr/share/fonts/

# 将部署文件复制到docker内根目录
COPY  *.jar  /server.jar

# 对外暴露端口
EXPOSE  $EXPOSE_PORT

# 运行jar包命令 远程调式  缩短 Tomcat 的启动时间  java -jar app.jar
# JDK8  -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
# JDK11 -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
ENTRYPOINT "java" "-jar" $JAVA_OPTS $REMOTE_DEBUGGING_PARAM  "-Djava.security.egd=file:/dev/./urandom" "/server.jar"
