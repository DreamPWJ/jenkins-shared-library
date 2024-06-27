#!/bin/bash
# Author: 潘维吉
# Description: 自动续签SSL证书

# 定义要ping的网络地址 因网络不通等原因提供等待网络方式
TARGET="acme-v02.api.letsencrypt.org"
# 设置超时时间（秒），可根据需要调整
TIMEOUT=3600
# 记录开始时间
START_TIME=$(date +%s)

# 循环直到ping成功或超过超时时间
while true; do
    # 尝试ping目标地址，只ping一次并丢弃输出(-c 1 >/dev/null)，通过$?检查上一条命令的退出状态
    ping -c 1 "$TARGET" >/dev/null 2>&1

    # 如果ping成功（退出状态码0），则跳出循环
    if [ $? -eq 0 ]; then
        echo "网络连接成功，继续执行下一步。"
        break
    fi

    # 计算已过去的时间
    ELAPSED_TIME=$(( $(date +%s) - START_TIME ))

    # 如果已经超过设定的超时时间，则终止循环
    if [ $ELAPSED_TIME -ge $TIMEOUT ]; then
        echo "等待超时，网络未就绪。"
        exit 1
    fi

    # 未成功，等待一段时间后重试，等待多少秒
    echo "网络未就绪，等待中..."
    sleep 60
done

# 续签SSL证书
# Another instance of Certbot is already running
#find / -type f -name ".certbot.lock" -exec rm {} \  || true;

# 可用ansible将文件同步到所有服务器
# 如果提示未到期，cert not due for renewal，可以强制更新 --force-renew  测试90天后续签情况执行添加参数 --dry-run
# 如果距离过期不到30天 默认不会重新生成证书 命令 certbot certificates 查看SSL证书的过期时间
# 如果certbot名称不存在 which certbot 查看 执行命令添加路径 如 /root/miniconda3/bin/certbot renew
sudo certbot renew

# 重新加载nginx配置才会生效
docker exec proxy-nginx nginx -t -c /etc/nginx/nginx.conf
docker exec proxy-nginx nginx -s reload || true


# 创建定时任务 自动续期SSL证书 默认证书有效期是90天
# chmod +x /my/renew-cert.sh  给shell脚本执行文件可执行权限  先手动执行测试一下
# sudo crontab -e
# 每月执行一次 0 0 1 * *  每天凌晨2点执行一次 0 2 * * *
# 0 2 * * * /bin/bash /my/renew-cert.sh >/my/certbot-crontab.log 2>&1
# service crond restart , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出
