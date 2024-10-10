#!/usr/bin/env bash
# Author: 潘维吉
# 单机版Kubernetes云原生容器编排管理安装  生产级别的容器编排系统  自动化的容器部署、扩展和管理
# 创建 Kubernetes cluster（单机版）最简单的方法是 minikube 实现本地 Kubernetes 集群。
# 参考文档: https://minikube.sigs.k8s.io/docs/start/

# 首先要安装Docker环境
echo "单机版Kubernetes云原生容器编排管理安装"

echo "Debian / Ubuntu阿里云Kubernetes 镜像"
apt-get update && apt-get install -y apt-transport-https || true
curl https://mirrors.aliyun.com/kubernetes/apt/doc/apt-key.gpg | apt-key add -
cat <<EOF >/etc/apt/sources.list.d/kubernetes.list
deb https://mirrors.aliyun.com/kubernetes/apt/ kubernetes-xenial main
EOF
apt-get update

echo "安装 kubectl 命令"
#apt-get install -y kubelet kubeadm kubectl
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
curl -LO "https://dl.k8s.io/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl.sha256"
echo "$(<kubectl.sha256) kubectl" | sha256sum --check
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
chmod +x kubectl
echo "Kubernetes客户端版本信息"
kubectl version --client

echo "安装 minikube 实现本地 Kubernetes 集群"
#apt-get install -y minikube || true
#yum install -y minikube || true
# 文档https://minikube.sigs.k8s.io/docs/start/  选择不同系统和和CPU
# uname -a  查看内核/操作系统/CPU信息
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
minikube version

sleep 1

#if [[ "$(whoami)" != "minikube" ]]; then
#echo "创建新管理账号 用于运行minikube命令"
#sudo useradd -s /bin/bash -mr minikube
#passwd minikube
#sudo adduser minikube sudo
#cat /etc/passwd
## 删除账号
## userdel -r minikube
#fi


echo "启动Kubernetes集群"
sudo usermod -aG docker minikube
# 不允许以 root 用户运行 minikube  从具有管理员访问权限的终端（但未以 root 身份登录）
su minikube &&  minikube start --driver=docker


# 与您的集群交互 访问集群
# kubectl get po -A

# 部署应用程序
# 部署并在端口 8080 上公开它
#kubectl create deployment hello-minikube --image=k8s.gcr.io/echoserver:1.4
#kubectl expose deployment hello-minikube --type=NodePort --port=8080
# 启动服务 或者   kubectl 转发端口
#minikube service hello-minikube  or kubectl port-forward service/hello-minikube 7080:8080

# minikube 捆绑了 Kubernetes 仪表板
# minikube dashboard
