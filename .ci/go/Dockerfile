# Author: 潘维吉
# Version 1.0.0
# Description: 自定义构建Go语言镜像
# 参考文档: https://docs.docker.com/language/golang/build-images/

# 构建镜像docker build --build-arg PROJECT_NAME=app 或 docker-compose.yaml args参数传入
ARG PROJECT_NAME
ARG EXPOSE_PORT

# 依赖基础镜像
FROM golang:1.17-alpine

# 指定切换用户
USER root

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 复制构建包到容器
COPY *.go /main.go

EXPOSE $EXPOSE_PORT

# 运行
CMD [ "/main.go" ]
