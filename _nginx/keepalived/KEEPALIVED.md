### Keepalived是一个基于VRRP虚拟路由冗余协议来实现的服务高可用方案, 可利用其来避免IP单点故障 使用虚拟IP漂移


##### 操作命令

- systemctl start keepalived

- systemctl stop keepalived

- systemctl status keepalived

- systemctl daemon-reload

- systemctl stop firewalld.service

##### 设置keepalived的虚拟主机，配置文件 /etc/keepalived/keepalived.conf

[##### 查看虚拟IP]()

ip addr

##### 查看日志

tail -f /var/log/messages