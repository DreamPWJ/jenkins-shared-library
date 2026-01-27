#!/bin/bash

################################################################################
# Kubernetes 自动部署脚本 (使用国内镜像源)
# 支持: 单机/多机部署、版本自定义、完整错误处理
################################################################################

set -e  # 遇到错误立即退出

# ============================================================================
# 配置区域 - 可根据需要修改
# ============================================================================

# K8s 组件版本配置
K8S_VERSION="${K8S_VERSION:-1.28.2}"           # Kubernetes 版本
CONTAINERD_VERSION="${CONTAINERD_VERSION:-1.7.8}"  # containerd 版本
RUNC_VERSION="${RUNC_VERSION:-1.1.10}"         # runc 版本
CNI_VERSION="${CNI_VERSION:-1.3.0}"            # CNI 插件版本
CALICO_VERSION="${CALICO_VERSION:-3.26.3}"     # Calico 版本

# 镜像仓库配置 (使用阿里云镜像)
IMAGE_REGISTRY="${IMAGE_REGISTRY:-registry.aliyuncs.com/google_containers}"
PAUSE_IMAGE="${PAUSE_IMAGE:-registry.aliyuncs.com/google_containers/pause:3.9}"

# 网络配置
POD_CIDR="${POD_CIDR:-10.244.0.0/16}"
SERVICE_CIDR="${SERVICE_CIDR:-10.96.0.0/12}"

# 节点类型配置
NODE_TYPE="${NODE_TYPE:-master}"  # master 或 worker
MASTER_IP="${MASTER_IP:-}"        # master节点IP (worker节点必填)
JOIN_TOKEN="${JOIN_TOKEN:-}"      # join token (worker节点必填)
JOIN_CA_HASH="${JOIN_CA_HASH:-}"  # CA hash (worker节点必填)

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# ============================================================================
# 错误处理函数
# ============================================================================

handle_error() {
    local exit_code=$?
    local line_number=$1
    log_error "脚本在第 ${line_number} 行执行失败,退出码: ${exit_code}"
    log_error "请检查上方错误信息,常见问题:"
    log_error "  1. 镜像下载失败: 检查网络连接和镜像源配置"
    log_error "  2. API Server 连接失败: 检查防火墙和端口 6443"
    log_error "  3. 依赖包安装失败: 检查 yum/apt 源配置"
    exit $exit_code
}

trap 'handle_error ${LINENO}' ERR

# ============================================================================
# 系统检查函数
# ============================================================================

check_system() {
    log_step "检查系统环境..."

    # 检查是否为root用户
    if [[ $EUID -ne 0 ]]; then
        log_error "此脚本必须以root用户运行"
        exit 1
    fi

    # 检测操作系统
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
        OS_VERSION=$VERSION_ID
    else
        log_error "无法检测操作系统类型"
        exit 1
    fi

    log_info "操作系统: $OS $OS_VERSION"

    # 检查系统资源
    local mem_total=$(free -m | awk '/^Mem:/{print $2}')
    local cpu_cores=$(nproc)

    log_info "CPU核心数: $cpu_cores"
    log_info "总内存: ${mem_total}MB"

    if [ "$NODE_TYPE" == "master" ]; then
        if [ $mem_total -lt 2048 ]; then
            log_warn "Master节点建议至少2GB内存,当前仅有 ${mem_total}MB"
        fi
        if [ $cpu_cores -lt 2 ]; then
            log_warn "Master节点建议至少2核CPU,当前仅有 ${cpu_cores}核"
        fi
    fi

    # 检查网络连接
    log_info "检查网络连接..."
    if ! ping -c 2 223.5.5.5 &>/dev/null; then
        log_error "无法连接到互联网,请检查网络配置"
        exit 1
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

    # 关闭防火墙 (生产环境建议配置规则而不是关闭)
    log_info "配置防火墙..."
    if command -v firewalld &>/dev/null; then
        systemctl stop firewalld 2>/dev/null || true
        systemctl disable firewalld 2>/dev/null || true
    fi

    if command -v ufw &>/dev/null; then
        ufw disable 2>/dev/null || true
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

    sysctl --system >/dev/null

    log_info "系统配置完成"
}

# ============================================================================
# 安装容器运行时 (containerd)
# ============================================================================

