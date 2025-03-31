# Author: 潘维吉
# Version 1.0.0
# Description: 自定义构建Python语言镜像
# 参考文档: https://docs.docker.com/language/python/build-images/

# 构建镜像docker build --build-arg PROJECT_NAME=app 或 docker-compose.yaml args参数传入
ARG PYTHON_VERSION
ARG PROJECT_NAME
ARG EXPOSE_PORT
ARG PYTHON_START_FILE

# Dockerfile多阶段构建 参考文档: https://docs.docker.com/develop/develop-images/multistage-build/
# 示例 FROM golang:1.16 下行注释的KEY值动态替换
#FROM-MULTISTAGE-BUILD-IMAGES

# 依赖基础镜像 多阶段构建复用docker缓存层加速构建
FROM python:$PYTHON_VERSION

# 多阶段构建镜像 从上一个镜像内复制环境到本镜像
# 示例 COPY --from=0 / /   下行注释的KEY值动态替换
#COPY-MULTISTAGE-BUILD-IMAGES

# Docker 将此路径用作所有后续命令的默认位置
WORKDIR /app

# 指定切换用户
USER root

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 更新默认的apk仓库，并添加额外的、速度较快的国内源（这里以阿里云为例）
#RUN echo "https://mirrors.aliyun.com/alpine/v3.19/main" > /etc/apk/repositories && \
#    echo "https://mirrors.aliyun.com/alpine/v3.19/community" >> /etc/apk/repositories \

# 安装依赖环境  --no-install-recommends不安装那些被推荐但非必需的依赖包
RUN sed -i 's/deb.debian.org/mirrors.ustc.edu.cn/g'  /etc/apt/sources.list && apt-get clean || true && apt-get update || true \
    && apt install -y --no-install-recommends libgl1-mesa-glx || true

# 下载项目依赖包
#COPY requirements.txt requirements.txt

# 复制代码到容器 ADD 会自动解压，COPY 不会解压  注意tar.gz不能使用中划线命名
#COPY . .
COPY python.tar.gz .

# 解压文件到当前文件夹下并删除压缩文件  下载项目依赖包  利用 --cache-dir 指定缓存目录
RUN tar -pxzvf python.tar.gz >/dev/null 2>&1 \
 && mv requirement.txt requirements.txt || true \
 && rm -f *.tar.gz \
 && --mount=type=cache,target=/root/.cache/pip  pip install --upgrade -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple  || true \
 && pwd && ls -l


# 暴露端口
EXPOSE $EXPOSE_PORT

# 根据主文件默认名称 app.py  CMD中的"$MY_VAR"变量设置成 docker run -e MY_VAR=value 运行变量 否则无法获取
CMD python /app/$PYTHON_START_FILE

# Flask部署 启动命令
#CMD ["python", "-m" , "flask", "run", "--host=0.0.0.0"]
# FastAPI部署 启动命令
#CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "${EXPOSE_PORT}"]