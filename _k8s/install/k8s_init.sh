#!/bin/bash

#################################################
# Kubernetes 集群自动化部署脚本
# 模式: 单机(Single) / 多机(Multi)
#################################################

set -e

# 版本配置参数
K8S_VERSION="1.33.7"
#CONTAINERD_VERSION="1.7.30"
CALICO_VERSION="v3.31.3"
COREDNS_VERSION="v1.13.2"

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

    log_info "系统版本: Ubuntu $VERSION_ID"
}


# 系统初始化
system_init() {
    log_info "开始系统初始化..."

    # 关闭swap
    log_info "关闭 swap 交换分区..."
    swapoff -a
    sed -i '/swap/s/^/#/' /etc/fstab

    # 关闭防火墙
    log_info "配置网络防火墙..."
    systemctl stop ufw 2>/dev/null || true
    systemctl disable ufw 2>/dev/null || true

    # 关闭SELinux (Ubuntu通常不启用)
    if command -v getenforce &> /dev/null; then
        setenforce 0 2>/dev/null || true
        sed -i 's/^SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config 2>/dev/null || true
    fi

    # 配置内核参数
    log_info "配置系统内核参数..."
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
    log_info "配置 apt 安装包国内镜像源..."

    # 备份原有源
    cp /etc/apt/sources.list /etc/apt/sources.list.bak.$(date +%Y%m%d%H%M%S) 2>/dev/null || true

    # 使用阿里云镜像源
    cat > /etc/apt/sources.list <<EOF
deb http://mirrors.aliyun.com/ubuntu/ $(lsb_release -cs) main restricted universe multiverse
deb http://mirrors.aliyun.com/ubuntu/ $(lsb_release -cs)-security main restricted universe multiverse
deb http://mirrors.aliyun.com/ubuntu/ $(lsb_release -cs)-updates main restricted universe multiverse
deb http://mirrors.aliyun.com/ubuntu/ $(lsb_release -cs)-backports main restricted universe multiverse
EOF

    # 更新系统软件包列表
    log_info "更新系统软件包列表..."
    apt-get update -y || error_exit "apt 更新失败"
}

get_private_ip() {
    # 获取内网IP（私有IP）
    local private_ip=$(ip route get 1.1.1.1 2>/dev/null | awk '{print $7; exit}')

    if [[ -z "$private_ip" ]]; then
        private_ip=$(ip -4 addr show | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | grep -v '127.0.0.1' | head -1)
    fi

    echo "$private_ip"
}

get_public_ip() {
    # 获取公网IP（外网IP）
    local public_ip=""

    # 尝试多个服务
    public_ip=$(curl -s --connect-timeout 3 https://api.ipify.org 2>/dev/null)

    if [[ -z "$public_ip" ]]; then
        public_ip=$(curl -s --connect-timeout 3 https://ifconfig.me 2>/dev/null)
    fi

    if [[ -z "$public_ip" ]]; then
        public_ip=$(curl -s --connect-timeout 3 https://ip.sb 2>/dev/null)
    fi

    echo "$public_ip"
}

# 安装依赖包
install_dependencies() {
    log_info "安装K8s依赖包..."

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
        || error_exit "K8s依赖包安装失败"

    log_info "K8s依赖包安装完成"
}

# 安装 containerd
install_containerd() {
    echo ""
    log_info "安装 containerd 容器..."

    # 检查是否已安装
    if command -v containerd &> /dev/null; then
        containerd --version
        log_warn "容器 containerd 已安装,跳过..."
        return
    fi

    # 安装 containerd
    apt-get install -y containerd || error_exit "容器 containerd 安装失败"

    # 创建配置目录
    mkdir -p /etc/containerd

    # 生成默认配置
    containerd config default > /etc/containerd/config.toml

    # 配置 systemd cgroup 驱动
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

    # 配置多个国内镜像加速源
    log_info "配置 containerd 容器镜像国内加速源..."

    # 备份原配置
    cp /etc/containerd/config.toml /etc/containerd/config.toml.bak

    # 使用更可靠的配置方式
    cat > /etc/containerd/config.toml <<EOF
version = 2
[plugins."io.containerd.grpc.v1.cri"]
  sandbox_image = "registry.cn-hangzhou.aliyuncs.com/google_containers/pause:3.10.1"

  [plugins."io.containerd.grpc.v1.cri".containerd]
    [plugins."io.containerd.grpc.v1.cri".containerd.runtimes]
      [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runc]
        runtime_type = "io.containerd.runc.v2"
        [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runc.options]
          SystemdCgroup = true

  [plugins."io.containerd.grpc.v1.cri".registry]
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors]

      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
        endpoint = [
          "https://docker.m.daocloud.io",
          "https://docker.1ms.run",
          "https://docker.xuanyuan.me",
          "https://docker.lanneng.tech",
          "https://em1sutsj.mirror.aliyuncs.com"
          ]

      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."registry.k8s.io"]
        endpoint = [
          "https://registry.cn-hangzhou.aliyuncs.com/google_containers"
        ]

      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."k8s.gcr.io"]
        endpoint = [
          "https://registry.cn-hangzhou.aliyuncs.com/google_containers"
        ]

      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."gcr.io"]
        endpoint = [
          "https://registry.cn-hangzhou.aliyuncs.com"
        ]

      [plugins."io.containerd.grpc.v1.cri".registry.mirrors."quay.io"]
        endpoint = [
          "https://quay.mirrors.aliyuncs.com"
        ]
