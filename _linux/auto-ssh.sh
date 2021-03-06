#!/bin/bash
# Author: 潘维吉
# Description:  批量执行SSH免密登录    chmod +x auto-ssh.sh  在hosts.txt内批量设置机器的ip 用户名 密码
# !!!注意当前机器先执行 ssh-keygen -t rsa

# 建立免密连接 流水线已实现自动设置免密登录  如需手动设置步骤如下
# 需要在jenkins docker容器内而非宿主机 ssh-keygen -t rsa   root用户在/root/.ssh/id_rsa.pub
# 公钥放在远程访问服务的/root/.ssh/authorized_keys里  在jenkins容器里执行 ssh root@ip 命令访问确认
# 如果有跳板机情况 可手动将构建机器的公钥分别添加外网跳板机和内网目标机authorized_keys内实现免密登录 touch authorized_keys
# 自动命令 scp -p ~/.ssh/id_rsa.pub root@<remote_ip>:/root/.ssh/authorized_keys && ssh root@<remote_ip> -p 22

if [[ ! $(command -v expect) ]]; then
  yum install -y expect || true
  apt-get install -y expect || true
  brew install expect || true
fi

while read host; do
  ip=$(echo $host | cut -d " " -f1)
  username=$(echo $host | cut -d " " -f2)
  password=$(echo $host | cut -d " " -f3)
  expect <<EOF
        spawn ssh-copy-id -i $HOME/.ssh/id_rsa.pub  $username@$ip
        expect {
                "yes/no" {send "yes\n";exp_continue}
                "password" {send "$password\n"}
        }
        expect eof
EOF
done <hosts.txt

# 透传跳板机实现自动登录
# 主要思路是 1. 客户端机先免密到跳板机 2. 跳板机再免密到目标机 3. 客户端最后将公钥放到内网目标机