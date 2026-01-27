#!/bin/bash

#################################################
# Kubernetes 集群部署脚本
# 模式: 单机(Single) / 多机(Multi)
#################################################

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 错误处理
error_exit() {
    log_error "$1"
    exit 1
}

# 检查是否为root用户
check_root() {
    if [[ $EUID -ne 0 ]]; then
        error_exit "请使用 root 用户或 sudo 运行此脚本"
    fi
}

# 检查Ubuntu版本
check_ubuntu_version() {
    if [[ ! -f /etc/os-release ]]; then
        error_exit "无法检测操作系统版本"
    fi

    . /etc/os-release
    if [[ "$ID" != "ubuntu" ]]; then
        error_exit "此脚本仅支持 Ubuntu 系统"
    fi

    log_info "检测到 Ubuntu $VERSION_ID"
}

# 配置参数
K8S_VERSION="1.28.2"
CONTAINERD_VERSION="1.7.8"
CNI_VERSION="1.3.0"
CALICO_VERSION="v3.26.3"

# 国内镜像源
ALIYUN_MIRROR="registry.cn-hangzhou.aliyuncs.com/google_containers"
DOCKER_MIRROR="https://docker.mirrors.ustc.edu.cn"

# 系统初始化
system_init() {
    log_info "开始系统初始化..."

    # 关闭swap
    log_info "关闭 swap..."
    swapoff -a
    sed -i '/swap/s/^/#/' /etc/fstab

    # 关闭防火墙
    log_info "配置防火墙..."
    systemctl stop ufw 2>/dev/null || true
    systemctl disable ufw 2>/dev/null || true

    # 关闭SELinux (Ubuntu通常不启用)
    if command -v getenforce &> /dev/null; then
        setenforce 0 2>/dev/null || true
        sed -i 's/^SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config 2>/dev/null || true
    fi

    # 配置内核参数
    log_info "配置内核参数..."
    cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
vm.swappiness                       = 0
EOF

    # 加载 br_netfilter 模块
    modprobe br_netfilter
    modprobe overlay

    cat > /etc/modules-load.d/k8s.conf <<EOF
br_netfilter
overlay
EOF

    sysctl --system > /dev/null 2>&1

    # 配置时区
    timedatectl set-timezone Asia/Shanghai 2>/dev/null || true

    log_info "系统初始化完成"
}

# 配置国内镜像源
configure_apt_mirror() {
    log_info "配置 APT 国内镜像源..."

    # 备份原有源
    cp /etc/apt/sources.list /etc/apt/sources.list.bak.$(date +%Y%m%d%H%M%S)

    # 使用阿里云镜像源
    cat > /etc/apt/sources.list <<EOF
deb http://mirrors.aliyun.com/ubuntu/ $(lsb_release -cs) main restricted universe multiverse
deb http://mirrors.aliyun.com/ubuntu/ $(lsb_release -cs)-security main restricted universe multiverse
deb http://mirrors.aliyun.com/ubuntu/ $(lsb_release -cs)-updates main restricted universe multiverse
deb http://mirrors.aliyun.com/ubuntu/ $(lsb_release -cs)-backports main restricted universe multiverse
EOF

    # 更新软件包列表
    log_info "更新软件包列表..."
    apt-get update -y || error_exit "APT 更新失败"
}

# 安装依赖包
install_dependencies() {
    log_info "安装依赖包..."

    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        apt-transport-https \
        ca-certificates \
        curl \
        gnupg \
        lsb-release \
        software-properties-common \
        wget \
        net-tools \
        ipvsadm \
        ipset \
        conntrack \
        socat \
        || error_exit "依赖包安装失败"

    log_info "依赖包安装完成"
}

# 安装 containerd
install_containerd() {
    log_info "安装 containerd..."

    # 检查是否已安装
    if command -v containerd &> /dev/null; then
        log_warn "containerd 已安装,跳过..."
        return
    fi

    # 安装 containerd
    apt-get install -y containerd || error_exit "containerd 安装失败"

    # 创建配置目录
    mkdir -p /etc/containerd

    # 生成默认配置
    containerd config default > /etc/containerd/config.toml

    # 配置 systemd cgroup 驱动
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

    # 配置国内镜像加速
    sed -i '/\[plugins."io.containerd.grpc.v1.cri".registry.mirrors\]/a\        [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]\n          endpoint = ["https://docker.mirrors.ustc.edu.cn"]' /etc/containerd/config.toml

    # 配置 sandbox_image 使用国内镜像
    sed -i "s|registry.k8s.io/pause:|${ALIYUN_MIRROR}/pause:|g" /etc/containerd/config.toml

    # 启动 containerd
    systemctl daemon-reload
    systemctl enable containerd
    systemctl restart containerd

    # 验证安装
    if ! systemctl is-active --quiet containerd; then
        error_exit "containerd 启动失败"
    fi

    log_info "containerd 安装完成"
}

