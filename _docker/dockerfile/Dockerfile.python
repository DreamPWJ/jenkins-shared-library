# Author: 潘维吉
# Version 1.0.0
# Description: 自定义构建Python语言镜像
# 参考文档: https://docs.docker.com/language/python/build-images/

# 构建镜像docker build --build-arg PROJECT_NAME=app 或 docker-compose.yaml args参数传入
ARG PROJECT_NAME
ARG EXPOSE_PORT

# 依赖基础镜像
FROM python:3.9

# 复制构建包到容器
COPY app /app

EXPOSE $EXPOSE_PORT

# 启动运行 代码设置示例 app.run(host='0.0.0.0',port='5000')
CMD [ "/app"]
