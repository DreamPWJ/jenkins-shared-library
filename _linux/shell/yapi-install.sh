#!/usr/bin/env bash
# Author: 潘维吉
# Description: API文档管理平台 YApi私有化部署  Apifox也很不错

cd /my

echo "创建存储卷 备份MongoDB到宿主机"
docker volume create mongo-data

echo "安装启动MongoDB"
docker pull mongo:latest
docker run -d --restart=always -p 27017:27017 \
  --name mongodb \
  -v mongo-data:/data/db \
  -e MONGO_INITDB_ROOT_USERNAME=root \
  -e MONGO_INITDB_ROOT_PASSWORD=123456 \
  -e MONGO_INITDB_DATABASE=yapi \
  mongo

echo "下载YApi镜像"
docker pull jayfong/yapi:latest

echo "自定义配置文件yapi-config.json"
sudo cat <<EOF >yapi-config.json
{
  "port": "6006",
  "adminAccount": "406798106@qq.com",
  "timeout": 120000,
  "db": {
    "servername": "mongo",
    "DATABASE": "yapi",
    "port": 27017,
    "user": "root",
    "pass": "123456",
    "authSource": "admin"
  },
  "mail": {
    "enable": true,
    "host": "smtp.gmail.com",
    "port": 465,
    "from": "*",
    "auth": {
      "user": "406798106@qq.com",
      "pass": "xxx"
    }
  }
}
EOF

echo "初始化YApi数据库连接及管理员账号"
docker run -d --rm \
  --name yapi-init \
  --link mongodb:mongo \
  -v $PWD/yapi-config.json:/yapi/config.json \
  jayfong/yapi \
  server/install.js

sleep 1s

echo "启动YApi服务"
docker run -d --restart=always \
  --name yapi \
  --link mongodb:mongo \
  -p 6006:3000 \
  -v $PWD/yapi-config.json:/yapi/config.json \
  jayfong/yapi

sleep 3s

echo "在服务器上验证YApi启动是否成功"
curl 127.0.0.1:6006

# 管理员登录账号
# 访问地址  http://ip:6006
# 登录账号: 406798106@qq.com
# 密码: ymfe.org