# 安装 kubeadm、kubelet、kubectl
install_kubernetes() {
    log_info "安装 Kubernetes 组件..."

    # 添加阿里云 Kubernetes 源
    curl -fsSL https://mirrors.aliyun.com/kubernetes/apt/doc/apt-key.gpg | apt-key add - || error_exit "添加 GPG 密钥失败"

    cat > /etc/apt/sources.list.d/kubernetes.list <<EOF
deb https://mirrors.aliyun.com/kubernetes/apt/ kubernetes-xenial main
EOF

    # 更新软件包列表
    apt-get update -y || error_exit "更新软件包列表失败"

    # 安装指定版本
    KUBE_VERSION="${K8S_VERSION}-00"

    # 检查版本是否可用
    if ! apt-cache madison kubeadm | grep -q "$KUBE_VERSION"; then
        log_warn "指定版本 $KUBE_VERSION 不可用,安装最新稳定版本"
        KUBE_VERSION=""
    fi

    # 安装 Kubernetes 组件
    if [[ -n "$KUBE_VERSION" ]]; then
        apt-get install -y kubelet=$KUBE_VERSION kubeadm=$KUBE_VERSION kubectl=$KUBE_VERSION || error_exit "Kubernetes 组件安装失败"
        apt-mark hold kubelet kubeadm kubectl
    else
        apt-get install -y kubelet kubeadm kubectl || error_exit "Kubernetes 组件安装失败"
        apt-mark hold kubelet kubeadm kubectl
    fi

    # 启动 kubelet
    systemctl enable kubelet

    log_info "Kubernetes 组件安装完成"
}

