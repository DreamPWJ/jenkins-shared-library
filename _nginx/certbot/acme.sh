#!/bin/bash
# Author: 潘维吉
# Description: 创建域名SSL证书和过期自动更新  与certbot比优势零依赖系统内安装简单 多CA支持 自动智能续期
# 通过与Let's Encrypt、ZeroSSL等权威CA机构对接，实现了证书申请-部署-续期全流程自动化

# 安装acme.sh
curl https://get.acme.sh | sh -s email=406798106@qq.com
source ~/.bashrc
# 默认CA  acme.sh 脚本默认 CA 服务器是 ZeroSSL
acme.sh --set-default-ca --server letsencrypt

# 推荐WebRoot方式 无需DNS验证 -d：需申请证书的域名(可多个)  --webroot：网站根目录路径（需确保目录可写入）
acme.sh --issue -d example.com -d www.example.com --webroot /var/www/html
# 使用 Nginx 模式 智能的从 Nginx 的配置中自动完成验证，不需要指定网站根目
acme.sh --issue --nginx -d example.com -d www.example.com

# DNS模式
#export Ali_Key="Ali_Key"
#export Ali_Secret="Ali_Secret"
#acme.sh --issue --dns dns_ali -d *.example.com

# Standalone模式： 临时启动HTTP服务完成验证（适合无Web服务场景）
# 如果服务器上没有运行任何 Web 服务，80 端口是空闲的，那么 acme.sh 还能假装自己是一个 WebServer，临时监听 80 端口，完成验证
#acme.sh --issue --standalone -d example.com -d www.example.com -d cp.example.com

# 重新生成证书
acme.sh --renew -d mydomain.com

# 重新加载nginx配置才会生效
docker exec proxy-nginx nginx -t -c /etc/nginx/nginx.conf || true
docker exec proxy-nginx nginx -s reload || true
systemctl reload nginx || true

# 查看定时任务
crontab -l
# 查看所有证书
acme.sh --list

# 手动触发续期测试
#acme.sh --renew -d example.com --force