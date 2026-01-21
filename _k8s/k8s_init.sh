#!/bin/bash

#================================================================
# Kubernetes 集群初始化脚本
# 支持: Ubuntu/Debian/CentOS/RHEL, 单机/集群模式, 多版本K8s
#================================================================

# 全局配置变量
K8S_VERSION=${K8S_VERSION:-"1.33.0"}
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-"containerd"}  # containerd or docker
POD_NETWORK_CIDR=${POD_NETWORK_CIDR:-"10.244.0.0/16"}
SERVICE_CIDR=${SERVICE_CIDR:-"10.96.0.0/12"}
CLUSTER_MODE=${CLUSTER_MODE:-"single"}  # single or cluster
NODE_ROLE=${NODE_ROLE:-"master"}  # master or worker

# 显示使用说明
show_usage() {
    cat <<EOF
使用说明:
========================================
环境变量:
  K8S_VERSION       - K8s 版本 (默认: 1.33.0)
  CONTAINER_RUNTIME - 容器运行时 containerd/docker (默认: containerd)
  POD_NETWORK_CIDR  - Pod 网络 CIDR (默认: 10.244.0.0/16)
  SERVICE_CIDR      - Service CIDR (默认: 10.96.0.0/12)
  CLUSTER_MODE      - 集群模式 single/cluster (默认: single)
  NODE_ROLE         - 节点角色 master/worker (默认: master)

使用示例:
    # 文件授权
    chmod +x k8s_init.sh

    # 1. 单机模式快速部署
    sudo bash k8s_init.sh

    # 2. 集群模式 - 初始化 Master 节点
    sudo K8S_VERSION=1.35.0 CLUSTER_MODE=cluster NODE_ROLE=master bash k8s_init.sh

    # 3. 集群模式 - Worker 节点加入集群
    sudo NODE_ROLE=worker bash k8s_init.sh

    # 4. 使用 Docker 运行时
    sudo CONTAINER_RUNTIME=docker bash k8s_init.sh

    # 5. 自定义网络配置
    sudo POD_NETWORK_CIDR=10.100.0.0/16 bash k8s_init.sh
========================================
EOF
}

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 日志函数
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# 检测操作系统
detect_os() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
        OS_VERSION=$VERSION_ID
    else
        log_error "无法检测操作系统"
    fi

    log_info "检测到操作系统: $OS $OS_VERSION"
}

# 检查root权限
check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "请使用 root 权限运行此脚本"
    fi
}

# 系统初始化配置
system_init() {
    log_info "开始系统初始化配置..."

    # 关闭swap
    swapoff -a
    sed -i '/swap/d' /etc/fstab

    # 关闭SELinux (CentOS/RHEL)
    if [ "$OS" = "centos" ] || [ "$OS" = "rhel" ]; then
        setenforce 0 2>/dev/null || true
        sed -i 's/^SELINUX=enforcing$/SELINUX=disabled/' /etc/selinux/config
    fi

    # 关闭防火墙
    if command -v ufw >/dev/null 2>&1; then
        ufw disable
    elif command -v firewalld >/dev/null 2>&1; then
        systemctl stop firewalld
        systemctl disable firewalld
    fi

    # 加载内核模块
    cat <<EOF | tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF

    modprobe overlay
    modprobe br_netfilter

    # 配置内核参数
    cat <<EOF | tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

    sysctl --system

    log_info "系统初始化配置完成"
}

# 安装依赖包
install_dependencies() {
    log_info "安装依赖包..."

    case $OS in
        ubuntu|debian)
            apt-get update
            apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release socat conntrack ipset
            ;;
        centos|rhel)
            yum install -y yum-utils device-mapper-persistent-data lvm2 socat conntrack ipset
            ;;
        *)
            log_error "不支持的操作系统: $OS"
            ;;
    esac
}

# 安装 containerd
install_containerd() {
    log_info "安装 containerd..."

    case $OS in
        ubuntu|debian)
            # 添加 Docker 官方 GPG key
            install -m 0755 -d /etc/apt/keyrings
            curl -fsSL https://download.docker.com/linux/$OS/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
            chmod a+r /etc/apt/keyrings/docker.gpg

            # 添加 Docker 仓库
            echo \
              "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/$OS \
              $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

            apt-get update
            apt-get install -y containerd.io
            ;;
        centos|rhel)
            yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
            yum install -y containerd.io
            ;;
    esac

    # 配置 containerd
    mkdir -p /etc/containerd
    containerd config default > /etc/containerd/config.toml

    # 配置 systemd cgroup 驱动
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

    # 配置镜像加速 (可选)
    sed -i 's|registry.k8s.io/pause:3.8|registry.cn-hangzhou.aliyuncs.com/google_containers/pause:3.9|' /etc/containerd/config.toml

    systemctl restart containerd
    systemctl enable containerd

    log_info "containerd 安装完成"
}

