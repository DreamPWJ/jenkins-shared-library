# Author: 潘维吉
# Version 1.0.0
# Description: 自定义构建Nginx Web项目镜像

# Nginx基础镜像
# 推荐标签 alpine 纯静态资源、轻量级生产部署最佳 无需动态模块或复杂配置
#FROM docker.1ms.run/library/nginx:alpine
FROM nginx:stable

# 设置镜像元数据，利用 docker inspect [镜像名称|镜像ID],即可查看
#LABEL author="潘维吉"

# 指定切换用户
USER root

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 构建镜像docker build --build-arg 或 docker-compose.yaml args参数传入
ARG DEPLOY_FOLDER
ARG NPM_PACKAGE_FOLDER
ARG PROJECT_NAME
ARG WEB_STRIP_COMPONENTS

# 拷贝自定义配置文件
COPY  default.conf /etc/nginx/conf.d/
COPY  nginx.conf   /etc/nginx/

# 删除之前的部署文件
#RUN rm -rf /usr/share/nginx/html/*

# 复制代码到容器 ADD 会自动解压，COPY 不会解压
COPY  ${NPM_PACKAGE_FOLDER}.tar.gz /usr/share/nginx/html/

# 解压文件到当前文件夹下并删除压缩文件
RUN cd /usr/share/nginx/html/ \
 && tar -xzvf ${NPM_PACKAGE_FOLDER}.tar.gz --strip-components ${WEB_STRIP_COMPONENTS} >/dev/null 2>&1 \
 && rm -f ${NPM_PACKAGE_FOLDER}.tar.gz

# 暴露端口
EXPOSE  80 443

# 健康检查 HTTP响应200为健康 如果服务不可用或未响应，Docker可以标记容器为不健康，并根据配置采取行动，如自动重启容器
HEALTHCHECK --start-period=1s --interval=30s --timeout=30s --retries=3 \
    CMD curl --fail http://localhost:80 || exit 1

# 启动nginx
CMD ["nginx", "-g", "daemon off;"]
