# Author: 潘维吉
# Version 1.0.0
# Description: 自定义构建JDK Spring Boot项目镜像

# 构建镜像docker build --build-arg PROJECT_NAME=app 或 docker-compose.yaml args参数传入
ARG JDK_PUBLISHER
ARG JDK_VERSION
ARG DEPLOY_FOLDER
ARG PROJECT_NAME
ARG EXPOSE_PORT

# Dockerfile多阶段构建 参考文档: https://docs.docker.com/develop/develop-images/multistage-build/
# 示例 FROM golang:1.16 下行注释的KEY值动态替换
#FROM-MULTISTAGE-BUILD-IMAGES

# Spring官方推荐的JDK提供商: https://bell-sw.com/libericajdk/
# JDK基础镜像 默认值变量方式 ${JDK_VERSION:-21}  推荐极致轻量化 alpine 版本
FROM ${JDK_PUBLISHER}:${JDK_VERSION}

# 多阶段构建镜像 从上一个镜像内复制环境到本镜像
# 示例 COPY --from=0 / /   下行注释的KEY值动态替换
#COPY-MULTISTAGE-BUILD-IMAGES

# 设置镜像元数据，利用 docker inspect [镜像名称|镜像ID],即可查看
#LABEL author="潘维吉"

# 挂载数据卷 docker删除后数据不丢失 docker run -v 映射
#VOLUME /logs

# 指定切换用户
USER root

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 动态下载安装相关依赖 下行注释的KEY值动态替换
#DOCKER-FILE-RUN-COMMAND

# 将部署文件复制到docker内根目录
COPY  *.jar  /server.jar

# 对外暴露端口 k8s在yaml设置端口
EXPOSE  $EXPOSE_PORT

# 健康检查 HTTP响应200为健康 如果服务不可用或未响应，Docker可以标记容器为不健康，并根据配置采取行动，如自动重启容器 和--restart=on-failure联动
HEALTHCHECK --start-period=10s --interval=30s --timeout=30s --retries=3 \
    CMD curl -v --fail  http://localhost:$DOCKER_SERVICE_PORT || exit 1

# 运行jar包命令 远程调式  启动命令: java -jar app.jar  在k8s动态设置启动参数
ENTRYPOINT "java" "-jar" $JAVA_OPTS $REMOTE_DEBUGGING_PARAM  "-Djava.security.egd=file:/dev/./urandom" "/server.jar"