# 安装 Docker (可选)
install_docker() {
    log_info "安装 Docker..."

    case $OS in
        ubuntu|debian)
            apt-get install -y docker-ce docker-ce-cli
            ;;
        centos|rhel)
            yum install -y docker-ce docker-ce-cli
            ;;
    esac

    # 配置 Docker daemon
    mkdir -p /etc/docker
    cat <<EOF | tee /etc/docker/daemon.json
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m"
  },
  "storage-driver": "overlay2"
}
EOF

    systemctl restart docker
    systemctl enable docker

    log_info "Docker 安装完成"
}

# 安装 kubeadm, kubelet, kubectl
install_k8s_tools() {
    log_info "安装 Kubernetes 工具 (版本: $K8S_VERSION)..."

    local K8S_VERSION_SHORT=$(echo $K8S_VERSION | cut -d. -f1,2)

    case $OS in
        ubuntu|debian)
            # 添加 Kubernetes 仓库
            curl -fsSL https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION_SHORT}/deb/Release.key | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
            echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION_SHORT}/deb/ /" | tee /etc/apt/sources.list.d/kubernetes.list

            apt-get update
            apt-get install -y kubelet kubeadm kubectl
            apt-mark hold kubelet kubeadm kubectl
            ;;
        centos|rhel)
            # 添加 Kubernetes 仓库
            cat <<EOF | tee /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION_SHORT}/rpm/
enabled=1
gpgcheck=1
gpgkey=https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION_SHORT}/rpm/repodata/repomd.xml.key
EOF

            yum install -y kubelet kubeadm kubectl --disableexcludes=kubernetes
            ;;
    esac

    systemctl enable kubelet

    log_info "Kubernetes 工具安装完成"
}

# 初始化 Master 节点
init_master() {
    log_info "初始化 Master 节点..."

    local INIT_CONFIG="/tmp/kubeadm-config.yaml"

    cat <<EOF > $INIT_CONFIG
apiVersion: kubeadm.k8s.io/v1beta3
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
networking:
  podSubnet: ${POD_NETWORK_CIDR}
  serviceSubnet: ${SERVICE_CIDR}
---
apiVersion: kubeadm.k8s.io/v1beta3
kind: InitConfiguration
nodeRegistration:
  criSocket: unix:///var/run/containerd/containerd.sock
EOF

    kubeadm init --config=$INIT_CONFIG --upload-certs

    # 配置 kubectl
    mkdir -p $HOME/.kube
    cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
    chown $(id -u):$(id -g) $HOME/.kube/config

    log_info "Master 节点初始化完成"

    # 安装 CNI 插件 (Flannel)
    log_info "安装 Flannel CNI 插件..."
    kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml

    # 如果是单机模式,移除 master 污点
    if [ "$CLUSTER_MODE" = "single" ]; then
        log_info "单机模式: 移除 master 节点污点,允许调度 Pod..."
        kubectl taint nodes --all node-role.kubernetes.io/control-plane- || true
        kubectl taint nodes --all node-role.kubernetes.io/master- || true
    fi

    # 显示加入集群的命令
    log_info "Worker 节点加入集群命令:"
    kubeadm token create --print-join-command
}

# 加入集群作为 Worker 节点
join_cluster() {
    log_info "等待输入 join 命令..."
    echo "请输入 master 节点提供的 join 命令:"
    read -r JOIN_COMMAND

    eval $JOIN_COMMAND

    log_info "Worker 节点加入集群完成"
}


# 主函数
main() {
    log_info "=== Kubernetes 集群初始化脚本 ==="
    log_info "K8s版本: $K8S_VERSION | 容器运行时: $CONTAINER_RUNTIME | 模式: $CLUSTER_MODE | 角色: $NODE_ROLE"

    check_root
    detect_os
    system_init
    install_dependencies

    # 安装容器运行时
    if [ "$CONTAINER_RUNTIME" = "docker" ]; then
        install_docker
    else
        install_containerd
    fi

    install_k8s_tools

    # 根据角色执行不同操作
    if [ "$NODE_ROLE" = "master" ]; then
        init_master

        log_info "================================"
        log_info "Master 节点安装完成!"
        log_info "检查集群状态: kubectl get nodes"
        log_info "检查 Pod 状态: kubectl get pods -A"
        log_info "================================"
    else
        join_cluster

        log_info "================================"
        log_info "Worker 节点加入完成!"
        log_info "请在 Master 节点检查: kubectl get nodes"
        log_info "================================"
    fi
}

# 显示帮助
if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    show_usage
    exit 0
fi

# 执行主函数
main