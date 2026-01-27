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
BLUE='\033[0;34m'
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

log_debug() {
    echo -e "${BLUE}[DEBUG]${NC} $1"
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

# 配置参数 - 更新到最新稳定版本
K8S_VERSION="1.32.11"
CONTAINERD_VERSION="1.7.22"
CNI_VERSION="1.5.1"
CALICO_VERSION="v3.29.1"

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
net.ipv4.conf.all.forwarding        = 1
net.ipv6.conf.all.forwarding        = 1
EOF

    # 加载内核模块
    modprobe br_netfilter
    modprobe overlay

    cat > /etc/modules-load.d/k8s.conf <<EOF
br_netfilter
overlay
EOF

    sysctl --system > /dev/null 2>&1

    # 配置时区
    timedatectl set-timezone Asia/Shanghai 2>/dev/null || true

    # 同步时间
    log_info "同步系统时间..."
    apt-get install -y ntpdate 2>/dev/null || true
    ntpdate -u ntp.aliyun.com 2>/dev/null || true

    log_info "系统初始化完成"
}

# 配置国内镜像源
configure_apt_mirror() {
    log_info "配置 APT 国内镜像源..."

    # 备份原有源
    cp /etc/apt/sources.list /etc/apt/sources.list.bak.$(date +%Y%m%d%H%M%S) 2>/dev/null || true

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
        jq \
        || error_exit "依赖包安装失败"

    log_info "依赖包安装完成"
}

# 安装 containerd
install_containerd() {
    log_info "安装 containerd..."

    # 检查是否已安装
    if systemctl is-active --quiet containerd; then
        log_warn "containerd 已安装并运行,跳过..."
        return
    fi

    # 卸载旧版本
    apt-get remove -y docker docker-engine docker.io containerd runc 2>/dev/null || true

    # 安装 containerd
    apt-get install -y containerd || error_exit "containerd 安装失败"

    # 创建配置目录
    mkdir -p /etc/containerd

    # 生成默认配置
    containerd config default > /etc/containerd/config.toml

    # 配置 systemd cgroup 驱动
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

    # 配置国内镜像加速 - 更全面的配置
    cat > /etc/containerd/config.toml <<EOF
version = 2
[plugins."io.containerd.grpc.v1.cri"]
  [plugins."io.containerd.grpc.v1.cri".containerd]
    [plugins."io.containerd.grpc.v1.cri".containerd.runtimes]
      [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runc]
        runtime_type = "io.containerd.runc.v2"
        [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runc.options]
          SystemdCgroup = true
  [plugins."io.containerd.grpc.v1.cri".registry]
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors]
      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
        endpoint = ["https://docker.mirrors.ustc.edu.cn", "https://hub-mirror.c.163.com"]
      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."k8s.gcr.io"]
        endpoint = ["https://registry.cn-hangzhou.aliyuncs.com/google_containers"]
      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."registry.k8s.io"]
        endpoint = ["https://registry.cn-hangzhou.aliyuncs.com/google_containers"]
      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."quay.io"]
        endpoint = ["https://quay.mirrors.ustc.edu.cn"]
[plugins."io.containerd.grpc.v1.cri".cni]
  bin_dir = "/opt/cni/bin"
  conf_dir = "/etc/cni/net.d"
EOF

    # 启动 containerd
    systemctl daemon-reload
    systemctl enable containerd
    systemctl restart containerd

    # 等待 containerd 启动
    sleep 3

    # 验证安装
    if ! systemctl is-active --quiet containerd; then
        error_exit "containerd 启动失败"
    fi

    log_info "containerd 安装完成"
}

# 安装 crictl (容器运行时命令行工具)
install_crictl() {
    log_info "安装 crictl..."

    if command -v crictl &> /dev/null; then
        log_warn "crictl 已安装,跳过..."
        return
    fi

    CRICTL_VERSION="v1.31.1"
    wget -q https://github.com/kubernetes-sigs/cri-tools/releases/download/${CRICTL_VERSION}/crictl-${CRICTL_VERSION}-linux-amd64.tar.gz \
        -O /tmp/crictl.tar.gz || log_warn "crictl 下载失败,跳过..."

    if [[ -f /tmp/crictl.tar.gz ]]; then
        tar -zxf /tmp/crictl.tar.gz -C /usr/local/bin/
        rm -f /tmp/crictl.tar.gz

        # 配置 crictl
        cat > /etc/crictl.yaml <<EOF
runtime-endpoint: unix:///run/containerd/containerd.sock
image-endpoint: unix:///run/containerd/containerd.sock
timeout: 10
EOF
        log_info "crictl 安装完成"
    fi
}

