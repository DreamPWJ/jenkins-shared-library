#!/bin/bash
# Author: 潘维吉
# Description:  跳板机方式自动批量执行SSH ProxyJump免密登录 chmod +x auto-proxy-ssh.sh  在proxy_jump_hosts.json内批量设置机器的ip 用户名 密码
# !!!注意当前机器先执行 ssh-keygen -t rsa  确保所有机器OpenSSH使用高版本才支持SSH跳板方式
# 安全性高和定制化的数据建议保存为Jenkins的“Secret file”类型的凭据并获取 无需放在代码中

# 透传跳板机实现自动登录授权 主要思路是： A访问B 需要把A的公钥放在B的授权列表里  然后重启ssh服务即可
# 1. 访问客户端执行机器先免密到跳板机 用户在cat /root/.ssh/id_rsa.pub 公钥放在远程要访问服务的vim /root/.ssh/authorized_keys里
# 执行命令： ssh-copy-id -i $HOME/.ssh/id_rsa.pub -p $jump_port $jump_user_name@$jump_host
# 2. 跳板机再免密到目标机 同理1  执行命令：  ssh $jump_user_name@$jump_host -p $jump_port && ssh-copy-id -i $HOME/.ssh/id_rsa.pub -p $target_port $target_user_name@$target_host
# 3. 最后将客户端的公钥 cat /root/.ssh/id_rsa.pub 放到内网目标机 vim /root/.ssh/authorized_keys 授信
# 执行命令：ssh-copy-id -i $HOME/.ssh/id_rsa.pub -p $target_port -o "ProxyCommand ssh -W %h:%p $jump_user_name@$jump_host -p $jump_port" $target_user_name@$target_host
# 在目标主机上执行 systemctl restart sshd 生效
# 在执行SSH跳板命令生效:  ssh -J root@外网跳板机IP:22 root@内网目标机器IP -p 22

echo "跳板机方式自动批量执行SSH ProxyJump免密登录"

if [[ ! $(command -v jq) ]]; then
   apt-get update -y || true
   apt install -y jq || true
   yum update -y || true
   yum install -y jq || true
   jq --version
fi

if [[ ! $(command -v expect) ]]; then
   yum install -y expect || true
   apt-get install -y expect || true
   brew install expect || true
fi

# 指定要读取的文件名
json_file="proxy_jump_hosts.json"

#cat $json_file

# 如果是从文件读取则用 cat json_file | jq ...
# 对于变量中的JSON数据，直接通过echo传递给jq

while read host; do
    jump_host=$(echo "$host" | jq -r '.jump_host') # 注意ip不要有空格
    jump_user_name=$(echo "$host" | jq -r '.jump_user_name')
    jump_password=$(echo "$host" | jq -r '.jump_password')
    jump_port=$(echo "$host" | jq -r '.jump_port')
    echo "jump_host_ip: $jump_host"

    # 只设置当前要配置的服务器   如果已经免密连接登录跳过设置
    if [[ "$jump_host" != "$1" ]] ; then
          continue  # 跳出本次循环
    fi

    # 清除之前授权信息  防止授权失败
    #ssh -p $jump_port $jump_user_name@$jump_host "rm -f ~/.ssh/authorized_keys"

    # 建立CI/CD构建服务器到跳板机免密连接
    echo "自动建立CI/CD构建服务器到跳板机免密SSH连接"
  expect <<EOF
        spawn ssh-copy-id -i $HOME/.ssh/id_rsa.pub -p $jump_port $jump_user_name@$jump_host
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
        # 只设置当前要配置的服务器   如果已经免密连接登录跳过设置
        if [[ "$target_host" != "$2" ]] ; then
              continue  # 跳出本次循环
        fi
        # 通过跳板机登录目标主机 ssh -J root@外网跳板机IP:22 root@内网目标机器IP -p 22 '命令'

        # 建立跳板机到目标机的免密连接
        echo "自动建立跳板机到目标机的免密SSH连接"
  expect <<EOF
        # 启动spawn命令来启动一个新的进程 建立跳板机到目标机的免密连接 使用 -J 参数通过跳板机连接目标主机
        spawn ssh $jump_user_name@$jump_host -p $jump_port

        # 清除之前授权信息  防止授权失败
        # send "ssh -p $target_port $target_user_name@$target_host 'rm -f ~/.ssh/authorized_keys' \r"

        # 在目标主机上执行 ssh-copy-id 命令
        send "ssh-copy-id -i $HOME/.ssh/id_rsa.pub -p $target_port $target_user_name@$target_host \r"

        # 处理可能的交互
         expect {
                 "yes/no" {send "yes\n";exp_continue}
                 "password" {send "$target_password\n"}
         }

        #send "echo '🤚 如果OpenSSH版本低于7.3不支持SSH命令跳板机方式访问, 请先升级' \r"
        #send "uname -a \r"
        #send "ssh -V 2>&1 | awk '{print $1, $NF}' | grep -qE 'OpenSSH_[0-6]\.|OpenSSH_7\.[0-2]' && (sudo yum update -y openssh openssh-server openssh-clients || echo "Failed to update SSH.") || echo "SSH version is already 7.3 or higher, no need to update." \r"
        send "exit\r"

        # 等待命令执行完成
        expect eof
EOF

       # 建立访问机器通过跳板机到目标机的免密连接
       echo "自动建立CI/CD构建服务器通过跳板机到目标机的免密SSH连接 🤚 如果OpenSSH版本低于7.3不支持SSH命令跳板机方式访问, 请先升级"
       # 从 known_hosts 文件中移除指定主机的密钥记录
       ssh-keygen -R $target_host
   expect <<EOF
         spawn ssh-copy-id -i $HOME/.ssh/id_rsa.pub  -p $target_port -o "ProxyCommand ssh -W %h:%p $jump_user_name@$jump_host -p $jump_port"  $target_user_name@$target_host
         expect {
                 "yes/no" {send "yes\n";exp_continue}
                 "password" {send "$target_password\n"}
         }
        # 处理可能的交互
        expect {
                "yes/no" {send "yes\n"}
         }
         expect eof
EOF

 done <<< "$(echo "$host" | jq -c '.target_hosts[]')"

done  <<< "$(cat "$json_file" | jq -c '.[]')"


# 在目标机器中查看 cat /root/.ssh/authorized_keys

# 透传跳板机实现自动登录授权 主要思路是： A访问B 需要把A的公钥放在B的授权列表里  然后重启ssh服务即可
# 1. 客户端执行机器先免密到跳板机 用户在cat /root/.ssh/id_rsa.pub 公钥放在远程要访问服务的vim /root/.ssh/authorized_keys里
# 2. 跳板机再免密到目标机 同理1
# 3. 最后将客户端的公钥 cat /root/.ssh/id_rsa.pub 放到内网目标机 vim /root/.ssh/authorized_keys 授信  systemctl restart sshd
# 在执行SSH跳板命令生效:  ssh -J root@外网跳板机IP:22 root@内网目标机器IP -p 22