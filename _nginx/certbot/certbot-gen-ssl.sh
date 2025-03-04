#!/bin/bash
# Author: 潘维吉
# Description: 创建域名SSL证书和过期自动更新 使用的是Let’s Encrypt签发的证书，配合Certbot客户端
# Let’s Encrypt 是一个自动签发 https 证书的免费项目
# Certbot 是 Let’s Encrypt 官方推荐的证书生成客户端工具  或者 acme.sh客户端 https://github.com/acmesh-official/acme.sh
# K8s集群使用 cert-manager 签发免费SSL证书: https://cloud.tencent.com/document/product/457/49368

mkdir -p /my/letsencrypt
cd /my/letsencrypt

if [[ ! $(command -v certbot) ]]; then
  echo "安装Certbot客户端"
  # Certbot 目前需要在类 UNIX 操作系统上运行 Python 3.6+。默认情况下，它需要 root 访问权限才能写入 /etc/letsencrypt
  sudo apt-get update -y || true
  sudo apt-get upgrade -y || true # 更新系统软件
  sudo apt-get install -y certbot || true  # 安装Certbot客户端
  sudo sudo apt upgrade certbot && sudo apt dist-upgrade # 升级Certbot版本

  # sudo yum update -y
  sudo yum install -y certbot || true  # 如果certbot不存在 设置yum镜像源/etc/yum.repos.d

  # Python包方式安装  非标准的CentOS或Ubuntu系统 比如国产麒麟等
  sudo yum install python3-pip -y || true && sudo apt install python3-pip -y || true &&  python3 -m pip install --upgrade pip
  pip3 install -i https://pypi.tuna.tsinghua.edu.cn/simple virtualenv
  # 创建个虚拟环境操作
  virtualenv venv_python
  source ./venv_python/bin/activate
  pip install -i https://pypi.tuna.tsinghua.edu.cn/simple certbot
  pip3 install -i https://pypi.tuna.tsinghua.edu.cn/simple certbot-dns-aliyun
  pip3 list
  # 在虚拟环境中执行即可
  /root/venv_python/bin/certbot --version || certbot --version

  # 初始化阿里云域名DNS验证插件  固定版本  pip install certbot-dns-aliyun==0.38.1  也使用更简单的WebRoot方式验证
  sudo apt install -y python3-pip || true && sudo yum install python3-pip -y || true && pip install certbot-dns-aliyun
  sudo pip3 install --upgrade certbot certbot-dns-aliyun

  # Docker服务之间安装  https://hub.docker.com/r/certbot/certbot
  #   docker pull certbot/certbot:latest
  #   sudo docker run -it --rm --name certbot \
  #                -v "/etc/letsencrypt:/etc/letsencrypt" \
  #                -v "/var/lib/letsencrypt:/var/lib/letsencrypt" \
  #                certbot/certbot certonly --email 406798106@qq.com  -d "panweiji.com"

  certbot --version

fi

echo "生成域名相关的SSL证书"
# 注意: 生成的过程中会让去阿里云添加域名记录  类型是TXT 命令控制台显示主记录、记录值 可能会等待一会才生效 !!!
# 主要限制是 每个注册域的证书（每周可生成 50 个）  具体查看: https://letsencrypt.org/docs/rate-limits/
# 域名（可以填多个 多个 -d 设置） 一个主域名证书所有二级域名可用
# 默认 standalone 是使用 443 端口，也就是说要停止服务器现在占用 443 端口的进程  我们也可以将其改为使用 80 端口，同样道理，这时需要停止 80 端口的占用 !!!

# 以命令交互方式开始制作证书
# certbot certonly

# 签发泛域名证书 Let's Encrypt 是单域绑定， 虽然支持多域名，但是不支持泛域绑定生成的也不受信任, 需要DNS校验 !!!
# certbot certonly --agree-tos --manual --preferred-challenges dns-01 --email 406798106@qq.com -d panweiji.com -d *.panweiji.com  \
# --server https://acme-v02.api.letsencrypt.org/directory

# 命令方式开始制作证书  -n 非交互式 --agree-tos 同意服务协议  --staging 测试模式限制更松 不是正式证书
# --dry-run 使用 Test "renew" or "certonly" without saving any certificates
#certbot certonly --manual --agree-tos --preferred-challenges dns \
#  --email 406798106@qq.com -d name.panweiji.com

# certonly阿里云自动生成二级域名的DNS验证  renew续签也需要DNS 动态添加 TXT 记录 不需要手动创建  https://github.com/tengattack/certbot-dns-aliyun
# certbot 提供了一个 hook，可以编写一个 Shell 脚本，让脚本调用 DNS 服务商的 API 接口，动态添加 TXT 记录

sudo apt install -y python3-pip && pip install certbot-dns-aliyun

# https://ram.console.aliyun.com/ 申请key和秘钥  并确保您的 RAM 帐户有AliyunDNSFullAccess权限 确保生成证书域名在当前阿里云账号管理
sudo cat <<EOF >/my/credentials.ini
dns_aliyun_access_key  =
dns_aliyun_access_key_secret =
EOF

chmod 600 /my/credentials.ini

# Certbot 生成域名相关的SSL证书
# 1. 使用更简单的WebRoot方式验证  适合那些已经在运行Web服务器 --webroot -w <web_root> 指定您的 Web 服务器的站点根目录 如/usr/share/nginx/html
sudo certbot certonly --webroot -w /path/web/root --non-interactive --agree-tos \
     -d panweiji.com  -d www.panweiji.com \
     --email 406798106@qq.com
# 2. 云DNS自动校验方式生成证书方式  3. standalone模式获取证书 --standalone 不需要指定网站根目录，自动启用服务器的443端口，来验证域名的归属
certbot certonly --authenticator=dns-aliyun --dns-aliyun-credentials='/my/credentials.ini' \
--email 406798106@qq.com  -d "panweiji.com"

echo "查看生成的SSL证书"
# certbot certificates
# 证书公钥fullchain.pem 和 私钥privkey.pem 文件

# nginx中配置  docker容器注意映射目录 -v /etc/letsencrypt:/etc/letsencrypt
# 注意  nginx: [emerg] cannot load certificate  需要在Docker运行的时候-v映射 -v /etc/letsencrypt:/etc/letsencrypt
# ssl_certificate /etc/letsencrypt/live/domain.com/fullchain.pem;
# ssl_certificate_key /etc/letsencrypt/live/domain.com/privkey.pem;

cd /etc/letsencrypt/live/ || true && ls -l

# 取消撤销证书
# certbot revoke --cert-path /etc/letsencrypt/live/example.com/cert.pem
# certbot delete --cert-name example.com

# SSL 状态检测  访问 https://myssl.com

# 卸载Certbot客户端  sudo apt remove -y certbot


# 如果出现生成失败 如archive directory exists for domain.com-0001 执行删除操作
# certbot certificates  查看
# rm -rf /etc/letsencrypt/archives/domain.com-0001
# rm -rf /etc/letsencrypt/live/domain.com-0001
# rm -f /etc/letsencrypt/renewal/domain.com-0001.conf