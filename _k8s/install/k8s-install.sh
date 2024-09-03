#!/usr/bin/env bash
# Author: 潘维吉
# kubernetes云原生容器编排管理安装  生产级别的容器编排系统  自动化的容器部署、扩展和管理
# chmod +x k8s-install.sh 给shell脚本执行文件可执行权限

#./docker-install-centos.sh

echo "国内网络原因 设置阿里镜像源"
cat <<EOF >/etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=http://mirrors.aliyun.com/kubernetes/yum/repos/kubernetes-el7-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=http://mirrors.aliyun.com/kubernetes/yum/doc/yum-key.gpg
       http://mirrors.aliyun.com/kubernetes/yum/doc/rpm-package-key.gpg
EOF

#echo "配置Master Node节点 修改各自的hostname和hosts文件"
#hostnamectl set-hostname k8s-master01 # 设置主机名 部署master节点开启
#hostnamectl set-hostname k8s-node01    # 设置主机名 部署node节点开启

#echo '127.0.0.1  k8s-master01
#127.0.0.1 k8s-node01' >>/etc/hosts

echo "安装Kubernetes"
sudo dnf install -y kubelet kubeadm kubectl --disableexcludes=kubernetes
# apt-get install -y kubelet kubeadm kubectl

echo "启动Kubernetes服务并加入开机自启动"
systemctl enable --now kubelet

echo "关闭防火墙"
systemctl disable --now firewalld

echo "关闭Swap分区 "
swapoff -a && sysctl -w vm.swappiness=0 # 关闭swap
sed -ri '/^[^#]*swap/s@^@#@' /etc/fstab # 取消开机挂载swap
free                                    # 使用 kubeadm 部署集群必须关闭Swap分区 Swap已经为0是关闭

echo "禁用SELinux"
sed -ri 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config # 修改配置
sestatus                                                            # 检查是否开启

echo "桥接流量"
cat <<EOF >/etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
EOF
sysctl --system

echo "所有节点同步时间"
ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
echo 'Asia/Shanghai' /etc/timezone

echo "创建一个守护程序文件 docker与k8s一致 使用systemd"
sudo cat <<EOF >/etc/docker/daemon.json
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m"
  },
  "storage-driver": "overlay2",
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ]
}
EOF
systemctl restart docker # 重启使配置生效

echo "Kubernetes版本"
sudo kubectl version

#echo "安装Dashboard UI管理集群资源 扩展部署，启动滚动更新，重新启动Pod或部署新应用程序等"
#wget https://raw.githubusercontent.com/kubernetes/dashboard/v2.0.0-beta4/aio/deploy/recommended.yaml
#kubectl apply -f recommended.yaml
#kubectl get pods --all-namespaces | grep dashboard

#echo "初始化集群"
#sudo kubeadm config images pull
#sudo kubeadm init

echo "重启配置生效"
sleep 3s
#reboot
