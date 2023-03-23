#!/bin/bash
# Author: 潘维吉
# Description: 自动续签SSL证书

if [[ ! $(command -v certbot) ]]; then
  echo "安装Certbot客户端"
  # Certbot 目前需要在类 UNIX 操作系统上运行 Python 3.6+。默认情况下，它需要 root 访问权限才能写入 /etc/letsencrypt
  sudo apt-get install -y certbot || true
  sudo yum install -y certbot || true
  # sudo pip install certbot-dns-aliyun
  certbot --version
fi

# 续签SSL证书
# Another instance of Certbot is already running
find / -type f -name ".certbot.lock" -exec rm {} \;

# 可用ansible将文件同步到所有服务器
# 如果提示未到期，cert not due for renewal，可以强制更新 --force-renew  测试90天后续签情况执行添加参数 --dry-run
# 如果距离过期不到30天 默认不会重新生成证书
# 出现0001等新目录情况 指定DNS源
certbot renew

# 重新加载nginx配置才会生效
docker exec proxy-nginx nginx -t -c /etc/nginx/nginx.conf
docker exec proxy-nginx nginx -s reload || true

# SSL 状态检测  访问 https://myssl.com

# 创建定时任务 自动续期SSL证书 默认证书有效期是90天
# sudo crontab -e
# 每月执行一次 0 0 1 * *  每天凌晨2点执行一次 0 2 * * *
# 0 2 * * * /bin/bash /my/renew-cert.sh >/my/crontab.log 2>&1
# service crond restart , Ubuntu 使用 sudo service cron start # 重启crond生效
# crontab -l # 查看crond列表
# chmod +x renew-cert.sh  给shell脚本执行文件可执行权限
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出