EOF

    # 启动 containerd
    systemctl daemon-reload
    systemctl enable containerd
    systemctl restart containerd

    # 验证安装
    if ! systemctl is-active --quiet containerd; then
        error_exit "容器 containerd 启动失败"
    fi

    log_info "容器 containerd 安装完成, 版本信息:"
    # 打印版本
    containerd --version
    echo ""
}

# 安装 crictl 工具（用于调试）
install_crictl() {
   log_info "crictl 调试工具在github下载速度慢 暂时跳过安装"
#    CRICTL_VERSION="v1.35.0"
#    log_info "安装 crictl $CRICTL_VERSION 工具..." +
#    wget -q https://github.com/kubernetes-sigs/cri-tools/releases/download/${CRICTL_VERSION}/crictl-${CRICTL_VERSION}-linux-amd64.tar.gz -O /tmp/crictl.tar.gz || {
#        log_warn "crictl 下载失败，跳过..."
#        return
#    }
#
#    tar zxf /tmp/crictl.tar.gz -C /usr/local/bin
#    rm -f /tmp/crictl.tar.gz
#
#    # 配置 crictl
#    cat > /etc/crictl.yaml <<EOF
#runtime-endpoint: unix:///run/containerd/containerd.sock
#image-endpoint: unix:///run/containerd/containerd.sock
#timeout: 10
#EOF
#
#    log_info "crictl 安装完成"
}

# 安装 kubeadm、kubectl 、kubelet
install_kubernetes() {
    echo  ""
    log_info "安装 Kubernetes ${K8S_VERSION} 组件(如 kubeadm、kubectl、kubelet)..."
    k8s_main_version=$(echo $K8S_VERSION | cut -d. -f1-2)
    # 添加阿里云 Kubernetes 源
    curl -fsSL https://mirrors.aliyun.com/kubernetes-new/core/stable/v${k8s_main_version}/deb/Release.key | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg || error_exit "添加 GPG 密钥失败"

    cat > /etc/apt/sources.list.d/kubernetes.list <<EOF
deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://mirrors.aliyun.com/kubernetes-new/core/stable/v${k8s_main_version}/deb/ /
EOF

    # 更新软件包列表
    apt-get update -y || error_exit "更新软件包列表失败"

    # 安装指定版本
    KUBE_VERSION="${K8S_VERSION}-1.1" # 版本后缀不同  新版 1.1，旧版 00 或 "0" "1" "2 等

    # 检查版本是否可用
    if ! apt-cache madison kubeadm | grep -q "$KUBE_VERSION"; then
        log_warn "指定版本 $KUBE_VERSION 不可用,尝试安装最新版本"
        apt-get install -y kubelet kubeadm kubectl || error_exit "Kubernetes 组件安装失败"
    else
        apt-get install -y kubelet=$KUBE_VERSION kubeadm=$KUBE_VERSION kubectl=$KUBE_VERSION || error_exit "Kubernetes 组件安装失败"
    fi

    apt-mark hold kubelet kubeadm kubectl

    # 启动 kubelet
    systemctl enable kubelet

    log_info "Kubernetes $(kubeadm version -o short) 组件安装完成"
    echo  ""
}

# 预拉取镜像（使用国内源）
prefetch_images() {
    log_info "预拉取 Kubernetes 核心镜像..."

    # 获取所需镜像列表
    kubeadm config images list --kubernetes-version=v${K8S_VERSION} > /tmp/k8s-images.txt 2>/dev/null || true

    # 使用 ctr 直接拉取阿里云镜像
    local images=(
        "registry.aliyuncs.com/google_containers/kube-apiserver:v${K8S_VERSION}"
        "registry.aliyuncs.com/google_containers/kube-controller-manager:v${K8S_VERSION}"
        "registry.aliyuncs.com/google_containers/kube-scheduler:v${K8S_VERSION}"
        "registry.aliyuncs.com/google_containers/kube-proxy:v${K8S_VERSION}"
        "registry.aliyuncs.com/google_containers/coredns:${COREDNS_VERSION}"
        "registry.aliyuncs.com/google_containers/pause:3.10.1"
        "registry.aliyuncs.com/google_containers/etcd:3.5.26-0"
    )

    for image in "${images[@]}"; do
        log_info "拉取镜像: $image"
        ctr -n k8s.io image pull "$image" || log_warn "镜像 $image 拉取失败，将在初始化时重试"
    done

    log_info "ctr容器镜像预拉取完成"
}

