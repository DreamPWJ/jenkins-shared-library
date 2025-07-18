#### 查看运行端口

lsof -i:8080
netstat -tunlp
netstat -anp | grep 8080

#### 查看详细进程

ps aux | grep tomcat
ps -ef | grep rsync

#### 内存最多的10个进程

ps -aux | sort -k4nr | head -10

#### 查IP地址

ifconfig
curl cip.cc

#### 查看Linux内核

lsb_release -a

#### 添加定时执行计划表

crontab -e
crontab -l
service crond restart , Ubuntu 使用 sudo service cron restart
tail -f /var/log/cron

#### Linux磁盘空间分析

- df -h 查看整体占用情况
- du -sh 查看整个当前文件的占用情况
- du -lh --max-depth=1 查看当前目录子目录占用情况
- ls -lh 查看每个文件的占用情况

#### 压缩解压文件

- tar -zcvf my.tar.gz /my
- tar -xzvf my.tar.gz && rm -f my.tar.gz

#### Linux扩容磁盘步骤  在线不停机扩容磁盘设置LVM（Logical Volume Manager）逻辑卷管理

- 查看所有设备挂载情况 :  lsblk 或 lsblk -f
- dev设备下新增加sdb磁盘分区(系统盘无法再分区) 分别选m n p w (fdisk支持2TB大小内分区 新的空GPT分区表解决) :  fdisk /dev/sdb
- dev/sdb磁盘下sdb1格式化新分区 xfs格式化: mkfs.xfs /dev/sdb1  或  ext4分区类型:  mkfs -t ext4 /dev/sdb1 
- 分区挂载到目录才能生效 mount 设备名称 挂载目录 :  mount /dev/sdb1 /mnt/nfs_data
- 注意重启系统后磁盘挂载会失效 自动挂载配置 :  vim /etc/fstab 执行 blkid 命令查看UUID和文件类型 
- 最后一行添加  如 UUID=xxxxx /tidb-data xfs defaults 0 1  保持重启挂载生效 执行 systemctl daemon-reload
- 卸载目录命令 :  umount /dev/sdb1


#### 创建虚拟IP命令 基于ARP是地址解析协议 每台主机中都有一个ARP高速缓存 存储同一个网络内的IP地址与MAC地址的对应关系 操作系统会自动维护这个缓存 IP漂移Keepalived完成主备切换

ip addr 或 ip a
arp -a

- 注意keepalived会自动配置无需手动配置虚拟IP 手动只一台主机器设置即可会自动IP漂移 添加一个VIP地址 eth0:1表示这个VIP绑定的目标网卡设备
  ifconfig eth0:1 172.16.0.199 broadcast 172.16.0.255 netmask 255.255.255.0 up
  route add -host 172.16.0.199 dev eth0:1 # 通过route命令，在路由表上添加对这个VIP的路由信息
  ping 172.16.0.199 # 测试虚拟VIP是否成功
  /sbin/ip addr del 172.16.0.199/24 dev eth0:1 # 删除虚拟IP
  sudo ip neigh flush 172.16.0.199/24 # 清除ARP本机缓存

#### 防火墙相关命令

- systemctl status firewalld.service 或 sudo ufw status  # 查看防火墙状态
- systemctl stop firewalld.service 或 sudo ufw disable   # 重启还会打开防火墙
- systemctl disable --now firewalld.service 或 sudo ufw reset # 永久禁用防火墙
- systemctl disable --now iptables # 永久禁用防火墙配置表
- iptables -F # 清空防火墙配置规则 设置

#### 建立SSH免密连接

- ssh-keygen -t rsa root用户在/root/.ssh/id_rsa.pub
- 公钥放在远程访问服务的/root/.ssh/authorized_keys里 重启sshd服务生效 执行 ssh root@ip 命令访问确认

#### 设置Linux服务器DNS服务加速 nameserver设置如 114.114.114.114, 223.5.5.5, 223.6.6.6, 8.8.8.8, 1.1.1.1

- sudo vim /etc/resolv.conf
- 锁死 chattr +i /etc/resolv.conf 防止重启系统数据丢失

#### 安装Ubuntu服务器系统或VMware ESXI裸机虚拟化

- 首先将服务器系统版本的ISO镜像下载到高配U盘 , 使用Rufus软件(引导ISO镜像Windows、Linux、VMware ESXI、UEFI等)制作的USB的系统启动引导盘 (安装完成reboot now重启系统前先拔出U盘, 再物理重启进入系统) , Help选项可提前进入shell控制
- 插入U盘启动主机大部分长按Delete或F2可以进入BIOS页面选择USB系统启动引导盘 F10保存设置 (BIOS界面不同电脑快捷键不一样 有EFI源文件安装 一般台式机开机按delete键可以进入BIOS，笔记本按F2键进入BIOS)
- 断电后上电自动启动系统: 在服务器的BIOS设置中，找到相关的自启动选项。 通常选项称为“Power AC”或类似的名称。 将其设置为“On”或“Always On”，服务器在断电后上电就会自动启动
- 安装参考文章: https://developer.aliyun.com/article/927675  设置阿里云镜像源: http://mirrors.aliyun.com/ubuntu/
- Ubuntu系统设置固定静态IP地址: _linux/network目录下有配置 命令如 sudo nano /etc/netplan/00-installer-config.yaml
- CentOS系统设置固定静态IP地址: 在 /etc/sysconfig/network-scripts/ifcfg-* 配置后执行systemctl restart network生效 在VMware中使用自动桥接配置 网关使用宿主机相同的网关地址并且主网络开启桥接协议、 journalctl查看xfs_repair修复emergency mode紧急模式磁盘挂载故障
- 固定IP可在路由器上设置静态地址 防止被DHCP动态分配
- 开启Ubuntu系统 root用户访问ssh远程访问权限(sudo passwd root) : https://blog.csdn.net/boonya/article/details/121256380
  su root 再执行 sudo vim /etc/ssh/sshd_config 添加 PermitRootLogin yes 生效 sudo systemctl restart sshd
  切换到root用户命令 sudo -i
-  同步系统时区操作
   sudo timedatectl set-timezone Asia/Shanghai  # 设置时区
   sudo timedatectl set-ntp true  # 设置NTP时间同步

#### 内网穿透工具 FRP 访问者无需安装客户端外网即可直接访问

- https://github.com/fatedier/frp

