#!/bin/bash
# Author: 潘维吉
# Description: Kubernetes集群自动化部署脚本 kubeadm支持单机模式和多机模式的Kubernetes集群部署
# 赋予执行权限
#chmod +x k8s-init.sh
# 单机部署（默认）
#./k8s-init.sh --single
# 多机部署（需要指定主节点IP）
#./k8s-init.sh --multi 192.168.1.100
# 指定Kubernetes版本
#./k8s-init.sh --single --version 1.35.0


set -e  # 遇到错误立即退出

# 配置参数
K8S_VERSION="1.35.0"  # Kubernetes版本
POD_NETWORK_CIDR="10.2.0.0/16"  # Pod网络CIDR
SERVICE_CIDR="10.1.0.0/16"  # Service网络CIDR
CLUSTER_TYPE="single"  # 集群类型：single(单机) 或 multi(多机)
MASTER_IP=""  # 主节点IP（多机模式需要设置）
NODE_IPS=()  # 工作节点IP数组

# 颜色定义用于输出显示
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 检查执行权限
check_privileges() {
    if [[ $EUID -ne 0 ]]; then
        log_error "请使用root权限运行此脚本"
        exit 1
    fi
    log_info "权限检查通过"
}

# 环境检测和配置
detect_environment() {
    log_info "开始检测系统环境..."

    # 检测操作系统
    if grep -q "Ubuntu" /etc/os-release; then
        OS="ubuntu"
    elif grep -q "CentOS" /etc/os-release; then
        OS="centos"
    else
        log_error "不支持的操作系统，仅支持Ubuntu和CentOS"
        exit 1
    fi

    log_info "检测到操作系统: $OS"

    # 获取本机IP
    LOCAL_IP=$(hostname -I | awk '{print $1}')
    log_info "本机IP地址: $LOCAL_IP"
}

# 配置系统参数
configure_system() {
    log_info "开始配置系统参数..."

    # 关闭swap
    swapoff -a
    sed -i '/ swap / s/^\(.*\)$/#\1/g' /etc/fstab
    log_info "已关闭swap分区"

    # 关闭防火墙
    if systemctl is-active --quiet firewalld; then
        systemctl stop firewalld
        systemctl disable firewalld
        log_info "已关闭firewalld"
    fi

    # 关闭SELinux
    if command -v setenforce >/dev/null 2>&1; then
        setenforce 0
        sed -i 's/^SELINUX=enforcing/SELINUX=disabled/g' /etc/selinux/config
        log_info "已关闭SELinux"
    fi

    # 配置内核参数
    cat > /etc/sysctl.d/k8s.conf << EOF
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
net.ipv4.ip_forward = 1
vm.swappiness = 0
EOF

    sysctl --system > /dev/null 2>&1
    log_info "已加载内核参数"

    # 加载内核模块
    cat > /etc/modules-load.d/k8s.conf << EOF
br_netfilter
overlay
EOF

    modprobe br_netfilter
    modprobe overlay
    log_info "已加载内核模块"
}

# 安装Docker容器运行时
install_docker() {
    log_info "开始安装Docker..."

    if command -v docker >/dev/null 2>&1; then
        log_warn "Docker已安装，跳过安装步骤"
        return
    fi

    if [[ "$OS" == "ubuntu" ]]; then
        # Ubuntu安装Docker
        apt-get update
        apt-get install -y apt-transport-https ca-certificates curl software-properties-common
        curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
        add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
        apt-get update
        apt-get install -y docker-ce docker-ce-cli containerd.io

    elif [[ "$OS" == "centos" ]]; then
        # CentOS安装Docker
        yum install -y yum-utils device-mapper-persistent-data lvm2
        yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
        yum install -y docker-ce docker-ce-cli containerd.io
    fi

    # 配置Docker
    mkdir -p /etc/docker
    cat > /etc/docker/daemon.json << EOF
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://registry.docker-cn.com"
  ],
  "storage-driver": "overlay2",
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  }
}
EOF

    systemctl daemon-reload
    systemctl enable docker
    systemctl start docker
    log_info "Docker安装完成"
}

# 安装Kubernetes组件
install_k8s_components() {
    log_info "开始安装Kubernetes组件..."

    if [[ "$OS" == "ubuntu" ]]; then
        # Ubuntu安装kubeadm, kubelet, kubectl
        apt-get update && apt-get install -y apt-transport-https curl
        curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
        echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" > /etc/apt/sources.list.d/kubernetes.list
        apt-get update
        apt-get install -y kubelet=$K8S_VERSION-00 kubeadm=$K8S_VERSION-00 kubectl=$K8S_VERSION-00
        apt-mark hold kubelet kubeadm kubectl

    elif [[ "$OS" == "centos" ]]; then
        # CentOS安装kubeadm, kubelet, kubectl
        cat <<EOF > /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://mirrors.aliyun.com/kubernetes/yum/repos/kubernetes-el7-x86_64/
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://mirrors.aliyun.com/kubernetes/yum/doc/yum-key.gpg https://mirrors.aliyun.com/kubernetes/yum/doc/rpm-package-key.gpg
EOF

        yum makecache
        yum install -y kubelet-$K8S_VERSION kubeadm-$K8S_VERSION kubectl-$K8S_VERSION
        systemctl enable kubelet
    fi

    log_info "Kubernetes组件安装完成"
}

