### Keepalived是一个基于VRRP虚拟路由冗余协议来实现的服务高可用方案, 可利用其来避免IP单点故障 使用虚拟IP瞬时漂移


#####  Keepalived配置详解  注意关闭防火墙 防止keepalived之间组播报文通讯失效 无法IP漂移等！！！

- https://blog.51cto.com/u_13710166/5288506

- systemctl status firewalld.service 或 sudo ufw status  # 查看防火墙状态
- systemctl stop firewalld.service 或 sudo ufw disable   # 重启还会打开防火墙
- systemctl disable --now firewalld.service 或 sudo ufw reset # 永久禁用防火墙
- systemctl disable --now iptables  # 永久禁用防火墙配置表
- iptables -F   # 清空防火墙配置规则 设置 
 
##### 操作命令

systemctl start keepalived

systemctl stop keepalived

systemctl status keepalived

systemctl restart keepalived

systemctl daemon-reload


##### 设置keepalived的虚拟主机，配置文件 /etc/keepalived/keepalived.conf  注意查看ifconfig配置网卡名配置interface参数！！！

##### 查看虚拟IP

ip addr 或 ip a

##### 查看日志

tail -f /var/log/messages

##### 处理网络不通问题

systemctl restart network  &&  systemctl restart docker