# 初始化 Master 节点
init_master() {
    local pod_network_cidr="10.244.0.0/16"
    local service_cidr="10.96.0.0/12"

    log_info "初始化 Kubernetes Master 节点..."

    # 创建 kubeadm 配置文件
    cat > /tmp/kubeadm-config.yaml <<EOF
apiVersion: kubeadm.k8s.io/v1beta3
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
imageRepository: ${ALIYUN_MIRROR}
networking:
  podSubnet: ${pod_network_cidr}
  serviceSubnet: ${service_cidr}
---
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
cgroupDriver: systemd
EOF

    # 提前拉取镜像
    log_info "拉取 Kubernetes 镜像..."
    kubeadm config images pull --config=/tmp/kubeadm-config.yaml || log_warn "镜像拉取失败,继续初始化..."

    # 初始化集群
    log_info "执行集群初始化(这可能需要几分钟)..."
    kubeadm init --config=/tmp/kubeadm-config.yaml || error_exit "集群初始化失败"

    # 配置 kubectl
    log_info "配置 kubectl..."
    mkdir -p $HOME/.kube
    cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
    chown $(id -u):$(id -g) $HOME/.kube/config

    # 配置普通用户(如果存在非root用户)
    for user_home in /home/*; do
        if [[ -d "$user_home" ]]; then
            local username=$(basename "$user_home")
            mkdir -p "$user_home/.kube"
            cp -f /etc/kubernetes/admin.conf "$user_home/.kube/config"
            chown -R "$username:$username" "$user_home/.kube"
        fi
    done

    log_info "Master 节点初始化完成"
}

# 安装 Calico 网络插件
install_calico() {
    log_info "安装 Calico 网络插件..."

    # 下载 Calico manifest
    wget -q https://docs.projectcalico.org/${CALICO_VERSION}/manifests/calico.yaml -O /tmp/calico.yaml || \
    wget -q https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VERSION}/manifests/calico.yaml -O /tmp/calico.yaml || \
    error_exit "Calico manifest 下载失败"

    # 应用 Calico
    kubectl apply -f /tmp/calico.yaml || error_exit "Calico 安装失败"

    log_info "Calico 网络插件安装完成"
}

# 单机模式:允许 Master 调度 Pod
enable_master_scheduling() {
    log_info "配置单机模式:允许 Master 节点调度 Pod..."

    # 等待节点就绪
    sleep 10

    # 去除 Master 节点的污点
    kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true
    kubectl taint nodes --all node-role.kubernetes.io/master- 2>/dev/null || true

    log_info "单机模式配置完成"
}

# 生成 Worker 节点加入命令
generate_join_command() {
    log_info "生成 Worker 节点加入命令..."

    local join_command=$(kubeadm token create --print-join-command)

    echo ""
    log_info "=========================================="
    log_info "Worker 节点加入命令:"
    echo ""
    echo "$join_command"
    echo ""
    log_info "=========================================="
    echo ""
    echo "$join_command" > /root/k8s-join-command.sh
    chmod +x /root/k8s-join-command.sh

    log_info "加入命令已保存到: /root/k8s-join-command.sh"
}

# 显示集群信息
show_cluster_info() {
    log_info "等待集群就绪..."
    sleep 15

    log_info "=========================================="
    log_info "集群部署完成!"
    log_info "=========================================="
    echo ""

    log_info "集群节点信息:"
    kubectl get nodes -o wide
    echo ""

    log_info "集群 Pod 信息:"
    kubectl get pods -A
    echo ""

    log_info "常用命令:"
    echo "  查看节点: kubectl get nodes"
    echo "  查看Pod:  kubectl get pods -A"
    echo "  查看服务: kubectl get svc -A"
    echo ""
}

# 主菜单
main_menu() {
    clear
    echo "=========================================="
    echo "   Kubernetes 集群部署脚本"
    echo "=========================================="
    echo ""
    echo "请选择部署模式:"
    echo "  1) 单机模式 (Single Node)"
    echo "  2) 多机模式 - Master 节点"
    echo "  3) 多机模式 - Worker 节点"
    echo "  4) 仅安装基础组件(不初始化集群)"
    echo "  0) 退出"
    echo ""
    read -p "请输入选项 [0-4]: " choice

    case $choice in
        1)
            deploy_single_node
            ;;
        2)
            deploy_master_node
            ;;
        3)
            deploy_worker_node
            ;;
        4)
            install_components_only
            ;;
        0)
            log_info "退出脚本"
            exit 0
            ;;
        *)
            log_error "无效的选项"
            exit 1
            ;;
    esac
}

# 单机模式部署
deploy_single_node() {
    log_info "=========================================="
    log_info "开始单机模式部署"
    log_info "=========================================="
    echo ""

    check_root
    check_ubuntu_version

    system_init
    configure_apt_mirror
    install_dependencies
    install_containerd
    install_kubernetes
    init_master
    install_calico
    enable_master_scheduling
    show_cluster_info

    log_info "=========================================="
    log_info "单机模式部署完成!"
    log_info "=========================================="
}

# Master 节点部署
deploy_master_node() {
    log_info "=========================================="
    log_info "开始 Master 节点部署"
    log_info "=========================================="
    echo ""

    check_root
    check_ubuntu_version

    system_init
    configure_apt_mirror
    install_dependencies
    install_containerd
    install_kubernetes
    init_master
    install_calico
    generate_join_command
    show_cluster_info

    log_info "=========================================="
    log_info "Master 节点部署完成!"
    log_info "请在 Worker 节点上运行加入命令"
    log_info "=========================================="
}

# Worker 节点部署
deploy_worker_node() {
    log_info "=========================================="
    log_info "开始 Worker 节点部署"
    log_info "=========================================="
    echo ""

    check_root
    check_ubuntu_version

    system_init
    configure_apt_mirror
    install_dependencies
    install_containerd
    install_kubernetes

    echo ""
    log_info "=========================================="
    log_info "Worker 节点基础组件安装完成!"
    log_info "=========================================="
    echo ""
    log_warn "请在 Master 节点执行以下命令获取加入命令:"
    echo "  kubeadm token create --print-join-command"
    echo ""
    log_warn "然后在本节点执行该命令加入集群"
    echo ""
}

# 仅安装组件
install_components_only() {
    log_info "=========================================="
    log_info "仅安装基础组件"
    log_info "=========================================="
    echo ""

    check_root
    check_ubuntu_version

    system_init
    configure_apt_mirror
    install_dependencies
    install_containerd
    install_kubernetes

    log_info "=========================================="
    log_info "基础组件安装完成!"
    log_info "=========================================="
}

# 脚本入口
main() {
    # 检查网络连接
    if ! ping -c 1 mirrors.aliyun.com &> /dev/null; then
        log_warn "无法连接到阿里云镜像源,请检查网络连接"
    fi

    main_menu
}

# 执行主函数
main