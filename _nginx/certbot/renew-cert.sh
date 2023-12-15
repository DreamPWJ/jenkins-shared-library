#!/bin/bash
# Author: 潘维吉
# Description: 自动续签SSL证书

# 续签SSL证书
# Another instance of Certbot is already running
#find / -type f -name ".certbot.lock" -exec rm {} \  || true;

# 可用ansible将文件同步到所有服务器
# 如果提示未到期，cert not due for renewal，可以强制更新 --force-renew  测试90天后续签情况执行添加参数 --dry-run
# 如果距离过期不到30天 默认不会重新生成证书
# 命令 certbot certificates 查看SSL证书的过期时间
sudo certbot renew

# 重新加载nginx配置才会生效
docker exec proxy-nginx nginx -t -c /etc/nginx/nginx.conf
docker exec proxy-nginx nginx -s reload || true


# 创建定时任务 自动续期SSL证书 默认证书有效期是90天
# sudo crontab -e
# 每月执行一次 0 0 1 * *  每天凌晨2点执行一次 0 2 * * *
# 0 2 * * * /bin/bash /my/renew-cert.sh >/my/crontab.log 2>&1
# service crond restart , Ubuntu 使用 sudo service cron start # 重启crond生效
# crontab -l # 查看crond列表
# chmod +x renew-cert.sh  给shell脚本执行文件可执行权限
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出
