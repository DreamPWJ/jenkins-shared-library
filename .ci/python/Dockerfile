# syntax=docker/dockerfile:1
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
FROM python:$PYTHON_VERSION-slim

# Docker 将此路径用作所有后续命令的默认位置
WORKDIR /app

# 指定切换用户
USER root

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 下载项目依赖包
#COPY requirements.txt requirements.txt

# 复制代码到容器 ADD 会自动解压，COPY 不会解压  注意tar.gz不能使用中划线命名
COPY python.tar.gz .

# 解压文件到当前文件夹下并删除压缩文件  下载项目依赖包
RUN  tar -xzvf python.tar.gz >/dev/null 2>&1 \
 && rm -f *.tar.gz \
 && mv requirement.txt requirements.txt || true \
 && pip3 install --no-cache-dir --upgrade -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple || true

# 暴露端口
EXPOSE $EXPOSE_PORT

# Flask部署 启动命令
#CMD [ "python", "-m" , "flask", "run", "--host=0.0.0.0"]
# FastAPI部署 启动命令
#CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "${EXPOSE_PORT}"]
# 根据主文件默认名称 app.py
CMD [ "python", "$PYTHON_START_FILE"]