# 安装 kubeadm、kubelet、kubectl
install_kubernetes() {
    log_info "安装 Kubernetes 组件..."

    # 添加阿里云 Kubernetes 源
    curl -fsSL https://mirrors.aliyun.com/kubernetes-new/core/stable/v1.31/deb/Release.key | \
        gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg || \
        error_exit "添加 GPG 密钥失败"

    echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://mirrors.aliyun.com/kubernetes-new/core/stable/v1.31/deb/ /" | \
        tee /etc/apt/sources.list.d/kubernetes.list

    # 更新软件包列表
    apt-get update -y || error_exit "更新软件包列表失败"

    # 查看可用版本
    log_debug "可用的 Kubernetes 版本:"
    apt-cache madison kubeadm | head -5

    # 安装 Kubernetes 组件
    KUBE_VERSION="${K8S_VERSION}-1.1"

    # 检查版本是否可用
    if apt-cache madison kubeadm | grep -q "$KUBE_VERSION"; then
        log_info "安装 Kubernetes $KUBE_VERSION"
        apt-get install -y kubelet=$KUBE_VERSION kubeadm=$KUBE_VERSION kubectl=$KUBE_VERSION || \
            error_exit "Kubernetes 组件安装失败"
    else
        log_warn "指定版本 $KUBE_VERSION 不可用,安装最新稳定版本"
        apt-get install -y kubelet kubeadm kubectl || error_exit "Kubernetes 组件安装失败"
    fi

    # 锁定版本
    apt-mark hold kubelet kubeadm kubectl

    # 启动 kubelet
    systemctl enable kubelet

    log_info "Kubernetes 组件安装完成"
    kubectl version --client
}

# 预拉取镜像
prefetch_images() {
    log_info "预拉取 Kubernetes 镜像..."

    # 使用 kubeadm 配置文件
    cat > /tmp/kubeadm-config.yaml <<EOF
apiVersion: kubeadm.k8s.io/v1beta3
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
imageRepository: ${ALIYUN_MIRROR}
networking:
  podSubnet: 10.244.0.0/16
  serviceSubnet: 10.96.0.0/12
---
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
cgroupDriver: systemd
EOF

    # 拉取镜像,失败不退出
    kubeadm config images pull --config=/tmp/kubeadm-config.yaml || log_warn "部分镜像拉取失败,将在初始化时自动拉取"

    log_info "镜像预拉取完成"
}

# 初始化 Master 节点
init_master() {
    log_info "=========================================="
    log_info "初始化 Kubernetes Master 节点"
    log_info "=========================================="
    echo ""

    # 创建 kubeadm 配置文件
    cat > /tmp/kubeadm-config.yaml <<EOF
apiVersion: kubeadm.k8s.io/v1beta3
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
imageRepository: ${ALIYUN_MIRROR}
networking:
  podSubnet: 10.244.0.0/16
  serviceSubnet: 10.96.0.0/12
---
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
cgroupDriver: systemd
serverTLSBootstrap: true
EOF

    # 初始化集群
    log_info "执行集群初始化(这可能需要几分钟)..."
    kubeadm init --config=/tmp/kubeadm-config.yaml --upload-certs || error_exit "集群初始化失败"

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
    log_info "=========================================="
    log_info "安装 Calico 网络插件"
    log_info "=========================================="
    echo ""

    # 下载 Calico operator
    log_info "下载 Calico operator..."
    kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VERSION}/manifests/tigera-operator.yaml || \
        error_exit "Calico operator 安装失败"

    # 等待 operator 就绪
    log_info "等待 Calico operator 就绪..."
    sleep 10

    # 创建自定义资源
    log_info "创建 Calico 自定义资源..."
    cat > /tmp/calico-custom-resources.yaml <<EOF
apiVersion: operator.tigera.io/v1
kind: Installation
metadata:
  name: default
spec:
  calicoNetwork:
    ipPools:
    - blockSize: 26
      cidr: 10.244.0.0/16
      encapsulation: IPIP
      natOutgoing: Enabled
      nodeSelector: all()
  registry: quay.io/
---
apiVersion: operator.tigera.io/v1
kind: APIServer
metadata:
  name: default
spec: {}
EOF

    kubectl create -f /tmp/calico-custom-resources.yaml || error_exit "Calico 自定义资源创建失败"

    log_info "Calico 网络插件安装完成,等待 Pod 启动..."
}

