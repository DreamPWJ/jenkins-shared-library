#!/bin/bash

#================================================================
# Kubernetes 集群初始化脚本
# 支持: Ubuntu/Debian/CentOS/RHEL, 单机/集群模式, 多版本K8s
#================================================================

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 配置变量
K8S_VERSION=${K8S_VERSION:-"1.35.0"}
CONTAINER_RUNTIME=${CONTAINER_RUNTIME:-"containerd"}  # containerd or docker
POD_NETWORK_CIDR=${POD_NETWORK_CIDR:-"10.244.0.0/16"}
SERVICE_CIDR=${SERVICE_CIDR:-"10.96.0.0/12"}
CLUSTER_MODE=${CLUSTER_MODE:-"single"}  # single or cluster
NODE_ROLE=${NODE_ROLE:-"master"}  # master or worker
USE_MIRROR=${USE_MIRROR:-"auto"}  # auto, yes, no - 是否使用国内镜像源

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

# 检测是否在国内网络环境
detect_network() {
    if [ "$USE_MIRROR" = "yes" ]; then
        log_info "强制使用国内镜像源"
        return 0
    elif [ "$USE_MIRROR" = "no" ]; then
        log_info "使用官方源"
        return 1
    fi
    
    # 自动检测
    log_info "检测网络环境..."
    if curl -m 5 -s https://www.google.com > /dev/null 2>&1; then
        log_info "检测到可访问国际网络,使用官方源"
        return 1
    else
        log_warn "无法访问国际网络,将使用国内镜像源"
        return 0
    fi
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
    log_info "安装Kubernetes依赖包..."
    
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
    # 检查是否已安装
    if command -v containerd >/dev/null 2>&1; then
        log_info "检测到 containerd 已安装,版本: $(containerd --version | awk '{print $3}')"
        if systemctl is-active --quiet containerd; then
#            log_info "containerd 服务正在运行, 跳过安装, 并升级到最新版本"
#                case $OS in
#                    ubuntu|debian)
#                       sudo apt install --only-upgrade containerd.io
#                        ;;
#                    centos|rhel)
#                        sudo yum update containerd.io
#                        ;;
#                    *)
#                        log_error "不支持的操作系统: $OS"
#                        ;;
#                esac

            # 验证 CRI 接口
            if ! crictl version > /dev/null 2>&1; then
                log_error "CRI 接口验证失败，containerd 配置有问题"
                log_info "开始启用CRI插件配置..."
                # 1. 停止 containerd
                sudo systemctl stop containerd
                # 2. 备份当前配置
                sudo cp /etc/containerd/config.toml /etc/containerd/config.toml.backup
                # 3. 生成新的正确配置
                sudo containerd config default > /etc/containerd/config.toml
                # 4. 启用 CRI 插件和 systemd cgroup
                sudo sed -i 's/SystemdCgroup = false/SystemdCgroup = true/g' /etc/containerd/config.toml
                # 5. 重启服务
                sudo systemctl daemon-reload
                sudo systemctl start containerd
                # 6. 验证 CRI
                sudo crictl version
            else
                log_info "Containerd CRI 接口验证成功"
            fi

            return 0
        else
            log_warn "containerd 已安装但未运行, 将重新配置"
        fi
    fi
    
    log_info "安装 containerd..."
    
    local SUCCESS=false
    
    # 检测是否使用国内镜像
    if detect_network; then
        log_info "尝试使用阿里云镜像源..."
        case $OS in
            ubuntu|debian)
                # 使用阿里云镜像
                curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/$OS/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg 2>/dev/null || true
                if [ -f /etc/apt/keyrings/docker.gpg ]; then
                    chmod a+r /etc/apt/keyrings/docker.gpg
                    echo \
                      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/$OS \
                      $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
                    
                    if apt-get update && apt-get install -y containerd.io; then
                        SUCCESS=true
                    fi
                fi
                ;;
            centos|rhel)
                yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
                if yum install -y containerd.io; then
                    SUCCESS=true
                fi
                ;;
        esac
    fi
    
    # 如果国内源失败,尝试官方源
    if [ "$SUCCESS" = false ]; then
        log_warn "国内镜像源失败或未使用,尝试官方源..."
        case $OS in
            ubuntu|debian)
                install -m 0755 -d /etc/apt/keyrings
                curl -fsSL https://download.docker.com/linux/$OS/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
                chmod a+r /etc/apt/keyrings/docker.gpg
                
                echo \
                  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/$OS \
                  $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
                
                apt-get update
                if apt-get install -y containerd.io; then
                    SUCCESS=true
                fi
                ;;
            centos|rhel)
                yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
                if yum install -y containerd.io; then
                    SUCCESS=true
                fi
                ;;
        esac
    fi
    
    if [ "$SUCCESS" = false ]; then
        log_error "containerd 安装失败,请检查网络连接或手动安装"
    fi
    
    # 配置 containerd
    mkdir -p /etc/containerd
    containerd config default > /etc/containerd/config.toml
    
    # 配置 systemd cgroup 驱动
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
    
    # 配置镜像加速
    if detect_network; then
        log_info "配置国内镜像加速..."
        # 配置阿里云镜像加速
        sed -i '/\[plugins."io.containerd.grpc.v1.cri".registry.mirrors\]/a\        [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]\n          endpoint = ["https://registry.cn-hangzhou.aliyuncs.com"]' /etc/containerd/config.toml
        sed -i 's|registry.k8s.io/pause:3.8|registry.cn-hangzhou.aliyuncs.com/google_containers/pause:3.9|' /etc/containerd/config.toml
        sed -i 's|registry.k8s.io/pause:3.9|registry.cn-hangzhou.aliyuncs.com/google_containers/pause:3.9|' /etc/containerd/config.toml
    fi
    
    systemctl restart containerd
    systemctl enable containerd
    
    log_info "containerd 安装配置完成"
}

