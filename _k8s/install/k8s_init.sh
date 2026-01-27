#!/bin/bash

################################################################################
# Kubernetes 自动部署脚本 V2 (使用国内镜像源 + 包管理器)
# 支持: 单机/多机部署、版本自定义、完整错误处理
################################################################################

set -e

# ============================================================================
# 配置区域 - 可根据需要修改
# ============================================================================

# K8s 组件版本配置
K8S_VERSION="${K8S_VERSION:-1.28.2}"
CALICO_VERSION="${CALICO_VERSION:-3.26.3}"

# 安装方式选择: "binary" 或 "package"
INSTALL_METHOD="${INSTALL_METHOD:-package}"  # 优先使用包管理器

# 镜像仓库配置 (使用阿里云镜像)
IMAGE_REGISTRY="${IMAGE_REGISTRY:-registry.aliyuncs.com/google_containers}"
PAUSE_IMAGE="${PAUSE_IMAGE:-registry.aliyuncs.com/google_containers/pause:3.9}"

# 网络配置
POD_CIDR="${POD_CIDR:-10.244.0.0/16}"
SERVICE_CIDR="${SERVICE_CIDR:-10.96.0.0/12}"

# 节点类型配置
NODE_TYPE="${NODE_TYPE:-master}"
MASTER_IP="${MASTER_IP:-}"
JOIN_TOKEN="${JOIN_TOKEN:-}"
JOIN_CA_HASH="${JOIN_CA_HASH:-}"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ============================================================================
# 日志函数
# ============================================================================

log_info() {
    echo -e "${GREEN}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $(date '+%Y-%m-%d %H:%M:%S') - $1"
}

handle_error() {
    local exit_code=$?
    local line_number=$1
    log_error "脚本在第 ${line_number} 行执行失败,退出码: ${exit_code}"
    log_error "请检查上方错误信息"
    exit $exit_code
}

trap 'handle_error ${LINENO}' ERR

# ============================================================================
# 系统检查函数
# ============================================================================

check_system() {
    log_step "检查系统环境..."

    if [[ $EUID -ne 0 ]]; then
        log_error "此脚本必须以root用户运行"
        exit 1
    fi

    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
        OS_VERSION=$VERSION_ID
    else
        log_error "无法检测操作系统类型"
        exit 1
    fi

    log_info "操作系统: $OS $OS_VERSION"
    log_info "安装方式: $INSTALL_METHOD"

    local mem_total=$(free -m | awk '/^Mem:/{print $2}')
    local cpu_cores=$(nproc)

    log_info "CPU核心数: $cpu_cores, 内存: ${mem_total}MB"

    if [ "$NODE_TYPE" == "master" ]; then
        if [ $mem_total -lt 2048 ]; then
            log_warn "Master节点建议至少2GB内存"
        fi
    fi

    log_info "系统检查完成"
}

# ============================================================================
# 系统配置函数
# ============================================================================

configure_system() {
    log_step "配置系统环境..."

    # 关闭 swap
    log_info "关闭 swap..."
    swapoff -a
    sed -i '/swap/d' /etc/fstab

    # 关闭 SELinux
    if [ "$OS" == "centos" ] || [ "$OS" == "rhel" ]; then
        log_info "关闭 SELinux..."
        setenforce 0 2>/dev/null || true
        sed -i 's/^SELINUX=enforcing$/SELINUX=disabled/' /etc/selinux/config
    fi

    # 配置内核参数
    log_info "配置内核参数..."
    cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
vm.swappiness                       = 0
EOF

    modprobe br_netfilter 2>/dev/null || true
    modprobe overlay 2>/dev/null || true

    cat > /etc/modules-load.d/k8s.conf <<EOF
br_netfilter
overlay
EOF

    sysctl --system >/dev/null 2>&1

    log_info "系统配置完成"
}

# ============================================================================
# 安装容器运行时 (使用包管理器)
# ============================================================================