# 等待所有 Pod 就绪
wait_for_pods_ready() {
    local namespace=$1
    local timeout=${2:-300}  # 默认超时5分钟
    local interval=10
    local elapsed=0

    log_info "等待 namespace: $namespace 中的所有 Pod 就绪..."

    while [[ $elapsed -lt $timeout ]]; do
        # 获取 Pod 状态
        local total_pods=$(kubectl get pods -n $namespace --no-headers 2>/dev/null | wc -l)
        local running_pods=$(kubectl get pods -n $namespace --no-headers 2>/dev/null | grep -c "Running" || echo 0)
        local ready_pods=$(kubectl get pods -n $namespace --no-headers 2>/dev/null | awk '{print $2}' | grep -c "^[0-9]*/[0-9]*$" | xargs -I {} sh -c 'kubectl get pods -n '$namespace' --no-headers | awk "{if (\$2 ~ /^[0-9]+\/[0-9]+$/) {split(\$2, a, \"/\"); if (a[1] == a[2]) print \$1}}" | wc -l' || echo 0)

        # 获取未就绪的 Pod
        local not_ready=$(kubectl get pods -n $namespace --no-headers 2>/dev/null | grep -v "Running\|Completed" | awk '{print $1}' || echo "")

        if [[ $total_pods -gt 0 ]]; then
            log_info "进度: $running_pods/$total_pods Pods Running (已等待 ${elapsed}s)"

            if [[ -n "$not_ready" ]]; then
                log_warn "未就绪的 Pods:"
                kubectl get pods -n $namespace | grep -v "Running\|Completed" || true
            fi

            # 检查是否所有 Pod 都在运行且就绪
            local all_running=$(kubectl get pods -n $namespace --no-headers 2>/dev/null | grep -v "Running\|Completed" | wc -l)

            if [[ $all_running -eq 0 ]]; then
                log_info "所有 Pod 已就绪!"
                return 0
            fi
        else
            log_warn "namespace $namespace 中暂无 Pod"
        fi

        sleep $interval
        elapsed=$((elapsed + interval))
    done

    log_error "超时: namespace $namespace 中的 Pod 未能在 ${timeout}s 内就绪"
    kubectl get pods -n $namespace
    return 1
}

# 检查节点就绪状态
wait_for_node_ready() {
    local timeout=300
    local interval=10
    local elapsed=0

    log_info "等待节点就绪..."

    while [[ $elapsed -lt $timeout ]]; do
        local ready_nodes=$(kubectl get nodes --no-headers 2>/dev/null | grep -c " Ready " || echo 0)
        local total_nodes=$(kubectl get nodes --no-headers 2>/dev/null | wc -l)

        if [[ $ready_nodes -gt 0 ]] && [[ $ready_nodes -eq $total_nodes ]]; then
            log_info "所有节点已就绪!"
            kubectl get nodes
            return 0
        fi

        log_info "等待节点就绪: $ready_nodes/$total_nodes (已等待 ${elapsed}s)"
        sleep $interval
        elapsed=$((elapsed + interval))
    done

    log_error "超时: 节点未能在 ${timeout}s 内就绪"
    kubectl get nodes
    return 1
}

# 单机模式:允许 Master 调度 Pod
enable_master_scheduling() {
    log_info "配置单机模式:允许 Master 节点调度 Pod..."

    # 等待节点注册
    sleep 10

    # 去除 Master 节点的污点
    kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true
    kubectl taint nodes --all node-role.kubernetes.io/master- 2>/dev/null || true

    log_info "单机模式配置完成"
}

