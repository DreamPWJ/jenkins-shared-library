#!/bin/bash
# Author: 潘维吉
# Description:  批量执行SSH免密登录    chmod +x auto-ssh.sh  在hosts.txt内批量设置机器的ip 用户名 密码
# !!!注意当前机器先执行 ssh-keygen -t rsa
# 安全性高和定制化的数据建议保存为Jenkins的“Secret file”类型的凭据并获取 无需放在代码中

# 建立免密连接 流水线已实现自动设置免密登录  A访问B 需要把A的公钥放在B的授权列表里  然后重启ssh服务即可 如需手动设置步骤如下
# 需要在Jenkins Docker ``容器内而非宿主机`` ssh-keygen -t rsa   root用户在/root/.ssh/id_rsa.pub
# 公钥放在远程访问服务的/root/.ssh/authorized_keys里  在jenkins容器里执行 ssh root@ip 命令访问确认
# 如果有跳板机情况 可手动将构建机器的公钥分别添加外网跳板机和内网目标机authorized_keys内实现免密登录 touch authorized_keys
# 自动命令 scp -p ~/.ssh/id_rsa.pub root@<remote_ip>:/root/.ssh/authorized_keys && ssh root@<remote_ip> -p 22
# 非root用户 执行 chmod 700 /home/非root用户名/.ssh && chmod 600 /home/非root用户名/.ssh/authorized_keys
# 免密后仍然需要密码  编辑 /etc/ssh/sshd_config 把#StrictModes yes设置为StrictModes no

if [[ ! $(command -v expect) ]]; then
  yum install -y expect || true
  apt-get install -y expect || true
  brew install expect || true
fi

while read host; do
  ip=$(echo $host | cut -d " " -f1)
  port=$(echo $host | cut -d " " -f2)
  username=$(echo $host | cut -d " " -f3)
  password=$(echo $host | cut -d " " -f4)

  # 只设置当前要配置的服务器   如果已经免密连接登录跳过设置
  if [[ ${ip} -ne  $1 ]] ; then
        continue  # 跳出本次循环
  fi

  # 清除之前授权信息  防止授权失败
  # ssh -p $port $username@$ip "rm -f ~/.ssh/authorized_keys"


  expect <<EOF
        spawn ssh-copy-id -i $HOME/.ssh/id_rsa.pub -p $port $username@$ip
        expect {
                "yes/no" {send "yes\n";exp_continue}
                "password" {send "$password\n"}
        }
        expect eof
EOF
done <hosts.txt
