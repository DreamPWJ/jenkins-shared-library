# syntax=docker/dockerfile:1.6
# Author: 潘维吉
# Version 1.0.0
# Description: 自定义构建Python语言镜像
# 参考文档: https://docs.docker.com/language/python/build-images/

# 构建镜像docker build --build-arg PROJECT_NAME=app 或 docker-compose.yaml args参数传入
ARG PYTHON_VERSION
ARG PROJECT_NAME
ARG EXPOSE_PORT
ARG PYTHON_START_FILE

# 依赖基础镜像
FROM python:${PYTHON_VERSION}-slim

# Docker 将此路径用作所有后续命令的默认位置
WORKDIR /app

# 挂载数据卷 docker删除后数据不丢失 docker run -v 映射
#VOLUME /app

# 指定切换用户
USER root

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 设置环境变量
ENV DEBIAN_FRONTEND=noninteractive \
    PIP_CACHE_DIR=/root/.cache/pip  \
    PYTHONUNBUFFERED=1

# 动态安装依赖环境包 安装特定项目依赖环境  --no-install-recommends不安装那些被推荐但非必需的依赖包
ARG CUSTOM_INSTALL_PACKAGES
RUN if [ -n "$CUSTOM_INSTALL_PACKAGES" ]; then \
    sed -i 's/deb.debian.org/mirrors.ustc.edu.cn/g'  /etc/apt/sources.list && apt-get clean || true && apt-get update || true \
    && apt-get install -y --no-install-recommends $CUSTOM_INSTALL_PACKAGES ; \
    fi

# 复制代码到容器 ADD 会自动解压，COPY 不会解压
ADD python.tar.gz .

# Docker 19.03后版本可启用 BuildKit 方式指定缓存目录 --mount=type=cache,target=$PIP_CACHE_DIR
ARG IS_NEED_INSTALL="true"
RUN --mount=type=cache,target=$PIP_CACHE_DIR pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/ --trusted-host mirrors.aliyun.com


# 暴露端口
EXPOSE $EXPOSE_PORT

# 启动项目时动态设置python主文件  CMD中的"$MY_VAR"变量设置成 docker run -e MY_VAR=value 运行变量 否则无法获取
CMD ["sh", "-c", "python $PYTHON_START_FILE"]
