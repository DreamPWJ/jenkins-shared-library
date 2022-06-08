# Author: 潘维吉
# Version 1.0.0
# Description: 自定义构建C++语言镜像
# 参考文章: https://markus-x-buchholz.medium.com/docker-container-networking-c-client-server-app-e9750f003f8

# 构建镜像docker build --build-arg PROJECT_NAME=app 或 docker-compose.yaml args参数传入
ARG PROJECT_NAME
ARG EXPOSE_PORT

# 依赖基础镜像
FROM ubuntu:bionic
FROM gcc:latest

# 指定切换用户
USER root

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 复制构建包到容器
COPY app /app
#COPY *.cpp /server.cpp

# 源码g++编译成一个可执行文件
#RUN g++ server.cpp -o app

EXPOSE $EXPOSE_PORT

# 运行
CMD [ "./app" ]