# 生成kubeadm 配置文件
gen_kubeadm_config() {
    local pod_network_cidr="10.244.0.0/16"         # 集群 Pod 网络 CIDR
    local service_network_cidr="10.96.0.0/12"      # 集群 Service 网络 CIDR  不可回收ip 所以网段要大

    log_info "初始化 Kubernetes Master 节点..."
    # 获取网络信息
    local hostname=$(hostname)
    local private_ip=$(get_private_ip)
    local public_ip=$(get_public_ip)

    log_info "网络配置信息:"
    echo "  主机名: $hostname"
    echo "  内网IP: $private_ip"
    echo "  公网IP: ${public_ip:-未获取到}"
    echo ""

    # 询问是否使用自定义域名
    local custom_domain=""
    local control_plane_endpoint=""

#    read -p "是否配置K8S API Server自定义域名？(y/N): " use_custom_domain
#    if [[ "$use_custom_domain" =~ ^[Yy]$ ]]; then
#        read -p "请输入自定义域名（如: k8s.example.com）: " custom_domain
#        if [[ -n "$custom_domain" ]]; then
#            control_plane_endpoint="${custom_domain}:6443"
#            log_info "将使用域名: $custom_domain"
#        fi
#    fi

    # 如果没有自定义域名，使用公网IP或内网IP
    if [[ -z "$control_plane_endpoint" ]]; then
        if [[ -n "$private_ip" ]]; then # 优先使用内网IP
              control_plane_endpoint="${private_ip}:6443"
              log_info "ControlPlane 将使用内网IP: $private_ip"
        else
              control_plane_endpoint="${public_ip}:6443"
              log_info "ControlPlane 将使用公网IP: $public_ip"
        fi
    fi
    log_info "控制面板访问地址: https://$control_plane_endpoint"

    # 创建 kubeadm 配置文件
    local kubeadm_api_version="v1beta4"             # K8s安装工具 kubeadm API版本  考虑和k8s版本兼容性
    local kubelet_api_version="v1beta1"             # Node代理服务 kubelet API版本
    cat > /tmp/kubeadm-config.yaml <<EOF
apiVersion: kubeadm.k8s.io/${kubeadm_api_version}
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
imageRepository: registry.aliyuncs.com/google_containers
controlPlaneEndpoint: ${control_plane_endpoint}
networking:
  podSubnet: ${pod_network_cidr}
  serviceSubnet: ${service_network_cidr}
apiServer:
  certSANs:
    - localhost
    - 127.0.0.1
    - ${hostname}
    - ${private_ip}
EOF
    # 添加公网IP（如果有）
    if [[ -n "$public_ip" ]]; then
        cat >> /tmp/kubeadm-config.yaml <<EOF
    - ${public_ip}
EOF
    fi
    # 添加自定义域名（如果有）
    if [[ -n "$custom_domain" ]]; then
        cat >> /tmp/kubeadm-config.yaml <<EOF
    - ${custom_domain}
EOF
    fi
    # 继续添加配置
    cat >> /tmp/kubeadm-config.yaml <<EOF
  extraArgs:
    - name: advertise-address
      value: ${private_ip}
    - name: bind-address
      value: "0.0.0.0"
---
apiVersion: kubelet.config.k8s.io/${kubelet_api_version}
kind: KubeletConfiguration
cgroupDriver: systemd
EOF

    log_info "kubeadm 配置文件已生成:"
    cat /tmp/kubeadm-config.yaml
    echo ""

 }