# 验证集群健康状态
verify_cluster_health() {
    log_info "=========================================="
    log_info "验证集群健康状态"
    log_info "=========================================="
    echo ""

    # 检查节点
    log_info "检查节点状态..."
    if ! wait_for_node_ready; then
        log_error "节点未就绪"
        return 1
    fi

    # 检查系统 Pods
    log_info "检查 kube-system namespace..."
    if ! wait_for_pods_ready "kube-system" 300; then
        log_error "kube-system pods 未就绪"
        return 1
    fi

    # 检查 Calico Pods
    log_info "检查 calico-system namespace..."
    kubectl get namespace calico-system &>/dev/null || sleep 30

    if kubectl get namespace calico-system &>/dev/null; then
        if ! wait_for_pods_ready "calico-system" 300; then
            log_error "calico-system pods 未就绪"
            return 1
        fi
    fi

    # 检查 Calico API Server
    log_info "检查 calico-apiserver namespace..."
    kubectl get namespace calico-apiserver &>/dev/null || sleep 20

    if kubectl get namespace calico-apiserver &>/dev/null; then
        if ! wait_for_pods_ready "calico-apiserver" 300; then
            log_warn "calico-apiserver pods 未完全就绪(这通常不影响集群功能)"
        fi
    fi

    # 检查 tigera-operator
    log_info "检查 tigera-operator namespace..."
    if kubectl get namespace tigera-operator &>/dev/null; then
        if ! wait_for_pods_ready "tigera-operator" 300; then
            log_warn "tigera-operator pods 未完全就绪"
        fi
    fi

    log_info "集群健康检查完成!"
    return 0
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
    log_info "=========================================="
    log_info "集群部署完成!"
    log_info "=========================================="
    echo ""

    log_info "集群版本信息:"
    kubectl version --short 2>/dev/null || kubectl version
    echo ""

    log_info "集群节点信息:"
    kubectl get nodes -o wide
    echo ""

    log_info "所有命名空间的 Pod 状态:"
    kubectl get pods -A -o wide
    echo ""

    log_info "常用命令:"
    echo "  查看节点:       kubectl get nodes"
    echo "  查看所有Pod:    kubectl get pods -A"
    echo "  查看服务:       kubectl get svc -A"
    echo "  查看组件状态:   kubectl get cs"
    echo "  查看集群信息:   kubectl cluster-info"
    echo ""

    log_info "配置文件位置:"
    echo "  kubectl配置:    ~/.kube/config"
    echo "  kubeadm配置:    /tmp/kubeadm-config.yaml"
    echo ""
}

# 主菜单
main_menu() {
    clear
    echo "=========================================="
    echo "   Kubernetes 集群部署脚本 v2.0"
    echo "=========================================="
    echo ""
    echo "版本信息:"
    echo "  Kubernetes:  v${K8S_VERSION}"
    echo "  Containerd:  v${CONTAINERD_VERSION}"
    echo "  Calico:      ${CALICO_VERSION}"
    echo "  CNI:         v${CNI_VERSION}"
    echo ""
    echo "请选择部署模式:"
    echo "  1) 单机模式 (Single Node)"
    echo "  2) 多机模式 - Master 节点"
    echo "  3) 多机模式 - Worker 节点"
    echo "  4) 仅安装基础组件(不初始化集群)"
    echo "  5) 验证集群健康状态"
    echo "  0) 退出"
    echo ""
    read -p "请输入选项 [0-5]: " choice

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
        5)
            verify_cluster_health
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
    install_crictl
    install_kubernetes
    prefetch_images
    init_master
    install_calico
    enable_master_scheduling

    # 等待并验证集群健康
    if verify_cluster_health; then
        show_cluster_info
        log_info "=========================================="
        log_info "单机模式部署成功!"
        log_info "=========================================="
    else
        log_error "=========================================="
        log_error "集群部署完成但存在问题,请检查上述错误"
        log_error "=========================================="
        show_cluster_info
    fi
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
    install_crictl
    install_kubernetes
    prefetch_images
    init_master
    install_calico

    # 等待并验证集群健康
    if verify_cluster_health; then
        generate_join_command
        show_cluster_info
        log_info "=========================================="
        log_info "Master 节点部署成功!"
        log_info "请在 Worker 节点上运行加入命令"
        log_info "=========================================="
    else
        log_error "=========================================="
        log_error "Master 节点部署完成但存在问题,请检查上述错误"
        log_error "=========================================="
        generate_join_command
        show_cluster_info
    fi
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
    install_crictl
    install_kubernetes

    echo ""
    log_info "=========================================="
    log_info "Worker 节点基础组件安装完成!"
    log_info "=========================================="
    echo ""
    log_warn "请在 Master 节点执行以下命令获取加入命令:"
    echo "  kubeadm token create --print-join-command"
    echo ""
    log_warn "然后在本节点以 root 权限执行该命令加入集群"
    echo ""
    log_info "示例:"
    echo "  sudo kubeadm join 192.168.1.100:6443 --token xxxxx \\"
    echo "    --discovery-token-ca-cert-hash sha256:xxxxx"
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
    install_crictl
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
        log_warn "按 Ctrl+C 退出,或按任意键继续..."
        read -n 1 -s
    fi

    main_menu
}

# 执行主函数
main