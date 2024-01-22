#!/bin/bash
# Author: 潘维吉
# Description:  跳板机方式批量执行SSH免密登录    chmod +x auto-proxy-ssh.sh  在hosts.txt内批量设置机器的ip 用户名 密码
# !!!注意当前机器先执行 ssh-keygen -t rsa
# 安全性高和定制化的数据建议保存为Jenkins的“Secret file”类型的凭据并获取 无需放在代码中

# 透传跳板机实现自动登录授权 主要思路是： A访问B 需要把A的公钥放在B的授权列表里  然后重启ssh服务即可
# 1. 客户端执行机器先免密到跳板机 用户在cat /root/.ssh/id_rsa.pub 公钥放在远程要访问服务的vim /root/.ssh/authorized_keys里
# 2. 跳板机再免密到目标机 同理1
# 3. 最后将客户端的公钥 cat /root/.ssh/id_rsa.pub 放到内网目标机 vim /root/.ssh/authorized_keys 授信  systemctl restart sshd
# 在执行SSH跳板命令生效:  ssh -J root@外网跳板机IP:22 root@内网目标机器IP -p 22

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
    echo "jump_host: $jump_host"

  while read item_host; do
       # echo "item_host: $item_host"
        target_host=$(echo "$item_host" | jq -r '.target_host')
        target_user_name=$(echo "$item_host" | jq -r '.target_user_name')
        echo "target_host: $target_host ,  target_user_name: $target_user_name"
    done <<< "$(echo "$host" | jq -c '.target_hosts[]')"

done  <<< "$(cat "$json_file" | jq -c '.[]')"

# 跳板机地址
#set jump_host "jump_host_address"
#
## 用户名和密码
#set username "your_username"
#set password "your_password"
#
## 目标主机列表
#set hosts_list [list "host1" "host2" "host3"]
#
#foreach host $hosts_list {
#    # 通过跳板机登录目标主机
#    spawn ssh -o ProxyCommand="ssh -W %h:%p $username@$jump_host" $username@$host
#
#    # 期望交互式输入并发送密码
#    expect "*assword:*"
#    send "$password\r"
#
#    # 发送一个简单的命令以确认连接成功（例如：ls）
#    expect "#"
#    send "ls\r"
#    # 钥添加到了目标主机的~/.ssh/authorized_keys文件中以实现免密登
#    send "ssh-copy-id -i $HOME/.ssh/id_rsa.pub  $username@$host\r"
#    # 等待命令执行完成
#    expect "#"
#
#    # 打印分隔符，便于区分不同主机的结果
#    puts "\n--- Connection to $host ---\n"
#
#    # 关闭当前连接
#    send "exit\r"
#    expect eof
#}
#
#puts "\nAll connections completed."