# 初始化 Master 节点
init_master() {
    # 生成 kubeadm 配置文件
    gen_kubeadm_config

    # 提前拉取镜像
    prefetch_images

    # 初始化集群
    echo ""
    log_info "自动执行K8S集群初始化(可能需要几分钟)..."
    kubeadm init --config=/tmp/kubeadm-config.yaml --upload-certs || error_exit "K8S集群初始化失败"

    # 配置 kubectl
    log_info "配置 kubectl组件..."
    mkdir -p $HOME/.kube
    cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
    chown $(id -u):$(id -g) $HOME/.kube/config

    # 配置普通用户(如果存在非root用户)
    for user_home in /home/*; do
        if [[ -d "$user_home" ]]; then
            local username=$(basename "$user_home")

            # 检查用户是否真实存在
            if id "$username" &>/dev/null; then
                local user_shell=$(getent passwd "$username" | cut -d: -f7)
                local user_uid=$(id -u "$username")

                # 只为真实用户配置（UID >= 1000 且有有效shell）
                if [[ $user_uid -ge 1000 ]] && [[ "$user_shell" =~ (bash|sh|zsh|fish)$ ]]; then
                    log_info "配置 kubectl for user: $username"
                    mkdir -p "$user_home/.kube"
                    cp -f /etc/kubernetes/admin.conf "$user_home/.kube/config"
                    chown -R "$username:$username" "$user_home/.kube"  #  安全
                fi
            fi
        fi
    done

    log_info "K8S Master 节点初始化完成"
    echo  ""
}

# 安装 Calico 网络插件
install_calico() {
    log_info "安装 Calico ${CALICO_VERSION} 网络插件..."

    # 下载 Calico manifest
    local calico_url="https://docs.tigera.io/calico/latest/manifests/calico.yaml"

    wget -q "$calico_url" -O /tmp/calico.yaml || \
    wget -q "https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VERSION}/manifests/calico.yaml" -O /tmp/calico.yaml || \
    error_exit "Calico manifest 下载失败"

    # 应用 Calico
    kubectl apply -f /tmp/calico.yaml || error_exit "Calico 安装失败"

    log_info "Calico ${CALICO_VERSION} 网络插件安装完成"
    echo ""
}

# 单机模式: 允许 Master 调度 Pod
enable_master_scheduling() {
    log_info "配置单机K8s部署模式: 去除Master节点的污点, 允许其调度运行 Pod 服务..."

    # 等待节点就绪
    sleep 10

    # 去除 Master 节点的污点
    kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true
    kubectl taint nodes --all node-role.kubernetes.io/master- 2>/dev/null || true

    log_info "单机K8s集群调度模式配置完成"
}

# 等待所有 Pod 就绪（健康检查）
wait_for_pods_ready() {
    echo  ""
    log_info "等待系统所有 Pod 启动完成 健康探测中..."

    local max_wait=3600  # 最多等待多少秒
    local wait_time=0
    local check_interval=10

    while [ $wait_time -lt $max_wait ]; do
        # 获取所有 kube-system 命名空间的 Pod 状态
        local pending_pods=$(kubectl get pods -n kube-system --no-headers 2>/dev/null | grep -v "Running\|Completed" | wc -l)
        local total_pods=$(kubectl get pods -n kube-system --no-headers 2>/dev/null | wc -l)
        local running_pods=$((total_pods - pending_pods))

        if [ $pending_pods -eq 0 ] && [ $total_pods -gt 0 ]; then
            log_info "所有系统 Pod 已就绪! ($running_pods/$total_pods)"
            return 0
        fi

        log_info "等待 Pod 就绪... ($running_pods/$total_pods 就绪, $pending_pods 待启动) - ${wait_time}s/${max_wait}s"

        # 显示未就绪的 Pod
        kubectl get pods -n kube-system --no-headers 2>/dev/null | grep -v "Running\|Completed" | awk '{print "  - "$1" ("$3")"}'

        sleep $check_interval
        wait_time=$((wait_time + check_interval))
    done

    log_warn "等待超时，但集群可能仍在初始化中"
    log_warn "请手动检查 Pod 状态: kubectl get pods -A"
    return 1
}

# 诊断镜像拉取问题
diagnose_image_issues() {
    log_info "诊断镜像拉取问题..."

    # 检查 containerd 状态
    log_info "Containerd 状态:"
    systemctl status containerd --no-pager -l | head -20

    # 检查镜像配置
    log_info "镜像源配置:"
    cat /etc/containerd/config.toml | grep -A 10 "registry.mirrors"

    # 检查网络连接
    log_info "测试镜像源连接:"
    curl -I https://registry.aliyuncs.com 2>&1 | head -5

    # 列出已拉取的镜像
#    log_info "已拉取的镜像:"
#    crictl images 2>/dev/null || ctr -n k8s.io images ls 2>/dev/null | head -20

    # 检查失败的 Pod
    log_info "问题 Pod 详情:"
    kubectl get pods -A | grep -E "ImagePull|ErrImage|Pending" | while read line; do
        local pod=$(echo $line | awk '{print $2}')
        local ns=$(echo $line | awk '{print $1}')
        echo "=== Pod: $ns/$pod ==="
        kubectl describe pod $pod -n $ns | grep -A 5 "Events:"
    done
}


# 自动安装 Helm
install_helm() {
    local helm_version="v3.19.5"
    log_info "开始安装 Helm ${helm_version} 包管理..."

    # 检查是否已安装 Helm
    if command -v helm &> /dev/null; then
        current_helm_version=$(helm version --short 2>/dev/null || helm version --template='{{.Version}}' 2>/dev/null)
        echo "Helm 已安装: $current_helm_version"
        read -p "是否重新安装? (y/n): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "跳过 Helm 安装"
            return 0
        fi
    fi

    # 检测操作系统
    OS=$(uname -s | tr '[:upper:]' '[:lower:]')
    ARCH=$(uname -m)

    # 转换架构名称
    case $ARCH in
        x86_64)
            ARCH="amd64"
            ;;
        aarch64|arm64)
            ARCH="arm64"
            ;;
        armv7l)
            ARCH="arm"
            ;;
        *)
            echo "不支持的架构: $ARCH"
            return 1
            ;;
    esac

    echo "检测到系统: $OS-$ARCH"

    # 下载并安装 Helm
    HELM_INSTALL_DIR="/usr/local/bin"
    TMP_DIR=$(mktemp -d)

    echo "下载 Helm 安装脚本..."
    curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 -o "$TMP_DIR/get_helm.sh"

    if [ $? -ne 0 ]; then
        echo "下载失败，尝试使用备用方法..."
        # 备用方法：直接下载二进制文件

        HELM_TAR="helm-${helm_version}-${OS}-${ARCH}.tar.gz"
        DOWNLOAD_URL="https://get.helm.sh/${HELM_TAR}"

        echo "下载 Helm ${helm_version}..."
        curl -fsSL "$DOWNLOAD_URL" -o "$TMP_DIR/$HELM_TAR"

        if [ $? -ne 0 ]; then
            echo "下载失败，请检查网络连接"
            rm -rf "$TMP_DIR"
            return 1
        fi

        echo "解压 Helm..."
        tar -zxf "$TMP_DIR/$HELM_TAR" -C "$TMP_DIR"

        echo "安装 Helm 到 $HELM_INSTALL_DIR..."
        sudo mv "$TMP_DIR/${OS}-${ARCH}/helm" "$HELM_INSTALL_DIR/helm"
        sudo chmod +x "$HELM_INSTALL_DIR/helm"
    else
        # 使用官方安装脚本
        chmod +x "$TMP_DIR/get_helm.sh"
        bash "$TMP_DIR/get_helm.sh"
    fi

    # 清理临时文件
    rm -rf "$TMP_DIR"

    # 验证安装
    if command -v helm &> /dev/null; then
        INSTALLED_HELM_VERSION=$(helm version --short 2>/dev/null || helm version --template='{{.Version}}' 2>/dev/null)
        echo "Helm 安装成功: $INSTALLED_HELM_VERSION"

        # 添加常用的 Helm 仓库
        echo "添加常用 Helm 仓库..."
        helm repo add stable https://charts.helm.sh/stable 2>/dev/null || true
        helm repo add bitnami https://charts.bitnami.com/bitnami 2>/dev/null || true
        helm repo update

        log_info "Helm ${INSTALLED_HELM_VERSION} 安装完成 ✅ "
        log_warn "如果使用海外源有问题，设置代理 "
        return 0
    else
        log_error "Helm 安装失败 ❌"
        return 1
    fi
}

# 自动安装 cert-manager
install_cert_manager() {
    log_info "开始安装 cert-manager ACME证书管理..."
    local cert_manager_version="v1.19.2"

    # 添加 cert-manager 的 Helm 仓库
    helm repo add jetstack https://charts.jetstack.io
    helm repo update

    # 创建 cert-manager 命名空间
    kubectl create namespace cert-manager --dry-run=client -o yaml | kubectl apply -f -

    # 安装 cert-manager CRDs
    kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/${cert_manager_version}/cert-manager.crds.yaml

    # 使用 Helm 安装 cert-manager
    helm install cert-manager jetstack/cert-manager \
        --namespace cert-manager \
        --version ${cert_manager_version} \
        --set installCRDs=false \
        --wait

    # 等待 cert-manager 部署完成
    log_info "等待 cert-manager 启动..."
    kubectl wait --for=condition=available --timeout=600s \
        deployment/cert-manager -n cert-manager
    kubectl wait --for=condition=available --timeout=600s \
        deployment/cert-manager-webhook -n cert-manager
    kubectl wait --for=condition=available --timeout=600s \
        deployment/cert-manager-cainjector -n cert-manager

    log_info "cert-manager 安装完成 ✅ "
    kubectl get pods -n cert-manager
}

# 自动安装 Prometheus
install_prometheus() {
    log_info "开始安装 Prometheus 监控..."
   # 添加 Prometheus 的 Helm 仓库
   if curl -I --connect-timeout 5 "https://prometheus-community.github.io/helm-charts/index.yaml" > /dev/null 2>&1; then
           log_info "使用helm在线安装 prometheus与grafana"
           helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
           helm repo update

           # 创建 monitoring 命名空间
           kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -

           # 安装 kube-prometheus-stack (包含 Prometheus, Grafana, Alertmanager 等)
           local grafana_admin_password="admin@0633"
           helm install prometheus prometheus-community/kube-prometheus-stack \
               --namespace monitoring \
               --set prometheus.prometheusSpec.retention=15d \
               --set prometheus.prometheusSpec.storageSpec.volumeClaimTemplate.spec.resources.requests.storage=20Gi \
               --set grafana.adminPassword=${grafana_admin_password} \
               --wait
    else
           log_error "Prometheus的Helm包网络不通"
           log_info  "使用K8s yaml文件离线安装 prometheus grafana"
           kubectl apply -f prometheus-complete.yaml
    fi

    # 等待 Prometheus 部署完成
    log_info "等待 Prometheus 与 Grafana 启动..."
    kubectl wait --for=condition=available --timeout=600s deployment/prometheus -n monitoring 2>/dev/null || true
    kubectl wait --for=condition=available --timeout=600s deployment/grafana -n monitoring 2>/dev/null || true

    kubectl get pods -n monitoring

    # 显示访问信息
    echo ""
    log_warn "要访问 Prometheus ，请先执行："
    log_info "kubectl port-forward -n monitoring svc/prometheus 9090:9090"
    log_info "Prometheus: http://localhost:9090"
    echo ""
    log_warn "要访问 Grafana，请先执行："
    log_info "kubectl port-forward -n monitoring svc/grafana 3000:3000"
    log_info "Grafana访问地址: http://localhost:3000"
    log_info "Grafana 默认用户名admin 密码: ${grafana_admin_password}"
    echo ""
    log_info "Prometheus 安装完成 ✅ "
    echo ""
}

# 安装 Metrics Server
install_metrics_server() {
    echo ""
    local metrics_server_version="v0.8.1"
    log_info "安装 Metrics Server ${metrics_server_version}版本 指标服务..."
    kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml && \
    kubectl patch deployment metrics-server -n kube-system --type='json' -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
    # 更新镜像源 保证成功下载镜像
    kubectl -n kube-system set image deployment/metrics-server \
      metrics-server=registry.aliyuncs.com/google_containers/metrics-server:${metrics_server_version}
    sleep 5
    log_info "Metrics Server验证安装:"
    kubectl get deployment metrics-server -n kube-system
}

# 安装 Node Exporter
install_node_exporter() {
    echo ""
    log_info "安装 Node Exporter 节点监控指标..."
    kubectl create namespace monitoring 2>/dev/null || true && \
    kubectl apply -f node-exporter.yaml
    log_info "Node Exporter验证安装:"
    kubectl get pods -n monitoring -l app=node-exporter
}

# 初始化 Gateway API
install_gateway_api() {
    local gateway_api_version="v1.4.1"     # Gateway API 版本
    log_info "开始安装 K8s官方 Gateway API ${gateway_api_version} 网关..."

    log_info "安装 Gateway API CRDs扩展..."
    kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/${gateway_api_version}/standard-install.yaml 2>/dev/null
    if [ $? -ne 0 ]; then
         log_warn "GitHub 访问失败，使用离线 YAML安装 Gateway API..."
         kubectl apply -f gateway-api.yaml
    fi

    if [ $? -ne 0 ]; then
        log_error "Gateway API 安装失败"
        return 1
    fi

    log_info "等待 Gateway API CRDs 就绪..."
    sleep 5

    # 验证安装
    kubectl get crd | grep gateway.networking.k8s.io

    if [ $? -eq 0 ]; then
        echo ""
        log_info "Gateway API ${gateway_api_version} 安装完成 ✅"
        echo ""
        log_warn "提示: 标准 Gateway API 已安装，需要配合网关实现使用（如 Envoy Gateway、Istio、Traefik、Nginx Gateway、Kong Gateway 等）"
        sleep 3
        install_envoy_gateway
        return 0
    else
        log_error "Gateway API 安装失败 ❌"
        return 1
    fi
}

# 安装 Envoy Gateway
install_envoy_gateway() {
    # Envoy Gateway 版本
    local envoy_gateway_version="v1.6.3"
    INSTALL_SUCCESS=0
    echo  ""
    log_info "开始安装 Envoy Gateway ${envoy_gateway_version} 版本..."

    # 方法1: 使用 Helm 安装
    kubectl create namespace envoy-gateway-system \
      --dry-run=client -o yaml | kubectl apply -f -

    if helm install eg oci://docker.io/envoyproxy/gateway-helm \
        --namespace envoy-gateway-system \
        --create-namespace \
        --version "${envoy_gateway_version}" \
        --wait; then

        log_info "Helm 安装 Envoy Gateway 成功"
        INSTALL_SUCCESS=1
    else
        log_error "Helm  安装失败，尝试使用 kubectl 安装..."
        if install_envoy_gateway_kubectl "${envoy_gateway_version}"; then
            INSTALL_SUCCESS=1
        else
            INSTALL_SUCCESS=0
        fi
    fi

  # 检查安装是否成功
    if [ $INSTALL_SUCCESS -eq 0 ]; then
        log_error "Envoy Gateway 安装失败"
        return 1
    fi

    # 验证安装
    echo ""
    log_info "等待 Envoy Gateway 启动..."
    kubectl wait --for=condition=available --timeout=300s deployment/envoy-gateway -n envoy-gateway-system 2>/dev/null || true

 # 检查 GatewayClass 是否存在，不存在则创建
    echo ""
    log_info "检查 GatewayClass..."
    if ! kubectl get gatewayclass eg >/dev/null 2>&1; then
        log_warn "GatewayClass 不存在，正在创建..."
        cat <<EOF | kubectl apply -f -
apiVersion: gateway.networking.k8s.io/v1
kind: GatewayClass
metadata:
  name: eg
spec:
  controllerName: gateway.envoyproxy.io/gatewayclass-controller
EOF
        sleep 5
    fi

    echo ""
    log_info "Envoy Gateway ${envoy_gateway_version} 安装完成 ✅"
    echo ""
    log_info "查看 Envoy Gateway 状态:"
    kubectl get pods -n envoy-gateway-system
    echo ""
    log_info "查看 GatewayClass:"
    kubectl get gatewayclass
    echo ""
    log_warn "提示: Envoy Gateway Service 类型为 LoadBalancer，需要配合 MetalLB 获取外部 IP"
    echo ""
}

# 使用 kubectl 安装 Envoy Gateway
install_envoy_gateway_kubectl() {
    log_info "使用 kubectl 直接安装 Envoy Gateway $1 版本..."

    # 尝试从 GitHub 安装
    kubectl apply -f https://github.com/envoyproxy/gateway/releases/download/$1/install.yaml 2>/dev/null
    #kubectl apply -f https://github.com/envoyproxy/gateway/releases/download/$1/quickstart.yaml -n default

    if [ $? -ne 0 ]; then
        log_warn "GitHub 访问失败，使用离线 YAML安装 Envoy Gateway..."
        # 创建基础配置
         kubectl apply -f envoy-gateway.yaml
    fi
}

# 初始化 Ingress Controller (使用 Nginx Ingress Controller)
install_ingress_controller() {
   local nginx_ingress_version="v1.14.2"
   log_info "开始安装 Nginx Ingress Controller ${nginx_ingress_version} 路由控制器..."
   echo ""
   log_info "删除Nginx Ingress现有的所有环境..."
   kubectl delete pod -n ingress-nginx -l app.kubernetes.io/component=controller --force --grace-period=0 2>/dev/null || true
   kubectl delete job -n ingress-nginx ingress-nginx-admission-create 2>/dev/null || true
   kubectl delete job -n ingress-nginx ingress-nginx-admission-patch 2>/dev/null || true
   kubectl delete secret -n ingress-nginx ingress-nginx-admission 2>/dev/null || true
   kubectl delete clusterrole ingress-nginx 2>/dev/null || true
   kubectl delete clusterrolebinding ingress-nginx 2>/dev/null || true
   kubectl delete clusterrole ingress-nginx-admission 2>/dev/null || true
   kubectl delete clusterrolebinding ingress-nginx-admission 2>/dev/null || true
   kubectl delete validatingwebhookconfiguration ingress-nginx-admission 2>/dev/null || true
   kubectl delete mutatingwebhookconfiguration ingress-nginx-admission 2>/dev/null || true
   kubectl delete ingressclass nginx 2>/dev/null || true
   helm uninstall ingress-nginx -n ingress-nginx 2>/dev/null || true
   kubectl delete namespace ingress-nginx 2>/dev/null || true
   kubectl wait --for=delete ns/ingress-nginx --timeout=120s 2>/dev/null || true

   if curl -I --connect-timeout 5 "https://kubernetes.github.io/ingress-nginx/index.yaml" > /dev/null 2>&1; then
       # 添加 Nginx Ingress Helm 仓库
       helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
       helm repo update

       # 创建命名空间
       kubectl create namespace ingress-nginx --dry-run=client -o yaml | kubectl apply -f -

       # 使用 Helm 安装 Nginx Ingress Controller
       log_info "使用 Helm 安装 Nginx Ingress Controller..."
       helm install ingress-nginx ingress-nginx/ingress-nginx \
           --namespace ingress-nginx \
           --set controller.service.type=LoadBalancer \
           --set controller.metrics.enabled=true \
           --set controller.podAnnotations."prometheus\.io/scrape"=true \
           --set controller.podAnnotations."prometheus\.io/port"=10254 \
           --wait

       if [ $? -ne 0 ]; then
           log_error "Ingress Controller 安装失败"
           return 1
       fi
   elif helm version ; then
        log_info "下载 Ingress Controller Helm Chart..."
        curl -LO https://github.com/kubernetes/ingress-nginx/releases/download/helm-chart-4.14.3/ingress-nginx-4.14.3.tgz
        tar -xzf ingress-nginx-4.14.3.tgz
        cd ingress-nginx

    # 创建配置
    log_info "配置国内镜像..."
cat > values-china.yaml <<'EOF'
controller:
  image:
    registry: registry.aliyuncs.com/google_containers
    image: nginx-ingress-controller
    tag: "v1.14.3"
    digest: null
  replicaCount: 2
  service:
    type: NodePort
admissionWebhooks:
  patch:
    image:
      registry: registry.aliyuncs.com/google_containers
      image: kube-webhook-certgen
      tag: "v1.6.7"
      digest: null
ingressClassResource:
  default: true
EOF

    log_info "Helm安装Ingress Controller..."
    kubectl delete namespace ingress-nginx 2>/dev/null || true
    helm install ingress-nginx . \
      --create-namespace \
      --namespace ingress-nginx \
      --values values-china.yaml \
      --wait \
      --timeout 10m

    else
        log_error "Ingress Controller的Helm包网络不通"
        log_info  "使用K8s yaml文件离线安装 Ingress Controller"
        kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-${nginx_ingress_version}/deploy/static/provider/cloud/deploy.yaml 2>/dev/null
        if [ $? -ne 0 ]; then
             log_warn "GitHub 访问失败，使用离线 YAML安装 Nginx Ingress Controller..."
             kubectl apply -f ingress-nginx.yaml
        fi
    fi

    log_info "等待 Ingress Controller 启动..."
    kubectl wait --for=condition=ready --timeout=1200s pod -l app.kubernetes.io/name=ingress-nginx -n ingress-nginx

    echo ""
    log_info "Nginx Ingress Controller ${nginx_ingress_version} 安装完成  ✅"
    echo ""
    log_info "查看 Ingress Controller 状态:"
    kubectl get pods -n ingress-nginx
    echo ""
    log_info "查看 Ingress Controller Service:"
    kubectl get svc -n ingress-nginx
    echo ""
    log_warn "提示: Ingress Controller Service 类型为 LoadBalancer，需要配合 MetalLB 获取外部 IP"
    echo ""
}

# 初始化 MetalLB
install_metallb() {
    local metallb_version="v0.15.3"
    log_info "开始安装 MetalLB ${metallb_version} 负载均衡..."

   if curl -I --connect-timeout 5 "https://metallb.github.io/metallb/index.yaml" > /dev/null 2>&1; then
       # 添加 MetalLB Helm 仓库
       helm repo add metallb https://metallb.github.io/metallb
       helm repo update

       # 创建命名空间
       kubectl create namespace metallb-system --dry-run=client -o yaml | kubectl apply -f -

       # 使用 Helm 安装 MetalLB
       log_info "使用 Helm 安装 MetalLB..."
       helm install metallb metallb/metallb \
           --namespace metallb-system \
           --wait

       if [ $? -ne 0 ]; then
           log_error "MetalLB 安装失败"
           return 1
       fi
    else
      log_error "MetalLB的Helm包网络不通"
      log_info  "使用K8s yaml文件离线安装 MetalLB"
      kubectl apply -f https://raw.githubusercontent.com/metallb/metallb/${metallb_version}/config/manifests/metallb-native.yaml 2>/dev/null
      if [ $? -ne 0 ]; then
           log_warn "GitHub 访问失败，使用离线 YAML安装 MetalLB..."
           kubectl apply -f metallb.yaml
      fi
    fi

    log_info "等待 MetalLB 组件启动..."
    kubectl wait --for=condition=ready --timeout=600s pod -l app=metallb -n metallb-system 2>/dev/null || true

    echo ""
    log_info "MetalLB 组件安装完成，现在配置 IP 地址池..."
    echo ""

    # 获取用户输入 IP 地址范围
    read -p "请输入 MetalLB IP 地址池范围 (例如: 172.16.1.240-172.16.1.250): " IP_RANGE

    if [ -z "$IP_RANGE" ]; then
        echo "未输入 IP 地址范围，跳过自动配置"
        echo ""
        echo "你可以稍后手动创建配置:"
        cat <<'EXAMPLE'
apiVersion: metallb.io/v1beta2
kind: IPAddressPool
metadata:
  name: default-pool
  namespace: metallb-system
spec:
  addresses:
  - 172.16.1.240-172.16.1.250
---
apiVersion: metallb.io/v1beta2
kind: L2Advertisement
metadata:
  name: default-l2
  namespace: metallb-system
spec:
  ipAddressPools:
  - default-pool
EXAMPLE
        return 0
    fi

    # 创建 IP 地址池配置
    cat <<EOF | kubectl apply -f -
apiVersion: metallb.io/v1beta2
kind: IPAddressPool
metadata:
  name: default-pool
  namespace: metallb-system
spec:
  addresses:
  - ${IP_RANGE}
---
apiVersion: metallb.io/v1beta2
kind: L2Advertisement
metadata:
  name: default-l2
  namespace: metallb-system
spec:
  ipAddressPools:
  - default-pool
EOF

    echo ""
    log_info "MetalLB ${metallb_version} 安装并配置完成 ✅"
    echo ""
    log_info "查看 MetalLB 状态:"
    kubectl get pods -n metallb-system
    echo ""
    log_info "查看 IP 地址池:"
    kubectl get ipaddresspool -n metallb-system
    echo ""
    log_info "测试 MetalLB:"
    log_info "  kubectl create deployment nginx --image=nginx"
    log_info "  kubectl expose deployment nginx --port=80 --type=LoadBalancer"
    log_info "  kubectl get svc nginx"
}

# 生成 Worker 节点加入命令
generate_join_command() {
    log_info "生成 K8s Worker 节点加入Master集群命令..."

    local join_command=$(kubeadm token create --print-join-command)

    log_info "=========================================="
    log_info "Worker 节点加入Master集群命令:"
    echo "$join_command"
    log_info "=========================================="

    echo "$join_command" > /root/k8s-join-command.sh
    chmod +x /root/k8s-join-command.sh

    log_info "Worker节点加入Master集群命令已保存到: /root/k8s-join-command.sh"
    echo ""
}

# 显示集群信息
show_cluster_info() {
    echo ""

    log_info "K8s集群节点信息:"
    kubectl get nodes -o wide
    echo ""

    log_info "K8s集群 Pod 信息:"
    kubectl get pods -A -o wide
    echo ""

    log_info "K8s集群组件状态:"
    kubectl get componentstatuses 2>/dev/null || log_warn "ComponentStatus API 已弃用"
    echo ""

    log_info "K8s常用命令:"
    echo "  查看节点: kubectl get nodes"
    echo "  查看Pod:  kubectl get pods -A"
    echo "  查看服务: kubectl get svc -A"
    echo "  查看日志: kubectl logs <pod-name> -n <namespace>"
    echo "  诊断Pod:  kubectl describe pod <pod-name> -n <namespace>"
    echo ""

    log_info "K8s版本信息:"
    echo "Kubernetes: $(kubectl version --short 2>/dev/null | grep Server || kubectl version --client)"
    echo "Containerd: $(containerd --version | awk '{print $3}')"
    echo ""

    log_info "=========================================="
    log_info "KubeConfig管理配置文件通常位于Master节点的: /etc/kubernetes/admin.conf"
    log_info "=========================================="
    echo ""
}

# 主菜单
main_menu() {
    clear
    echo "=========================================="
    echo "   Kubernetes 集群部署脚本"
    echo "   版本: K8s ${K8S_VERSION}"
    echo "=========================================="
    echo ""
    echo "请选择K8s部署模式:"
    echo "  1) 单机模式 (Single Node)"
    echo "  2) 多机模式 - Master 节点"
    echo "  3) 多机模式 - Worker 节点"
    echo "  4) 仅安装基础组件(不初始化集群)"
    echo "  5) 诊断现有集群问题"
    echo "  6) 安装Helm包管理"
    echo "  7) 安装Cert Manager自动化证书组件"
    echo "  8) 安装Metrics Server与Node Exporter监控运维组件"
    echo "  9) 安装Gateway API网关与Envoy Gateway组件"
    echo "  10) 安装MetalLB负载均衡组件"
    echo "  11) 安装Ingress Controller路由控制组件"
    echo "  12) 安装Prometheus Grafana监控组件"
    echo "  0) 退出"
    echo ""
    read -p "请输入选项 [0-12]: " choice

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
            diagnose_existing_cluster
            ;;
        6)
            install_helm
            ;;
        7)
            install_cert_manager
            ;;
        8)
            install_metrics_server
            install_node_exporter
            ;;
        9)
            install_gateway_api
            ;;
        10)
            install_metallb
            ;;
        11)
            install_ingress_controller
            ;;
        12)
            install_prometheus
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

# 诊断现有集群
diagnose_existing_cluster() {
    log_info "=========================================="
    log_info "诊断现有集群"
    log_info "=========================================="
    echo ""

    check_root

    # 检查集群状态
    log_info "集群节点状态:"
    kubectl get nodes -o wide
    echo ""

    log_info "Pod 状态:"
    kubectl get pods -A -o wide
    echo ""

    # 诊断镜像问题
    diagnose_image_issues

    echo ""
    log_info "=========================================="
    log_info "诊断完成"
    log_info "=========================================="
}

# 单机模式部署
deploy_single_node() {
    log_info "=========================================="
    log_info "开始单机K8S集群部署"
    log_info "Kubernetes 版本: ${K8S_VERSION}"
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
    init_master
    install_calico
    enable_master_scheduling

    # 等待 Pod 就绪
    wait_for_pods_ready

    show_cluster_info

    log_info "=========================================="
    log_info "✅ 单机 K8S v${K8S_VERSION} 集群部署完成 🎉"
    log_info "=========================================="
    echo ""
}

# Master 节点部署
deploy_master_node() {
    log_info "=========================================="
    log_info "开始 K8s Master 节点部署"
    log_info "Kubernetes 版本: ${K8S_VERSION}"
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
    init_master
    install_calico

    # 等待 Pod 就绪
    wait_for_pods_ready

    show_cluster_info
    generate_join_command

    log_info "=========================================="
    log_info "✅ K8s v${K8S_VERSION} Master 节点部署完成 🎉"
    #log_info "请在 K8s Worker 节点上运行加入命令"
    log_info "=========================================="
    echo ""
}

# Worker 节点部署
deploy_worker_node() {
    log_info "=========================================="
    log_info "开始 K8s Worker 节点部署"
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
    log_info "✅ K8s v${K8S_VERSION} Worker 节点基础组件安装完成 🎉"
    log_info "=========================================="
    echo ""
    log_warn "请在 K8s Master 节点执行以下命令获取加入命令:"
    echo "  kubeadm token create --print-join-command"
    echo ""
    log_warn "然后在本节点执行该命令加入K8S集群!"
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
    log_info "✅ K8s基础组件安装完成!"
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