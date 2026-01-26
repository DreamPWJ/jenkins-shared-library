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
BLUE='\033[0;34m'
NC='\033[0m'

# 配置变量
K8S_VERSION=${K8S_VERSION:-"1.31.0"}  # K8s版本 注意系统版本的兼容性
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
log_debug() { echo -e "${BLUE}[DEBUG]${NC} $1"; }

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

# 检查端口占用
check_ports() {
    log_info "检查关键端口占用情况..."
    local PORTS="6443 10250 10259 10257 2379 2380"
    local OCCUPIED=false

    for PORT in $PORTS; do
        if netstat -tunlp 2>/dev/null | grep -q ":$PORT "; then
            log_warn "端口 $PORT 已被占用:"
            netstat -tunlp | grep ":$PORT "
            OCCUPIED=true
        fi
    done

    if [ "$OCCUPIED" = true ]; then
        log_warn "检测到端口占用,这可能导致初始化失败"
        echo -n "是否继续? (y/n): "
        read -r CONTINUE
        if [ "$CONTINUE" != "y" ]; then
            log_error "用户取消操作"
        fi
    fi
}

# 检查系统资源
check_resources() {
    log_info "检查系统资源..."

    # 检查内存
    local TOTAL_MEM=$(free -m | awk '/^Mem:/{print $2}')
    if [ "$TOTAL_MEM" -lt 2048 ]; then
        log_warn "系统内存不足2GB (当前: ${TOTAL_MEM}MB), 建议至少2GB"
    fi

    # 检查CPU
    local CPU_CORES=$(nproc)
    if [ "$CPU_CORES" -lt 2 ]; then
        log_warn "CPU核心数少于2 (当前: ${CPU_CORES}), 建议至少2核"
    fi

    # 检查磁盘空间
    local FREE_SPACE=$(df -BG / | awk 'NR==2 {print $4}' | sed 's/G//')
    if [ "$FREE_SPACE" -lt 20 ]; then
        log_warn "根分区可用空间不足20GB (当前: ${FREE_SPACE}GB)"
    fi

    log_info "资源检查完成: CPU=${CPU_CORES}核, 内存=${TOTAL_MEM}MB, 磁盘=${FREE_SPACE}GB"
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

    modprobe overlay 2>/dev/null || log_warn "overlay模块加载失败"
    modprobe br_netfilter 2>/dev/null || log_warn "br_netfilter模块加载失败"

    # 配置内核参数
    cat <<EOF | tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
net.ipv4.conf.all.forwarding        = 1
EOF

    sysctl --system > /dev/null

    log_info "系统初始化配置完成"
}

# 安装依赖包
install_dependencies() {
    log_info "安装Kubernetes依赖包..."

    case $OS in
        ubuntu|debian)
            apt-get update
            apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release socat conntrack ipset ebtables
            ;;
        centos|rhel)
            yum install -y yum-utils device-mapper-persistent-data lvm2 socat conntrack ipset ebtables
            ;;
        *)
            log_error "不支持的操作系统: $OS"
            ;;
    esac
}

# 安装 containerd
install_containerd() {
    if command -v containerd >/dev/null 2>&1; then
        log_info "检测到 containerd 已安装,版本: $(containerd --version | awk '{print $3}')"
        if systemctl is-active --quiet containerd; then
            return 0
        else
            log_warn "containerd 已安装但未运行, 将重新配置"
        fi
    fi

    log_info "安装 containerd..."

    local SUCCESS=false

    if detect_network; then
        log_info "尝试使用阿里云镜像源..."
        case $OS in
            ubuntu|debian)
                mkdir -p /etc/apt/keyrings
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
        log_info "配置 containerd 国内镜像加速..."

        local PAUSE_VERSION="3.9"
        case $K8S_VERSION in
            1.32.*|1.31.*) PAUSE_VERSION="3.10" ;;
            1.30.*) PAUSE_VERSION="3.9" ;;
            1.29.*) PAUSE_VERSION="3.9" ;;
        esac

        PAUSE_IMAGE="registry.cn-hangzhou.aliyuncs.com/google_containers/pause:${PAUSE_VERSION}"

        if grep -q "sandbox_image" /etc/containerd/config.toml; then
            sed -i "s|sandbox_image = \".*\"|sandbox_image = \"$PAUSE_IMAGE\"|" /etc/containerd/config.toml
        else
            sed -i "/\[plugins.\"io.containerd.grpc.v1.cri\"\]/a\  sandbox_image = \"$PAUSE_IMAGE\"" /etc/containerd/config.toml
        fi

        # 配置 registry mirrors
        cat >> /etc/containerd/config.toml <<'EOF'

[plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
  endpoint = ["https://registry.cn-hangzhou.aliyuncs.com"]

[plugins."io.containerd.grpc.v1.cri".registry.mirrors."registry.k8s.io"]
  endpoint = ["https://registry.cn-hangzhou.aliyuncs.com/google_containers"]
EOF
    fi

    systemctl restart containerd
    systemctl enable containerd

    # 验证containerd状态
    if ! systemctl is-active --quiet containerd; then
        log_error "containerd 启动失败,请检查日志: journalctl -xeu containerd"
    fi

    log_info "containerd 安装配置完成"
}

# 安装 kubeadm, kubelet, kubectl
install_k8s_tools() {
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
            mkdir -p /etc/apt/keyrings

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

# 预拉取镜像
prefetch_images() {
    log_info "预拉取 Kubernetes 镜像..."

    # 列出需要的镜像
    kubeadm config images list --kubernetes-version v${K8S_VERSION} 2>/dev/null || true

    # 尝试预拉取
    log_info "开始拉取镜像,这可能需要几分钟..."
    if detect_network; then
        kubeadm config images pull --image-repository registry.cn-hangzhou.aliyuncs.com/google_containers --kubernetes-version v${K8S_VERSION} || \
            log_warn "镜像预拉取失败,将在初始化时自动拉取"
    else
        kubeadm config images pull --kubernetes-version v${K8S_VERSION} || \
            log_warn "镜像预拉取失败,将在初始化时自动拉取"
    fi
}

# 获取主机IP
get_host_ip() {
    # 如果已指定API Server地址则使用
    if [ -n "$API_SERVER_ADVERTISE_ADDRESS" ]; then
        echo "$API_SERVER_ADVERTISE_ADDRESS"
        return
    fi

    # 否则自动检测
    local IP=$(ip -4 addr show | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | grep -v '127.0.0.1' | head -n1)

    if [ -z "$IP" ]; then
        log_warn "无法自动检测IP地址,使用127.0.0.1"
        echo "127.0.0.1"
    else
        echo "$IP"
    fi
}

# 初始化 Master 节点
init_master() {
    log_info "初始化Master节点..."

    # 获取主机IP
    local HOST_IP=$(get_host_ip)
    log_info "使用API Server地址: $HOST_IP"

    local INIT_CONFIG="/tmp/kubeadm-config.yaml"

    if detect_network; then
        log_info "配置使用阿里云镜像仓库..."
        cat <<EOF > $INIT_CONFIG
apiVersion: kubeadm.k8s.io/v1beta4
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
imageRepository: registry.cn-hangzhou.aliyuncs.com/google_containers
controlPlaneEndpoint: "${HOST_IP}:6443"
networking:
  podSubnet: ${POD_NETWORK_CIDR}
  serviceSubnet: ${SERVICE_CIDR}
---
apiVersion: kubeadm.k8s.io/v1beta4
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: ${HOST_IP}
  bindPort: 6443
nodeRegistration:
  criSocket: unix:///var/run/containerd/containerd.sock
  taints: null
EOF
    else
        cat <<EOF > $INIT_CONFIG
apiVersion: kubeadm.k8s.io/v1beta4
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
controlPlaneEndpoint: "${HOST_IP}:6443"
networking:
  podSubnet: ${POD_NETWORK_CIDR}
  serviceSubnet: ${SERVICE_CIDR}
---
apiVersion: kubeadm.k8s.io/v1beta4
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: ${HOST_IP}
  bindPort: 6443
nodeRegistration:
  criSocket: unix:///var/run/containerd/containerd.sock
  taints: null
EOF
    fi

    log_info "Kubeadm初始化K8s v${K8S_VERSION}版本集群环境..."
    log_info "配置文件位置: $INIT_CONFIG"

    # 显示配置内容
    log_debug "初始化配置:"
    cat $INIT_CONFIG

    # 执行初始化
    if kubeadm init --config=$INIT_CONFIG --upload-certs --v=5; then
        log_info "kubeadm init 执行成功"
    else
        log_error "kubeadm init 执行失败,请查看上方日志"
    fi

    # 配置 kubectl
    mkdir -p $HOME/.kube
    cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
    chown $(id -u):$(id -g) $HOME/.kube/config

    # 等待API Server就绪
    log_info "等待API Server就绪..."
    local RETRY=0
    while [ $RETRY -lt 60 ]; do
        if kubectl get nodes &>/dev/null; then
            log_info "API Server已就绪"
            break
        fi
        sleep 2
        RETRY=$((RETRY + 1))
    done

    if [ $RETRY -eq 60 ]; then
        log_error "API Server启动超时,请检查: kubectl get pods -n kube-system"
    fi

    log_info "Master 节点初始化完成"

    # 安装 CNI 插件 (Flannel)
    log_info "安装 Flannel CNI 插件..."

    if detect_network; then
        kubectl apply -f https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml 2>/dev/null || \
        kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml 2>/dev/null || \
        log_warn "Flannel 安装失败,请手动安装 CNI 插件"
    else
        kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml || \
        log_warn "Flannel 安装失败,请检查网络或手动安装"
    fi

    # 如果是单机模式,移除 master 污点
    if [ "$CLUSTER_MODE" = "single" ]; then
        log_info "单机模式: 移除 master 节点污点,允许调度 Pod..."
        kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true
        kubectl taint nodes --all node-role.kubernetes.io/master- 2>/dev/null || true
    fi

    # 显示加入集群的命令
    log_info "================================"
    log_info "Worker 节点加入集群命令:"
    kubeadm token create --print-join-command
    log_info "================================"
}

# 加入集群作为 Worker 节点
join_cluster() {
    log_info "等待输入 join 命令..."
    echo "请输入 master 节点提供的 join 命令:"
    read -r JOIN_COMMAND

    eval $JOIN_COMMAND

    log_info "Worker 节点加入集群完成"
}

# 故障排查
troubleshoot() {
    log_info "开始故障排查..."

    log_info "1. 检查容器运行时状态:"
    systemctl status containerd --no-pager || true

    log_info "2. 检查kubelet状态:"
    systemctl status kubelet --no-pager || true

    log_info "3. 检查kubelet日志:"
    journalctl -xeu kubelet --no-pager -n 50 || true

    log_info "4. 检查containerd容器:"
    crictl --runtime-endpoint unix:///var/run/containerd/containerd.sock ps -a || true

    log_info "5. 检查系统pod:"
    kubectl get pods -n kube-system || true

    log_info "6. 检查节点:"
    kubectl get nodes || true
}

# 显示使用说明
show_usage() {
    cat <<EOF
使用说明:
========================================
环境变量:
  K8S_VERSION                    - K8s 版本 (默认: 1.31.0)
  CONTAINER_RUNTIME              - 容器运行时 containerd/docker (默认: containerd)
  POD_NETWORK_CIDR               - Pod 网络 CIDR (默认: 10.244.0.0/16)
  SERVICE_CIDR                   - Service CIDR (默认: 10.96.0.0/12)
  CLUSTER_MODE                   - 集群模式 single/cluster (默认: single)
  NODE_ROLE                      - 节点角色 master/worker (默认: master)
  USE_MIRROR                     - 使用镜像源 auto/yes/no (默认: auto)
  API_SERVER_ADVERTISE_ADDRESS   - API Server地址 (默认: 自动检测)

使用示例:
  # 检查关键端口
  netstat -tunlp | grep -E '6443|10259|10257|10250|2379|2380'

  # 单机模式部署 K8s
  sudo ./k8s_init.sh

  # 集群模式 - Master 节点
  sudo K8S_VERSION=1.31.0 CLUSTER_MODE=cluster NODE_ROLE=master ./k8s_init.sh

  # 集群模式 - Worker 节点
  sudo NODE_ROLE=worker ./k8s_init.sh

  # 指定API Server地址
  sudo API_SERVER_ADVERTISE_ADDRESS=192.168.1.100 ./k8s_init.sh

  # 强制使用国内镜像源
  sudo USE_MIRROR=yes ./k8s_init.sh

故障排查:
  # 查看kubelet日志
  journalctl -xeu kubelet -f

  # 查看containerd日志
  journalctl -xeu containerd -f

  # 查看系统pod状态
  kubectl get pods -n kube-system

  # 重置集群
  kubeadm reset -f
========================================
EOF
}

# 主函数
main() {
    log_info "=== Kubernetes 集群初始化脚本 (优化版) ==="
    log_info "K8s版本: $K8S_VERSION | 容器运行时: $CONTAINER_RUNTIME | 模式: $CLUSTER_MODE | 角色: $NODE_ROLE"

    check_root
    detect_os
    check_resources
    check_ports
    system_init
    install_dependencies

    # 安装容器运行时
    if [ "$CONTAINER_RUNTIME" = "docker" ]; then
        log_error "Docker作为K8s运行时已被弃用,请使用containerd"
    else
        install_containerd
    fi

    install_k8s_tools

    # 根据角色执行不同操作
    if [ "$NODE_ROLE" = "master" ]; then
        prefetch_images
        init_master

        log_info "================================"
        log_info "Master 节点安装完成!"
        log_info "检查集群状态: kubectl get nodes"
        log_info "检查 Pod 状态: kubectl get pods -A"
        log_info "如遇问题运行: $0 --troubleshoot"
        log_info "================================"
    else
        join_cluster

        log_info "================================"
        log_info "Worker 节点加入完成!"
        log_info "请在 Master 节点检查: kubectl get nodes"
        log_info "================================"
    fi
}

# 命令行参数处理
case "${1:-}" in
    -h|--help)
        show_usage
        exit 0
        ;;
    --troubleshoot)
        troubleshoot
        exit 0
        ;;
    *)
        main
        ;;
esac