install_containerd_package() {
    log_step "使用包管理器安装 containerd..."

    if command -v containerd &>/dev/null; then
        log_info "containerd 已安装"
        return 0
    fi

    if [ "$OS" == "ubuntu" ] || [ "$OS" == "debian" ]; then
        log_info "安装依赖..."
        apt-get update
        apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release

        # 添加 Docker 官方 GPG 密钥
        mkdir -p /etc/apt/keyrings
        curl -fsSL https://download.docker.com/linux/$OS/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg 2>/dev/null || true

        # 添加 Docker 仓库
        echo \
          "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/$OS \
          $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

        apt-get update
        apt-get install -y containerd.io

    elif [ "$OS" == "centos" ] || [ "$OS" == "rhel" ]; then
        log_info "安装依赖..."
        yum install -y yum-utils

        # 添加 Docker 仓库
        yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

        yum install -y containerd.io
    fi

    # 配置 containerd
    mkdir -p /etc/containerd
    containerd config default | tee /etc/containerd/config.toml >/dev/null

    # 配置 systemd cgroup 驱动和国内镜像源
    log_info "配置 containerd..."
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
    sed -i "s#registry.k8s.io/pause:.*#${PAUSE_IMAGE}#" /etc/containerd/config.toml

    # 配置镜像加速
    sed -i '/\[plugins\."io\.containerd\.grpc\.v1\.cri"\.registry\.mirrors\]/a\        [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]\n          endpoint = ["https://docker.mirrors.ustc.edu.cn"]\n        [plugins."io.containerd.grpc.v1.cri".registry.mirrors."registry.k8s.io"]\n          endpoint = ["https://registry.aliyuncs.com/google_containers"]' /etc/containerd/config.toml

    # 启动 containerd
    log_info "启动 containerd..."
    systemctl daemon-reload
    systemctl enable containerd
    systemctl restart containerd

    if ! systemctl is-active --quiet containerd; then
        log_error "containerd 启动失败"
        systemctl status containerd --no-pager
        exit 1
    fi

    log_info "containerd 安装完成"
}

# ============================================================================
# 安装 Kubernetes 组件
# ============================================================================

install_k8s_components() {
    log_step "安装 Kubernetes 组件 (版本: ${K8S_VERSION})..."

    if [ "$OS" == "ubuntu" ] || [ "$OS" == "debian" ]; then
        install_k8s_apt
    elif [ "$OS" == "centos" ] || [ "$OS" == "rhel" ]; then
        install_k8s_yum
    else
        log_error "不支持的操作系统: $OS"
        exit 1
    fi
}

install_k8s_apt() {
    log_info "使用 APT 安装 Kubernetes..."

    apt-get update
    apt-get install -y apt-transport-https ca-certificates curl

    # 添加 Kubernetes 官方源 (使用阿里云镜像)
    curl -fsSL https://mirrors.aliyun.com/kubernetes/apt/doc/apt-key.gpg | apt-key add - 2>/dev/null || {
        log_warn "添加密钥失败，尝试备用方法..."
        curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
    }

    cat > /etc/apt/sources.list.d/kubernetes.list <<EOF
deb https://mirrors.aliyun.com/kubernetes/apt/ kubernetes-xenial main
EOF

    apt-get update || {
        log_warn "更新失败，尝试使用官方源..."
        cat > /etc/apt/sources.list.d/kubernetes.list <<EOF
deb https://apt.kubernetes.io/ kubernetes-xenial main
EOF
        apt-get update
    }

    # 安装指定版本
    local k8s_version_apt="${K8S_VERSION}-00"
    log_info "安装 kubelet=${k8s_version_apt} kubeadm=${k8s_version_apt} kubectl=${k8s_version_apt}"

    apt-get install -y kubelet=${k8s_version_apt} kubeadm=${k8s_version_apt} kubectl=${k8s_version_apt} || {
        log_warn "指定版本安装失败，尝试安装最新版本..."
        apt-get install -y kubelet kubeadm kubectl
    }

    apt-mark hold kubelet kubeadm kubectl
    systemctl enable kubelet
}

