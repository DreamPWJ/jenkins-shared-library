#!/bin/bash
# Author: 潘维吉
# Description:  批量执行SSH免密登录    chmod +x auto-ssh.sh  在hosts.txt内批量设置机器的ip 用户名 密码
# !!!注意当前机器先执行 ssh-keygen -t rsa 或者 ssh-keygen -t dsa

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
