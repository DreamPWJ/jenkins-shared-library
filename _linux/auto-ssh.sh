#!/bin/bash
# Author: 潘维吉
# Description:  批量执行SSH免密登录    chmod +x auto-ssh.sh  在hosts.txt内批量设置机器的ip 用户名 密码
# !!!注意当前机器先执行 ssh-keygen -t rsa 或者 ssh-keygen -t dsa

# 建立免密连接 流水线已实现自动设置免密登录  如需手动设置步骤如下
# 需要在jenkins docker容器内而非宿主机 ssh-keygen -t rsa   root用户在/root/.ssh/id_rsa.pub
# 公钥放在远程访问服务的/root/.ssh/authorized_keys里  在jenkins容器里执行 ssh root@ip 命令访问确认
# 如果有跳板机情况 可以手动将构建机器的公钥分别添加到跳板机和目标机authorized_keys内实现免密登录

if [[ ! $(command -v expect) ]]; then
  yum install -y expect || true
  apt-get install -y expect || true
  brew install expect || true
fi

#ssh-keygen -f id_rsa -t rsa -N ''

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