install_k8s_yum() {
    log_info "使用 YUM 安装 Kubernetes..."

    # 添加 Kubernetes 源
    cat > /etc/yum.repos.d/kubernetes.repo <<EOF
[kubernetes]
name=Kubernetes
baseurl=https://mirrors.aliyun.com/kubernetes/yum/repos/kubernetes-el7-x86_64/
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://mirrors.aliyun.com/kubernetes/yum/doc/yum-key.gpg https://mirrors.aliyun.com/kubernetes/yum/doc/rpm-package-key.gpg
EOF

    # 安装指定版本
    local k8s_version_yum="${K8S_VERSION}-0"
    log_info "安装 kubelet-${k8s_version_yum} kubeadm-${k8s_version_yum} kubectl-${k8s_version_yum}"

    yum install -y kubelet-${k8s_version_yum} kubeadm-${k8s_version_yum} kubectl-${k8s_version_yum} || {
        log_warn "指定版本安装失败，尝试安装最新版本..."
        yum install -y kubelet kubeadm kubectl
    }

    systemctl enable kubelet
}

# ============================================================================
# 预拉取镜像
# ============================================================================

pull_images() {
    log_step "预拉取 Kubernetes 镜像..."

    log_info "获取所需镜像列表..."
    local images=$(kubeadm config images list --kubernetes-version=${K8S_VERSION} 2>/dev/null)

    if [ -z "$images" ]; then
        log_warn "无法获取镜像列表，跳过预拉取"
        return 0
    fi

    log_info "将从以下镜像仓库拉取: $IMAGE_REGISTRY"

    while IFS= read -r image; do
        local image_name=$(echo $image | awk -F'/' '{print $NF}')
        local local_image="${IMAGE_REGISTRY}/${image_name}"

        log_info "拉取镜像: $local_image"

        if ctr -n k8s.io image pull "$local_image" 2>&1 | grep -q "unpacking\|exists"; then
            ctr -n k8s.io image tag "$local_image" "$image" >/dev/null 2>&1 || true
            log_info "✓ 镜像拉取成功: $image_name"
        else
            log_warn "镜像拉取失败: $local_image"
        fi
    done <<< "$images"

    log_info "镜像拉取完成"
}

# ============================================================================
# 初始化 Master 节点
# ============================================================================

init_master() {
    log_step "初始化 Master 节点..."

    local node_ip=$(hostname -I | awk '{print $1}')
    log_info "节点IP: $node_ip"

    # 创建 kubeadm 配置文件
    cat > /tmp/kubeadm-config.yaml <<EOF
apiVersion: kubeadm.k8s.io/v1beta3
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: ${node_ip}
  bindPort: 6443
nodeRegistration:
  criSocket: unix:///run/containerd/containerd.sock
  imagePullPolicy: IfNotPresent
  kubeletExtraArgs:
    pod-infra-container-image: ${PAUSE_IMAGE}
---
apiVersion: kubeadm.k8s.io/v1beta3
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
imageRepository: ${IMAGE_REGISTRY}
networking:
  podSubnet: ${POD_CIDR}
  serviceSubnet: ${SERVICE_CIDR}
---
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
cgroupDriver: systemd
EOF

    log_info "执行 kubeadm init..."
    if ! kubeadm init --config=/tmp/kubeadm-config.yaml --upload-certs 2>&1 | tee /tmp/kubeadm-init.log; then
        log_error "Kubernetes 初始化失败"
        log_error "详细日志已保存到: /tmp/kubeadm-init.log"
        log_error "常见问题排查:"
        log_error "  1. 检查 6443 端口: netstat -tlnp | grep 6443"
        log_error "  2. 检查 containerd: systemctl status containerd"
        log_error "  3. 查看日志: journalctl -xeu kubelet"
        exit 1
    fi

    # 配置 kubectl
    log_info "配置 kubectl..."
    mkdir -p $HOME/.kube
    cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
    chown $(id -u):$(id -g) $HOME/.kube/config

    # 等待API Server就绪
    log_info "等待 API Server 就绪..."
    local retry=0
    local max_retry=30
    while [ $retry -lt $max_retry ]; do
        if kubectl get nodes &>/dev/null; then
            log_info "✓ API Server 已就绪"
            break
        fi
        retry=$((retry + 1))
        if [ $retry -eq $max_retry ]; then
            log_error "API Server 启动超时"
            exit 1
        fi
        echo -n "."
        sleep 2
    done
    echo ""

    # 安装网络插件
    install_calico

    # 输出 join 命令
    log_info "生成 Worker 节点加入命令..."
    local join_cmd=$(kubeadm token create --print-join-command)

    echo ""
    log_info "=================================="
    log_info "Master 节点初始化完成!"
    log_info "=================================="
    echo ""
    log_info "Worker 节点加入命令:"
    echo -e "${GREEN}${join_cmd}${NC}"
    echo ""

    echo "$join_cmd" > /root/k8s-join-command.sh
    chmod +x /root/k8s-join-command.sh
    log_info "Join 命令已保存到: /root/k8s-join-command.sh"
}