# 安装 Docker (可选)
install_docker() {
    # 检查是否已安装
    if command -v docker >/dev/null 2>&1; then
        log_info "检测到 Docker 已安装,版本: $(docker --version | awk '{print $3}')"
        if systemctl is-active --quiet docker; then
            log_info "Docker 服务正在运行,跳过安装"
            return 0
        else
            log_warn "Docker 已安装但未运行,将重新配置"
        fi
    fi
    
    log_info "安装 Docker..."
    
    local SUCCESS=false
    
    # 尝试使用国内镜像
    if detect_network; then
        log_info "尝试使用阿里云镜像源..."
        case $OS in
            ubuntu|debian)
                if apt-get install -y docker-ce docker-ce-cli 2>/dev/null; then
                    SUCCESS=true
                fi
                ;;
            centos|rhel)
                if yum install -y docker-ce docker-ce-cli 2>/dev/null; then
                    SUCCESS=true
                fi
                ;;
        esac
    fi
    
    # 如果失败,尝试官方源
    if [ "$SUCCESS" = false ]; then
        log_warn "尝试官方源..."
        case $OS in
            ubuntu|debian)
                apt-get install -y docker-ce docker-ce-cli || log_error "Docker 安装失败"
                ;;
            centos|rhel)
                yum install -y docker-ce docker-ce-cli || log_error "Docker 安装失败"
                ;;
        esac
    fi
    
    # 配置 Docker daemon
    mkdir -p /etc/docker
    if detect_network; then
        cat <<EOF | tee /etc/docker/daemon.json
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m"
  },
  "storage-driver": "overlay2",
  "registry-mirrors": [
    "https://registry.cn-hangzhou.aliyuncs.com",
    "https://docker.mirrors.ustc.edu.cn"
  ]
}
EOF
    else
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
    fi
    
    systemctl restart docker
    systemctl enable docker
    
    log_info "Docker 安装配置完成"
}

# 安装 kubeadm, kubelet, kubectl
install_k8s_tools() {
    # 检查是否已安装
    if command -v kubeadm >/dev/null 2>&1 && command -v kubelet >/dev/null 2>&1 && command -v kubectl >/dev/null 2>&1; then
        local CURRENT_VERSION=$(kubeadm version -o short 2>/dev/null | sed 's/v//')
        log_info "检测到 Kubernetes 工具已安装,版本: $CURRENT_VERSION"
        if [ "$CURRENT_VERSION" = "$K8S_VERSION" ]; then
            log_info "K8s已安装版本匹配, 跳过安装"
            return 0
        else
            log_warn "版本不匹配(当前: $CURRENT_VERSION, 目标: $K8S_VERSION),将重新安装"
        fi
    fi
    
    log_info "安装 Kubernetes 工具 (版本: $K8S_VERSION)..."
    
    local K8S_VERSION_SHORT=$(echo $K8S_VERSION | cut -d. -f1,2)
    local SUCCESS=false
    
    case $OS in
        ubuntu|debian)
            # 尝试使用阿里云镜像
            if detect_network; then
                log_info "尝试使用阿里云 Kubernetes 镜像源..."
                curl -fsSL https://mirrors.aliyun.com/kubernetes-new/core/stable/v${K8S_VERSION_SHORT}/deb/Release.key | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg 2>/dev/null || true
                
                if [ -f /etc/apt/keyrings/kubernetes-apt-keyring.gpg ]; then
                    echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://mirrors.aliyun.com/kubernetes-new/core/stable/v${K8S_VERSION_SHORT}/deb/ /" | tee /etc/apt/sources.list.d/kubernetes.list
                    
                    if apt-get update && apt-get install -y kubelet kubeadm kubectl; then
                        SUCCESS=true
                    fi
                fi
            fi
            
            # 如果阿里云源失败,尝试官方源
            if [ "$SUCCESS" = false ]; then
                log_warn "阿里云源失败,尝试官方源..."
                curl -fsSL https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION_SHORT}/deb/Release.key | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
                echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION_SHORT}/deb/ /" | tee /etc/apt/sources.list.d/kubernetes.list
                
                apt-get update
                apt-get install -y kubelet kubeadm kubectl || log_error "Kubernetes 工具安装失败"
            fi
            
            apt-mark hold kubelet kubeadm kubectl
            ;;
            
        centos|rhel)
            # 尝试使用阿里云镜像
            if detect_network; then
                log_info "尝试使用阿里云 Kubernetes 镜像源..."
                cat <<EOF | tee /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://mirrors.aliyun.com/kubernetes-new/core/stable/v${K8S_VERSION_SHORT}/rpm/
