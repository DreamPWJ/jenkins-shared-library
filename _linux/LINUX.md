#### 查看运行端口

netstat -tunlp
netstat -anp | grep 8080
lsof -i:8080

#### 查看详细进程

ps aux | grep tomcat

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

#### 建立免密连接
- ssh-keygen -t rsa   root用户在/root/.ssh/id_rsa.pub
- 公钥放在远程访问服务的/root/.ssh/authorized_keys里  执行 ssh root@ip 命令访问确认  
   
#### 基础环境安装
##### 安装git
yum install -y git
git --version
- 查看编辑git配置
git config --list
git config --global --edit

##### 安装jdk
yum search java
yum install -y java-1.8.0-openjdk-devel.x86_64 // java-1.8.0-openjdk-devel.x86_64
apt install -y openjdk-8-jre-headless
java -version
which java
yum  remove -y java-1.8.0-openjdk.x86_64 

##### 安装maven
mkdir -p /opt/maven && cd /opt/maven && wget https://mirror.its.dal.ca/apache/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz
tar -xzvf  apache-maven-3.6.3-bin.tar.gz
vim /etc/profile

//export JAVA_HOME=/usr/bin/java
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-11.0.9.11-0.el8_2.x86_64
export JRE_HOME=$JAVA_HOME/jre
export CLASSPATH=$JAVA_HOME/lib:$JRE_HOME/lib:$CLASSPATH
export MAVEN_HOME=/opt/maven/apache-maven-3.6.3
export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH

. /etc/profile && mvn -version
echo $MAVEN_HOME && echo $JAVA_HOME && echo $PATH

##### 安装nodejs

curl -sL https://rpm.nodesource.com/setup_12.x | sudo bash -
yum install -y nodejs
npm install -g npm
npm install -g n
n 12.18.3 // n stable
node -v && npm -v
which node
yum remove -y nodejs

#### 安装Ubuntu服务器系统

- 参考文章: https://bynss.com/howto/633952.html
- 固定IP可在路由器上设置静态地址 防止被DHCP动态分配

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
-  远程连接wsl  apt-get remove -y openssh-server && apt-get install -y openssh-server
-  编辑sshd_config文件 vim /etc/ssh/sshd_config
Port 2222   #设置ssh的端口号, 由于22在windows中有别的用处, 尽量不修改系统的端口号
PermitRootLogin yes            # 允许root远程登录
PasswordAuthentication yes     # 密码验证登录
- 重置密码 passwd
- 重启服务 sudo service ssh --full-restart
- 格式化linux磁盘   mkfs -t ext4 /dev/vda1

##### Docker中的MacOS系统 [Docker-OSX](https://github.com/sickcodes/Docker-OSX)
- 在QEMU / KVM上运行macOS [OSX-KVM](https://github.com/kholia/OSX-KVM)
- docker pull sickcodes/docker-osx:latest

##### 在Ubuntu20 上安装卸载VirtualBox 
- 相关下载地址 https://www.virtualbox.org/wiki/Downloads  https://ubuntu.com/download/desktop
- 可使用macos-guest-virtualbox.sh脚本自动安装MacOS官方镜像 sudo apt-get install -y coreutils gzip unzip wget xxd dmg2img
sudo apt update && sudo apt install -y build-essential dkms linux-headers-$(uname -r)  
wget -q https://www.virtualbox.org/download/oracle_vbox.asc -O- | sudo apt-key add -
wget -q https://www.virtualbox.org/download/oracle_vbox_2016.asc -O- | sudo apt-key add -
sudo  apt-get install -y software-properties-common
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