# ============================================================================
# 安装 Calico
# ============================================================================

install_calico() {
    log_step "安装 Calico 网络插件..."

    local calico_manifest="https://raw.githubusercontent.com/projectcalico/calico/v${CALICO_VERSION}/manifests/calico.yaml"

    log_info "下载 Calico manifest..."
    if wget -q --timeout=30 -O /tmp/calico.yaml "$calico_manifest" 2>/dev/null; then
        sed -i "s|192.168.0.0/16|${POD_CIDR}|g" /tmp/calico.yaml
        kubectl apply -f /tmp/calico.yaml
    else
        log_warn "下载失败，尝试备用方法..."
        kubectl apply -f https://docs.projectcalico.org/manifests/calico.yaml || {
            log_error "Calico 安装失败"
            exit 1
        }
    fi

    log_info "等待 Calico Pod 就绪..."
    sleep 10

    log_info "Calico 安装完成"
}

# ============================================================================
# 加入 Worker 节点
# ============================================================================

join_worker() {
    log_step "加入 Worker 节点到集群..."

    if [ -z "$MASTER_IP" ]; then
        log_error "Worker 节点必须指定 MASTER_IP"
        exit 1
    fi

    log_info "Master 节点 IP: $MASTER_IP"

    # 检查连接
    if ! ping -c 3 "$MASTER_IP" &>/dev/null; then
        log_error "无法连接到 Master 节点"
        exit 1
    fi

    # 构建 join 命令
    if [ -n "$JOIN_TOKEN" ] && [ -n "$JOIN_CA_HASH" ]; then
        local join_cmd="kubeadm join ${MASTER_IP}:6443 --token ${JOIN_TOKEN} --discovery-token-ca-cert-hash ${JOIN_CA_HASH}"
    else
        log_error "必须指定 JOIN_TOKEN 和 JOIN_CA_HASH"
        exit 1
    fi

    log_info "执行加入命令..."
    if ! $join_cmd; then
        log_error "加入集群失败"
        exit 1
    fi

    log_info "Worker 节点加入成功!"
}

# ============================================================================
# 验证集群
# ============================================================================

verify_cluster() {
    log_step "验证集群状态..."

    if [ "$NODE_TYPE" == "master" ]; then
        sleep 5
        log_info "节点状态:"
        kubectl get nodes -o wide || true

        echo ""
        log_info "系统 Pod 状态:"
        kubectl get pods -n kube-system || true
    fi
}

# ============================================================================
# 主函数
# ============================================================================

main() {
    echo "========================================================================"
    echo "  Kubernetes 自动部署脚本 V2"
    echo "  版本: ${K8S_VERSION}"
    echo "  节点类型: ${NODE_TYPE}"
    echo "  安装方式: ${INSTALL_METHOD}"
    echo "========================================================================"
    echo ""

    check_system
    configure_system
    install_containerd_package
    install_k8s_components

    if [ "$NODE_TYPE" == "master" ]; then
        pull_images
        init_master
        verify_cluster
    elif [ "$NODE_TYPE" == "worker" ]; then
        pull_images
        join_worker
    else
        log_error "无效的节点类型: $NODE_TYPE"
        exit 1
    fi

    echo ""
    echo "========================================================================"
    log_info "🎉 Kubernetes 部署完成!"
    echo "========================================================================"
}

main "$@"