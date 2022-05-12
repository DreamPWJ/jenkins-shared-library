# Author: 潘维吉
# Version 1.0.0
# Description: 自定义构建Nginx Web项目镜像

# Nginx基础镜像
FROM nginx:stable

# 设置镜像元数据，利用 docker inspect [镜像名称|镜像ID],即可查看
#LABEL author="潘维吉"

# 挂载数据卷 docker删除后数据不丢失 docker run -v 映射
#VOLUME /logs

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

# 拷贝自定义配置文件和SSL安全证书到指定目录
COPY  default.conf /etc/nginx/conf.d/
#COPY  ssl/* /etc/nginx/ssl/

# 删除之前的部署文件
#RUN rm -rf /usr/share/nginx/html/*

# 拷贝部署文件到nginx
COPY  ${NPM_PACKAGE_FOLDER}.tar.gz /usr/share/nginx/html/
# 解压文件到当前文件夹下并删除压缩文件
RUN cd /usr/share/nginx/html/ \
 && tar -xzvf ${NPM_PACKAGE_FOLDER}.tar.gz --strip-components ${WEB_STRIP_COMPONENTS} >/dev/null 2>&1 \
 && rm -f ${NPM_PACKAGE_FOLDER}.tar.gz

# 暴露端口
EXPOSE  80 443

# 启动nginx
CMD ["nginx", "-g", "daemon off;"]
