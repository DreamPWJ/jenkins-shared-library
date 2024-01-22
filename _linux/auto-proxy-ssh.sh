#!/bin/bash
# Author: 潘维吉
# Description:  跳板机方式自动批量执行SSH ProxyCommand免密登录 chmod +x auto-proxy-ssh.sh  在proxy_jump_hosts.json内批量设置机器的ip 用户名 密码
# !!!注意当前机器先执行 ssh-keygen -t rsa
# 安全性高和定制化的数据建议保存为Jenkins的“Secret file”类型的凭据并获取 无需放在代码中

# 透传跳板机实现自动登录授权 主要思路是： A访问B 需要把A的公钥放在B的授权列表里  然后重启ssh服务即可
# 1. 客户端执行机器先免密到跳板机 用户在cat /root/.ssh/id_rsa.pub 公钥放在远程要访问服务的vim /root/.ssh/authorized_keys里
# 2. 跳板机再免密到目标机 同理1
# 3. 最后将客户端的公钥 cat /root/.ssh/id_rsa.pub 放到内网目标机 vim /root/.ssh/authorized_keys 授信  systemctl restart sshd
# 在执行SSH跳板命令生效:  ssh -J root@外网跳板机IP:22 root@内网目标机器IP -p 22

echo "跳板机方式自动批量执行SSH ProxyCommand免密登录"

if [[ ! $(command -v jq) ]]; then
sudo apt install -y jq || true
sudo yum install -y jq || true
jq --version
fi

# 指定要读取的文件名
json_file="proxy_jump_hosts.json"

#cat $json_file

# 如果是从文件读取则用 cat json_file | jq ...
# 对于变量中的JSON数据，直接通过echo传递给jq

while read host; do
    jump_host=$(echo "$host" | jq -r '.jump_host')
    jump_user_name=$(echo "$host" | jq -r '.jump_user_name')
    jump_password=$(echo "$host" | jq -r '.jump_password')
    jump_port=$(echo "$host" | jq -r '.jump_port')
    echo "jump_host: $jump_host"
  expect <<EOF
        spawn ssh-copy-id -i $HOME/.ssh/id_rsa.pub  $jump_user_name@$jump_host
        expect {
                "yes/no" {send "yes\n";exp_continue}
                "password" {send "$jump_password\n"}
        }
        expect eof
EOF

  # 跳板机登录目标主机
  while read item_host; do
       # echo "item_host: $item_host"
        target_host=$(echo "$item_host" | jq -r '.target_host')
        target_user_name=$(echo "$item_host" | jq -r '.target_user_name')
        target_password=$(echo "$item_host" | jq -r '.target_password')
        target_port=$(echo "$item_host" | jq -r '.target_port')
        echo "target_host: $target_host ,  target_user_name: $target_user_name"

        # 通过跳板机登录目标主机 ssh -J root@外网跳板机IP:22 root@内网目标机器IP -p 22 '命令'
        # spawn ssh-copy-id -i $HOME/.ssh/id_rsa.pub  $target_user_name@$target_host
  expect <<EOF
        spawn ssh -J $jump_user_name@$jump_host:$jump_port $target_user_name@$target_host -p $target_port 'ssh-copy-id -i $HOME/.ssh/id_rsa.pub'
        expect {
                "yes/no" {send "yes\n";exp_continue}
                "password" {send "$target_password\n"}
        }
        expect eof
EOF
  done <<< "$(echo "$host" | jq -c '.target_hosts[]')"

done  <<< "$(cat "$json_file" | jq -c '.[]')"