install_containerd() {
    log_step "安装 containerd..."

    # 检查是否已安装
    if command -v containerd &>/dev/null; then
        local installed_version=$(containerd --version | awk '{print $3}' | cut -d'v' -f2)
        log_info "containerd 已安装,版本: $installed_version"
        if [ "$installed_version" == "$CONTAINERD_VERSION" ]; then
            log_info "版本匹配,跳过安装"
            return 0
        fi
    fi

    log_info "下载 containerd ${CONTAINERD_VERSION}..."

    local download_url="https://mirrors.aliyun.com/docker-ce/linux/static/stable/x86_64/containerd-${CONTAINERD_VERSION}-linux-amd64.tar.gz"
    local temp_file="/tmp/containerd.tar.gz"

    # 下载并重试
    local retry=0
    local max_retry=3
    while [ $retry -lt $max_retry ]; do
        if wget -q --show-progress -O "$temp_file" "$download_url"; then
            break
        else
            retry=$((retry + 1))
            if [ $retry -eq $max_retry ]; then
                log_error "下载 containerd 失败,已重试 $max_retry 次"
                log_error "下载地址: $download_url"
                exit 1
            fi
            log_warn "下载失败,重试 $retry/$max_retry..."
            sleep 3
        fi
    done

    log_info "解压并安装 containerd..."
    tar Cxzvf /usr/local "$temp_file" >/dev/null
    rm -f "$temp_file"

    # 创建 containerd 配置目录
    mkdir -p /etc/containerd

    # 生成默认配置
    containerd config default > /etc/containerd/config.toml

    # 配置 systemd cgroup 驱动和国内镜像源
    log_info "配置 containerd 使用国内镜像源..."
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
    sed -i "s#registry.k8s.io/pause:.*#${PAUSE_IMAGE}#" /etc/containerd/config.toml

    # 配置镜像加速
    cat >> /etc/containerd/config.toml <<EOF

[plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
  endpoint = ["https://docker.mirrors.ustc.edu.cn", "https://registry.docker-cn.com"]
[plugins."io.containerd.grpc.v1.cri".registry.mirrors."k8s.gcr.io"]
  endpoint = ["https://registry.aliyuncs.com/google_containers"]
[plugins."io.containerd.grpc.v1.cri".registry.mirrors."registry.k8s.io"]
  endpoint = ["https://registry.aliyuncs.com/google_containers"]
EOF

    # 安装 runc
    log_info "安装 runc ${RUNC_VERSION}..."
    local runc_url="https://github.com/opencontainers/runc/releases/download/v${RUNC_VERSION}/runc.amd64"
    wget -q --show-progress -O /usr/local/sbin/runc "$runc_url" || {
        log_error "下载 runc 失败"
        exit 1
    }
    chmod +x /usr/local/sbin/runc

    # 安装 CNI 插件
    log_info "安装 CNI 插件 ${CNI_VERSION}..."
    local cni_url="https://github.com/containernetworking/plugins/releases/download/v${CNI_VERSION}/cni-plugins-linux-amd64-v${CNI_VERSION}.tgz"
    mkdir -p /opt/cni/bin
    wget -q --show-progress -O /tmp/cni-plugins.tgz "$cni_url" || {
        log_error "下载 CNI 插件失败"
        exit 1
    }
    tar Cxzvf /opt/cni/bin /tmp/cni-plugins.tgz >/dev/null
    rm -f /tmp/cni-plugins.tgz

    # 创建 systemd service
    log_info "创建 containerd systemd 服务..."
    cat > /etc/systemd/system/containerd.service <<EOF
[Unit]
Description=containerd container runtime
Documentation=https://containerd.io
After=network.target local-fs.target

[Service]
ExecStartPre=-/sbin/modprobe overlay
ExecStart=/usr/local/bin/containerd
Type=notify
Delegate=yes
KillMode=process
Restart=always
RestartSec=5
LimitNPROC=infinity
LimitCORE=infinity
LimitNOFILE=infinity
TasksMax=infinity
OOMScoreAdjust=-999

[Install]
WantedBy=multi-user.target
EOF

    # 启动 containerd
    log_info "启动 containerd..."
    systemctl daemon-reload
    systemctl enable containerd
    systemctl restart containerd

    # 验证 containerd 状态
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

    # 安装依赖
    apt-get update
    apt-get install -y apt-transport-https ca-certificates curl

    # 添加阿里云 Kubernetes 源
    curl -fsSL https://mirrors.aliyun.com/kubernetes/apt/doc/apt-key.gpg | apt-key add -

    cat > /etc/apt/sources.list.d/kubernetes.list <<EOF
deb https://mirrors.aliyun.com/kubernetes/apt/ kubernetes-xenial main
EOF

    apt-get update

    # 安装指定版本
    local k8s_version_apt="${K8S_VERSION}-00"
    log_info "安装 kubelet=${k8s_version_apt} kubeadm=${k8s_version_apt} kubectl=${k8s_version_apt}"

    apt-get install -y kubelet=${k8s_version_apt} kubeadm=${k8s_version_apt} kubectl=${k8s_version_apt}
    apt-mark hold kubelet kubeadm kubectl

    systemctl enable kubelet
}

install_k8s_yum() {
    log_info "使用 YUM 安装 Kubernetes..."

    # 添加阿里云 Kubernetes 源
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

    yum install -y kubelet-${k8s_version_yum} kubeadm-${k8s_version_yum} kubectl-${k8s_version_yum}

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
        log_error "无法获取镜像列表,请检查 kubeadm 是否正确安装"
        exit 1
    fi

    log_info "将从以下镜像仓库拉取: $IMAGE_REGISTRY"

    while IFS= read -r image; do
        # 替换为国内镜像
        local image_name=$(echo $image | awk -F'/' '{print $NF}')
        local local_image="${IMAGE_REGISTRY}/${image_name}"

        log_info "拉取镜像: $local_image"

        local retry=0
        local max_retry=3
        while [ $retry -lt $max_retry ]; do
            if ctr -n k8s.io image pull "$local_image"; then
                # 打标签为原始镜像名
                ctr -n k8s.io image tag "$local_image" "$image" >/dev/null 2>&1 || true
                log_info "✓ 镜像拉取成功: $image_name"
                break
            else
                retry=$((retry + 1))
                if [ $retry -eq $max_retry ]; then
                    log_error "镜像拉取失败: $local_image"
                    log_error "已重试 $max_retry 次,请检查:"
                    log_error "  1. 网络连接是否正常"
                    log_error "  2. 镜像仓库地址是否正确"
                    log_error "  3. containerd 服务是否运行正常"
                    exit 1
                fi
                log_warn "拉取失败,重试 $retry/$max_retry..."
                sleep 3
            fi
        done
    done <<< "$images"

    log_info "所有镜像拉取完成"
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
    if ! kubeadm init --config=/tmp/kubeadm-config.yaml --upload-certs; then
        log_error "Kubernetes 初始化失败"
        log_error "常见问题排查:"
        log_error "  1. 检查端口 6443 是否被占用: netstat -tlnp | grep 6443"
        log_error "  2. 检查 containerd 是否运行: systemctl status containerd"
        log_error "  3. 检查镜像是否拉取成功: ctr -n k8s.io images ls"
        log_error "  4. 查看详细日志: journalctl -xeu kubelet"
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
            log_error "请检查:"
            log_error "  1. 端口 6443 是否可访问: curl -k https://localhost:6443"
            log_error "  2. 查看 kube-apiserver 日志: kubectl logs -n kube-system kube-apiserver-*"
            exit 1
        fi
        echo -n "."
        sleep 2
    done
    echo ""

    # 安装网络插件 (Calico)
    install_calico

    # 输出 join 命令
    log_info "生成 Worker 节点加入命令..."
    local join_cmd=$(kubeadm token create --print-join-command)

    echo ""
    log_info "=================================="
    log_info "Master 节点初始化完成!"
    log_info "=================================="
    echo ""
    log_info "Worker 节点加入集群命令:"
    echo -e "${GREEN}${join_cmd}${NC}"
    echo ""
    log_info "或使用此脚本加入 Worker 节点:"
    echo -e "${GREEN}NODE_TYPE=worker MASTER_IP=${node_ip} JOIN_TOKEN=<token> JOIN_CA_HASH=<hash> bash $0${NC}"
    echo ""

    # 保存 join 信息到文件
    echo "$join_cmd" > /root/k8s-join-command.sh
    chmod +x /root/k8s-join-command.sh
    log_info "Join 命令已保存到: /root/k8s-join-command.sh"
}

# ============================================================================
# 安装 Calico 网络插件
# ============================================================================

install_calico() {
    log_step "安装 Calico 网络插件 (版本: ${CALICO_VERSION})..."

    local calico_url="https://docs.projectcalico.org/v${CALICO_VERSION}/manifests/calico.yaml"

    log_info "下载 Calico manifest..."
    if ! wget -q --show-progress -O /tmp/calico.yaml "$calico_url"; then
        log_warn "从官方源下载失败,尝试使用备用源..."
        # 使用备用的 manifest
        kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/v${CALICO_VERSION}/manifests/calico.yaml || {
            log_error "Calico 安装失败"
            exit 1
        }
    else
        # 修改 Pod CIDR
        sed -i "s|192.168.0.0/16|${POD_CIDR}|g" /tmp/calico.yaml

        log_info "应用 Calico..."
        kubectl apply -f /tmp/calico.yaml
    fi

    log_info "等待 Calico Pod 就绪..."
    kubectl wait --for=condition=Ready pods -l k8s-app=calico-node -n kube-system --timeout=300s || {
        log_warn "Calico Pod 启动超时,请手动检查"
        log_warn "检查命令: kubectl get pods -n kube-system"
    }

    log_info "Calico 安装完成"
}

# ============================================================================
# 加入 Worker 节点
# ============================================================================

join_worker() {
    log_step "加入 Worker 节点到集群..."

    if [ -z "$MASTER_IP" ]; then
        log_error "Worker 节点必须指定 MASTER_IP"
        log_error "用法: MASTER_IP=<master-ip> NODE_TYPE=worker bash $0"
        exit 1
    fi

    log_info "Master 节点 IP: $MASTER_IP"

    # 检查与 Master 的连接
    log_info "检查与 Master 节点的连接..."
    if ! ping -c 3 "$MASTER_IP" &>/dev/null; then
        log_error "无法连接到 Master 节点: $MASTER_IP"
        exit 1
    fi

    # 检查 API Server 端口
    if ! nc -zv "$MASTER_IP" 6443 &>/dev/null; then
        log_error "无法连接到 API Server 端口 6443"
        log_error "请检查:"
        log_error "  1. Master 节点防火墙是否开放 6443 端口"
        log_error "  2. Master 节点 API Server 是否正常运行"
        exit 1
    fi

    # 构建 join 命令
    if [ -n "$JOIN_TOKEN" ] && [ -n "$JOIN_CA_HASH" ]; then
        local join_cmd="kubeadm join ${MASTER_IP}:6443 --token ${JOIN_TOKEN} --discovery-token-ca-cert-hash ${JOIN_CA_HASH}"
    else
        log_error "Worker 节点必须指定 JOIN_TOKEN 和 JOIN_CA_HASH"
        log_error "这些信息可从 Master 节点获取:"
        log_error "  JOIN_TOKEN: kubeadm token list"
        log_error "  JOIN_CA_HASH: openssl x509 -pubkey -in /etc/kubernetes/pki/ca.crt | openssl rsa -pubin -outform der 2>/dev/null | openssl dgst -sha256 -hex | sed 's/^.* //'"
        exit 1
    fi

    log_info "执行加入命令..."
    if ! $join_cmd; then
        log_error "加入集群失败"
        log_error "请检查:"
        log_error "  1. Token 是否有效 (默认24小时过期)"
        log_error "  2. CA Hash 是否正确"
        log_error "  3. 网络连接是否正常"
        exit 1
    fi

    log_info "Worker 节点加入成功!"
    log_info "请在 Master 节点执行以下命令验证:"
    log_info "  kubectl get nodes"
}

# ============================================================================
# 验证集群状态
# ============================================================================

verify_cluster() {
    log_step "验证集群状态..."

    if [ "$NODE_TYPE" == "master" ]; then
        log_info "节点状态:"
        kubectl get nodes -o wide

        echo ""
        log_info "系统 Pod 状态:"
        kubectl get pods -n kube-system

        echo ""
        log_info "组件状态:"
        kubectl get cs 2>/dev/null || kubectl get --raw='/readyz?verbose'
    fi

    log_info "集群验证完成"
}

# ============================================================================
# 清理函数 (可选)
# ============================================================================

cleanup() {
    log_step "清理安装文件..."
    rm -f /tmp/kubeadm-config.yaml
    rm -f /tmp/calico.yaml
    log_info "清理完成"
}

# ============================================================================
# 主函数
# ============================================================================

main() {
    echo "========================================================================"
    echo "  Kubernetes 自动部署脚本"
    echo "  版本: ${K8S_VERSION}"
    echo "  节点类型: ${NODE_TYPE}"
    echo "========================================================================"
    echo ""

    check_system
    configure_system
    install_containerd
    install_k8s_components

    if [ "$NODE_TYPE" == "master" ]; then
        pull_images
        init_master
        verify_cluster
    elif [ "$NODE_TYPE" == "worker" ]; then
        pull_images
        join_worker
    else
        log_error "无效的节点类型: $NODE_TYPE (必须是 master 或 worker)"
        exit 1
    fi

    cleanup

    echo ""
    echo "========================================================================"
    log_info "🎉 Kubernetes 部署完成!"
    echo "========================================================================"
    echo ""

    if [ "$NODE_TYPE" == "master" ]; then
        log_info "下一步操作:"
        log_info "  1. 检查节点: kubectl get nodes"
        log_info "  2. 检查 Pods: kubectl get pods -A"
        log_info "  3. 部署应用: kubectl create deployment nginx --image=nginx"
        echo ""
        log_info "配置文件位置:"
        log_info "  - kubeconfig: ~/.kube/config"
        log_info "  - join命令: /root/k8s-join-command.sh"
    fi
}

# 执行主函数
main "$@"