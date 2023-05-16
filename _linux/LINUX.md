#### 查看运行端口

netstat -tunlp
netstat -anp | grep 8080
lsof -i:8080

#### 查看详细进程

ps aux | grep tomcat

#### 内存最多的10个进程

ps -aux | sort -k4nr | head -10

#### 查IP地址

ifconfig
curl cip.cc

#### 查看linux内核

lsb_release -a

#### 添加定时执行计划表

crontab -e
crontab -l
service crond restart , Ubuntu 使用 sudo service cron start
tail -f /var/log/cron

#### Linux磁盘空间分析

- df -h 查看整体占用情况
- du -sh 查看整个当前文件的占用情况
- du -lh --max-depth=1 查看当前目录子目录占用情况
- ls -lh 查看每个文件的占用情况

#### Linux扩容磁盘步骤  在线不停机扩容磁盘设置LVM逻辑卷管理

- 查看所有设备挂载情况 :  lsblk 或 lsblk -f 
- dev设备下新sdb磁盘分区 分别选m n p w (fdisk支持2TB大小内分区 新的空GPT分区表解决) :  fdisk /dev/sdb
- dev/sdb下sdb1格式化新分区 ext4分区类型 :  mkfs -t ext4 /dev/sdb1
- 分区挂载到目录才能生效 mount 设备名称 挂载目录 :  mount /dev/sdb1 /my
- 卸载目录 :  umount /dev/sdb1

#### 创建虚拟IP命令 基于ARP是地址解析协议 每台主机中都有一个ARP高速缓存 存储同一个网络内的IP地址与MAC地址的对应关系 操作系统会自动维护这个缓存 IP漂移Keepalived完成主备切换

ip addr
arp -a
ifconfig eth0:1 192.168.0.199 broadcast 192.168.0.255 netmask 255.255.255.0 up  # 在一台机器设置即可会自动IP漂移 添加一个VIP地址  eth0:1表示这个VIP绑定的目标网卡设备
route add -host 192.168.0.199 dev eth0:1                    # 通过route命令，在路由表上添加对这个VIP的路由信息
ping 192.168.0.199                                        # 测试虚拟VIP是否成功
/sbin/ip addr del 192.168.0.199/24 dev eth0:1               # 删除虚拟IP

#### 建立免密连接

- ssh-keygen -t rsa root用户在/root/.ssh/id_rsa.pub
- 公钥放在远程访问服务的/root/.ssh/authorized_keys里 执行 ssh root@ip 命令访问确认

#### 设置Linux服务器DNS服务 如 144.144.144.144 , 223.5.5.5, 223.6.6.6, 8.8.8.8

- sudo vim /etc/resolv.conf

#### 安装Ubuntu服务器系统 大部分按F12可以进入USB启动引导盘安装 (不同电脑快捷键不一样 有EFI源文件安装)

- 首先将系统版本的ISO镜像下载到U盘 , 使用Rufus软件制作的USB的启动引导盘安装
- 安装参考文章: https://bynss.com/howto/633952.html
- Ubuntu系统设置固定静态IP地址: _linux/network目录下有配置 参考文章：https://ld246.com/article/1593929878472
- 固定IP可在路由器上设置静态地址 防止被DHCP动态分配
- 开启Ubuntu系统 root用户访问ssh远程访问权限(sudo passwd root)  : https://blog.csdn.net/boonya/article/details/121256380
   su root 再执行 sudo vim /etc/ssh/sshd_config 添加 PermitRootLogin yes  生效 sudo systemctl restart sshd  切换到root用户命令 sudo -i

##### 在Ubuntu上安装图形化界面

sudo sudo apt update && apt-get update && apt-get upgrade
apt-get install -y ubuntu-desktop # Gnome桌面
reboot

- 使用阿里云自带的远程连接VNC
- sudo apt-get install xrdp vnc4server xbase-clients # 安装VNC
- sudo apt-get install dconf-editor # 安装dconf-editor 5yX6xi
- 取消权限限制：打开dconf-editor，依次展开org->gnome->desktop->remote-access，然后取消 “requlre-encryption”的勾选即可
- VNC工具或者Windows自带的mstsc(远程桌面控制)进行访问就行
- 选择模式【vnc-any】，然后输入IP地址和密码进行登录 其中端口号默认为5900

##### Windows10原生安装Linux子系统WSL(Windows Subsystem for Linux)

- 远程连接wsl apt-get remove -y openssh-server && apt-get install -y openssh-server
- 编辑sshd_config文件 vim /etc/ssh/sshd_config
  Port 2222 #设置ssh的端口号, 由于22在windows中有别的用处, 尽量不修改系统的端口号
  PermitRootLogin yes # 允许root远程登录
  PasswordAuthentication yes # 密码验证登录
- 重置密码 passwd
- 重启服务 sudo service ssh --full-restart
- 格式化linux磁盘 mkfs -t ext4 /dev/vda1

##### Docker中的MacOS系统 [Docker-OSX](https://github.com/sickcodes/Docker-OSX)

- 在QEMU / KVM上运行macOS [OSX-KVM](https://github.com/kholia/OSX-KVM)
- docker pull sickcodes/docker-osx:latest

##### 在Ubuntu20 上安装卸载VirtualBox

- 相关下载地址 https://www.virtualbox.org/wiki/Downloads  https://ubuntu.com/download/desktop
- 可使用macos-guest-virtualbox.sh脚本自动安装MacOS官方镜像 sudo apt-get install -y coreutils gzip unzip wget xxd dmg2img
  sudo apt update && sudo apt install -y build-essential dkms linux-headers-$(uname -r)  
  wget -q https://www.virtualbox.org/download/oracle_vbox.asc -O- | sudo apt-key add -
  wget -q https://www.virtualbox.org/download/oracle_vbox_2016.asc -O- | sudo apt-key add -
  sudo apt-get install -y software-properties-common
  sudo apt install --reinstall libqt5widgets5
  sudo apt install --reinstall libqt5dbus5
  sudo apt install --reinstall libxcb-xinerama0
  sudo apt-get install libxkbcommon-x11-0
  sudo apt-get install libxcb-xinerama0
  export QT_DEBUG_PLUGINS=1
  sudo add-apt-repository "deb [arch=amd64] http://download.virtualbox.org/virtualbox/debian $(lsb_release -cs) contrib"
  sudo apt-get install -y virtualbox-6.1
  virtualbox
  sudo apt remove -y virtualbox

##### 一步一步搭建Cygwin最小系统

- 安装时筛选软件包及操作 软件包被按照用分类组织，点击“View”旁边下来框，选择“Category”，看到有Accessibility、Admin、Base、Devel、Doc等多个类别
- coreutils gzip unzip wget xxd dmg2img
- cd d: 找到执行shell文件