# 初始化Kubernetes集群
init_kubernetes_cluster() {
    log_info "开始初始化Kubernetes集群..."

    # 拉取Kubernetes镜像
    kubeadm config images pull --image-repository=registry.aliyuncs.com/google_containers

    # 初始化集群
    kubeadm init \
        --kubernetes-version=v$K8S_VERSION \
        --pod-network-cidr=$POD_NETWORK_CIDR \
        --service-cidr=$SERVICE_CIDR \
        --image-repository=registry.aliyuncs.com/google_containers \
        --ignore-preflight-errors=Swap

    # 配置kubectl
    mkdir -p $HOME/.kube
    cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
    chown $(id -u):$(id -g) $HOME/.kube/config

    # 单机模式：移除主节点的污点，允许调度Pod
    if [[ "$CLUSTER_TYPE" == "single" ]]; then
        kubectl taint nodes --all node-role.kubernetes.io/control-plane-
        log_info "已配置单机模式（主节点可调度Pod）"
    fi

    log_info "Kubernetes集群初始化完成"
}

# 安装网络插件
install_network_addon() {
    log_info "开始安装网络插件(Flannel)..."

    # 使用Flannel作为网络插件
    kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml

    # 等待网络插件就绪
    log_info "等待网络插件启动..."
    sleep 30
    kubectl wait --for=condition=ready pod -l app=flannel -n kube-system --timeout=300s
    log_info "网络插件安装完成"
}

# 验证集群状态
verify_cluster() {
    log_info "开始验证集群状态..."

    # 检查节点状态
    echo -e "\n${GREEN}=== 节点状态 ===${NC}"
    kubectl get nodes

    # 检查Pod状态
    echo -e "\n${GREEN}=== 系统Pod状态 ===${NC}"
    kubectl get pods -n kube-system

    # 检查集群信息
    echo -e "\n${GREEN}=== 集群信息 ===${NC}"
    kubectl cluster-info

    # 创建测试Pod验证网络
    log_info "创建测试Pod验证集群功能..."
    kubectl create deployment nginx-test --image=nginx:alpine
    kubectl expose deployment nginx-test --port=80 --type=NodePort

    sleep 20
    TEST_POD=$(kubectl get pods -l app=nginx-test -o jsonpath='{.items[0].metadata.name}')
    if kubectl get pod $TEST_POD | grep -q Running; then
        log_info "测试Pod运行正常"
    else
        log_warn "测试Pod可能存在问题，请检查"
    fi

    echo -e "\n${GREEN}=== 部署完成 ===${NC}"
    log_info "集群访问配置: $HOME/.kube/config"
    log_info "使用 'kubectl get nodes' 查看节点状态"
}

# 生成节点加入命令
generate_join_command() {
    if [[ "$CLUSTER_TYPE" == "multi" ]]; then
        JOIN_COMMAND=$(kubeadm token create --print-join-command)
        echo -e "\n${YELLOW}=== 工作节点加入命令 ===${NC}"
        echo "$JOIN_COMMAND"
        echo -e "\n在工作节点上执行以上命令加入集群"
    fi
}

# 主执行函数
main() {
    log_info "开始部署Kubernetes集群..."
    log_info "集群类型: $CLUSTER_TYPE"
    log_info "Kubernetes版本: $K8S_VERSION"

    check_privileges
    detect_environment
    configure_system
    install_docker
    install_k8s_components
    init_kubernetes_cluster
    install_network_addon
    verify_cluster
    generate_join_command

    log_info "Kubernetes集群部署完成！"
}

# 脚本执行入口
if [[ $# -gt 0 ]]; then
    case $1 in
        --single)
            CLUSTER_TYPE="single"
            ;;
        --multi)
            CLUSTER_TYPE="multi"
            if [[ -n "$2" ]]; then
                MASTER_IP=$2
            fi
            ;;
        --version)
            if [[ -n "$2" ]]; then
                K8S_VERSION=$2
            fi
            ;;
        *)
            echo "用法: $0 [--single|--multi [master_ip]] [--version version]"
            echo "示例:"
            echo "  $0 --single                    # 单机部署"
            echo "  $0 --multi 192.168.1.100       # 多机部署，指定主节点IP"
            echo "  $0 --version 1.35.0            # 指定Kubernetes版本"
            exit 1
            ;;
    esac
fi

main