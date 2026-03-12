#!/usr/bin/env bash
# Author: 潘维吉
# Description: 创建域名SSL证书和过期自动更新  与certbot比优势零依赖系统内安装简单 多CA支持 自动智能续期
# 通过与Let's Encrypt、ZeroSSL等权威CA机构对接，实现了证书申请-部署-续期全流程自动化

# 安装acme.sh
curl https://get.acme.sh | sh -s email=406798106@qq.com
source ~/.bashrc
# 默认CA  acme.sh 脚本默认 CA 服务器是 ZeroSSL
acme.sh --set-default-ca --server letsencrypt

# 申请证书 推荐WebRoot HTTP-01方式 无需DNS验证 -d：需申请证书的域名(可多个)  --webroot：网站根目录路径（需确保目录可写入）
#acme.sh --issue -d example.com -d www.example.com --webroot /var/www/html
# 宿主机部署Nginx模式 不支持Docker 智能的从 Nginx 的配置中自动完成验证，不需要指定网站根目
#acme.sh --issue --nginx -d example.com -d www.example.com

# DNS模式验证（无需开放端口）
#export Ali_Key="你的域名阿里云Key"
#export Ali_Secret="你的域名阿里云Secret"
cat >> ~/.acme.sh/account.conf << 'EOF'
SAVED_Ali_Key="你的域名阿里云Key"
SAVED_Ali_Secret="你的域名阿里云Secret"
EOF
chmod 600 ~/.acme.sh/account.conf

# 申请证书 DNS模式
acme.sh --issue --dns dns_ali -d example.com

# Standalone模式： 临时启动HTTP服务完成验证（适合无Web服务场景和代理服务情况）
# 如果服务器上没有运行任何 Web 服务，80 端口必须是一直空闲的，那么 acme.sh 还能假装自己是一个 WebServer，临时监听 80 端口，完成验证
#acme.sh --issue --standalone -d example.com -d www.example.com -d example.com

# 续期成功会自动执行nginx证书生成 install-cert 负责“复制 + reload, 重新加载nginx配置才会生效 续期成功才自动reload
mkdir /etc/letsencrypt/live/example.com
acme.sh --install-cert -d example.com \
  --key-file       /etc/letsencrypt/live/example.com/privkey.pem \
  --fullchain-file /etc/letsencrypt/live/example.com/fullchain.pem \
  --reloadcmd     "docker exec proxy-nginx nginx -s reload"

# 重新生成证书
# Standalone模式添加钩子避免80端口占用 --pre-hook "systemctl stop nginx" --post-hook "systemctl start nginx"
acme.sh --renew -d example.com


# 查看定时任务
crontab -l
# 查看所有证书
acme.sh --list

# 手动触发续期测试
#acme.sh --renew -d example.com --force