enabled=1
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/kubernetes-new/core/stable/v${K8S_VERSION_SHORT}/rpm/repodata/repomd.xml.key
EOF
                
                if yum install -y kubelet kubeadm kubectl --disableexcludes=kubernetes 2>/dev/null; then
                    SUCCESS=true
                fi
            fi
            
            # 如果阿里云源失败,尝试官方源
            if [ "$SUCCESS" = false ]; then
                log_warn "阿里云源失败,尝试官方源..."
                cat <<EOF | tee /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION_SHORT}/rpm/
enabled=1
gpgcheck=1
gpgkey=https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION_SHORT}/rpm/repodata/repomd.xml.key
EOF
                
                yum install -y kubelet kubeadm kubectl --disableexcludes=kubernetes || log_error "Kubernetes 工具安装失败"
            fi
            ;;
    esac
    
    systemctl enable kubelet
    
    log_info "Kubernetes 工具安装完成"
}

# 初始化 Master 节点
init_master() {
    log_info "初始化Master节点..."
    
    local INIT_CONFIG="/tmp/kubeadm-config.yaml"
    
    # 根据是否使用国内镜像配置不同的镜像仓库
    if detect_network; then
        log_info "配置使用阿里云镜像仓库..."
        cat <<EOF > $INIT_CONFIG
apiVersion: kubeadm.k8s.io/v1beta4
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
imageRepository: registry.cn-hangzhou.aliyuncs.com/google_containers
networking:
  podSubnet: ${POD_NETWORK_CIDR}
  serviceSubnet: ${SERVICE_CIDR}
---
apiVersion: kubeadm.k8s.io/v1beta4
kind: InitConfiguration
nodeRegistration:
  criSocket: unix:///var/run/containerd/containerd.sock
EOF
    else
        cat <<EOF > $INIT_CONFIG
apiVersion: kubeadm.k8s.io/v1beta4
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
networking:
  podSubnet: ${POD_NETWORK_CIDR}
  serviceSubnet: ${SERVICE_CIDR}
---
apiVersion: kubeadm.k8s.io/v1beta4
kind: InitConfiguration
nodeRegistration:
  criSocket: unix:///var/run/containerd/containerd.sock
EOF
    fi

    log_info "Kubeadm初始化K8s v${K8S_VERSION}版本集群环境..."
    kubeadm init --config=$INIT_CONFIG --upload-certs
    
    # 配置 kubectl
    mkdir -p $HOME/.kube
    cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
    chown $(id -u):$(id -g) $HOME/.kube/config
    
    log_info "Master 节点初始化完成"
    
    # 安装 CNI 插件 (Flannel)
    log_info "安装 Flannel CNI 插件..."
    
    # 尝试从不同源下载
    if detect_network; then
        # 先尝试从 GitHub 下载(可能被代理)
        kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml 2>/dev/null || \
        # 如果失败,尝试从国内镜像
        kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml 2>/dev/null || \
        log_warn "Flannel 安装失败,请手动安装 CNI 插件"
    else
        kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml || \
        log_warn "Flannel 安装失败,请检查网络或手动安装"
    fi
    
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

# 显示使用说明
show_usage() {
    cat <<EOF
使用说明:
========================================
环境变量:
  K8S_VERSION       - K8s 版本 (默认: 1.35.0)
  CONTAINER_RUNTIME - 容器运行时 containerd/docker (默认: containerd)
  POD_NETWORK_CIDR  - Pod 网络 CIDR (默认: 10.244.0.0/16)
  SERVICE_CIDR      - Service CIDR (默认: 10.96.0.0/12)
  CLUSTER_MODE      - 集群模式 single/cluster (默认: single)
  NODE_ROLE         - 节点角色 master/worker (默认: master)
  USE_MIRROR        - 使用镜像源 auto/yes/no (默认: auto)

使用示例:
  # 检查关键端口
  netstat -tunlp | grep -E '6443|10259|10257|10250|2379|2380'

  # 单机模式部署 K8s
  sudo ./k8s_init.sh
  
  # 集群模式 - Master 节点
  sudo K8S_VERSION=1.35.0 CLUSTER_MODE=cluster NODE_ROLE=master ./k8s_init.sh
  
  # 集群模式 - Worker 节点
  sudo NODE_ROLE=worker ./k8s_init.sh
  
  # 使用 Docker 作为容器运行时
  sudo CONTAINER_RUNTIME=docker ./k8s_init.sh
  
  # 强制使用国内镜像源
  sudo USE_MIRROR=yes ./k8s_init.sh
  
  # 强制使用官方源
  sudo USE_MIRROR=no ./k8s_init.sh
========================================
EOF
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