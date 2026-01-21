#!/bin/bash
# Author: 潘维吉
# Description: Kubernetes集群初始化自动化部署脚本
# Version: 2.0
# kubeadm官方工具支持单机/多机部署，支持最新K8s版本，支持多种容器运行时和网络插件
#
# 使用示例:
# chmod +x k8s-init.sh

# 单机部署（默认使用containerd）尝试运行 --dry-run :
# ./k8s-init.sh --mode single
#
# 多机部署 - 主节点:
# ./k8s-init.sh --mode master --apiserver-advertise 192.168.1.100
#
# 多机部署 - 工作节点:
# ./k8s-init.sh --mode worker --master-ip 192.168.1.100 --token <token> --hash <hash>
#
# 自定义配置:
# ./k8s-init.sh --mode single --version 1.35.0 --runtime containerd --network calico

set -euo pipefail  # 严格错误处理
IFS=$'\n\t'

# ============================================================================
# 全局配置参数
# ============================================================================
SCRIPT_VERSION="2.0"
K8S_VERSION="1.35.0"                     # Kubernetes版本
POD_NETWORK_CIDR="10.244.0.0/16"         # Pod网络CIDR
SERVICE_CIDR="10.96.0.0/12"              # Service网络CIDR
CLUSTER_MODE="single"                    # 集群模式：single/master/worker
CONTAINER_RUNTIME="containerd"           # 容器运行时：docker/containerd
NETWORK_PLUGIN="flannel"                 # 网络插件：flannel/calico/cilium
APISERVER_ADVERTISE_ADDRESS=""           # API Server监听地址
MASTER_IP=""                             # 主节点IP（worker模式必需）
JOIN_TOKEN=""                            # 加入令牌（worker模式必需）
JOIN_HASH=""                             # CA证书哈希（worker模式必需）
CONTROL_PLANE_ENDPOINT=""                # 控制平面端点（高可用）
IMAGE_REPOSITORY="registry.aliyuncs.com/google_containers k8s.gcr.io docker.io/google_containers"  # 镜像仓库
LOG_FILE="/var/log/k8s-init-$(date +%Y%m%d-%H%M%S).log"
DRY_RUN=false                            # 干运行模式
SKIP_PREFLIGHT=false                     # 跳过预检查
OFFLINE_MODE=false                       # 离线模式

# ============================================================================
# 颜色定义
# ============================================================================
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m'  # No Color
readonly BOLD='\033[1m'

# ============================================================================
# 日志函数
# ============================================================================
log() {
    local level=$1
    shift
    local message="$*"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo -e "${timestamp} [${level}] ${message}" | tee -a "$LOG_FILE"
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $*" | tee -a "$LOG_FILE"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" | tee -a "$LOG_FILE"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $*" | tee -a "$LOG_FILE"
}

log_step() {
    echo -e "\n${CYAN}${BOLD}==== $* ====${NC}" | tee -a "$LOG_FILE"
}

# 进度条函数
show_progress() {
    local pid=$1
    local message=$2
    local spin='-\|/'
    local i=0

    while kill -0 $pid 2>/dev/null; do
        i=$(( (i+1) %4 ))
        printf "\r${BLUE}[${spin:$i:1}]${NC} ${message}..."
        sleep 0.1
    done
    printf "\r${GREEN}[✓]${NC} ${message}... Done\n"
}

# ============================================================================
# 错误处理和清理
# ============================================================================
cleanup() {
 local exit_code=$?

    # 清理临时文件
    rm -f /tmp/kubeadm-*.yaml /tmp/kubeadm-*.yaml.bak 2>/dev/null || true

    if [ $exit_code -ne 0 ]; then
        log_error "脚本执行失败，退出码: $exit_code"
        log_warn "日志文件位置: $LOG_FILE"
        log_warn "如需回滚，请运行: kubeadm reset -f"
    fi
}

trap cleanup EXIT

# ============================================================================
# 帮助信息
# ============================================================================
show_help() {
    cat << EOF
${BOLD}Kubernetes 集群自动化部署脚本 v${SCRIPT_VERSION}${NC}

${BOLD}用法:${NC}
    $0 [选项]

${BOLD}模式选项:${NC}
    --mode <single|master|worker>    部署模式 (默认: single)
        single  - 单机模式（主节点可调度Pod）
        master  - 多机主节点模式
        worker  - 工作节点模式

${BOLD}基础配置:${NC}
    --version <version>              K8s版本 (默认: ${K8S_VERSION})
    --runtime <docker|containerd>    容器运行时 (默认: ${CONTAINER_RUNTIME})
    --network <flannel|calico|cilium> 网络插件 (默认: ${NETWORK_PLUGIN})
    --pod-cidr <cidr>                Pod网络CIDR (默认: ${POD_NETWORK_CIDR})
    --service-cidr <cidr>            Service网络CIDR (默认: ${SERVICE_CIDR})

${BOLD}主节点配置:${NC}
    --apiserver-advertise <ip>       API Server监听地址
    --control-plane-endpoint <addr>  控制平面端点（高可用）

${BOLD}工作节点配置:${NC}
    --master-ip <ip>                 主节点IP地址（worker模式必需）
    --token <token>                  加入令牌（worker模式必需）
    --hash <hash>                    CA证书哈希（worker模式必需）

${BOLD}高级选项:${NC}
    --image-repo <url>               镜像仓库地址
    --dry-run                        干运行模式（不执行实际操作）
    --skip-preflight                 跳过预检查
    --offline                        离线模式（使用本地镜像）
    --help, -h                       显示帮助信息

${BOLD}使用示例:${NC}
    # 单机部署
    $0 --mode single

    # 多机部署 - 主节点
    $0 --mode master --apiserver-advertise 192.168.1.100

    # 多机部署 - 工作节点
    $0 --mode worker --master-ip 192.168.1.100 \\
       --token abcdef.0123456789abcdef \\
       --hash sha256:1234567890abcdef...

    # 自定义配置
    $0 --mode single --version 1.35.0 --runtime containerd --network calico

${BOLD}更多信息:${NC}
    文档: https://kubernetes.io/docs/
    日志: $LOG_FILE

EOF
}

# ============================================================================
# 参数解析
# ============================================================================
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --mode)
                CLUSTER_MODE="$2"
                shift 2
                ;;
            --version)
                K8S_VERSION="$2"
                shift 2
                ;;
            --runtime)
                CONTAINER_RUNTIME="$2"
                shift 2
                ;;
            --network)
                NETWORK_PLUGIN="$2"
                shift 2
                ;;
            --pod-cidr)
                POD_NETWORK_CIDR="$2"
                shift 2
                ;;
            --service-cidr)
                SERVICE_CIDR="$2"
                shift 2
                ;;
            --apiserver-advertise)
                APISERVER_ADVERTISE_ADDRESS="$2"
                shift 2
                ;;
            --control-plane-endpoint)
                CONTROL_PLANE_ENDPOINT="$2"
                shift 2
                ;;
            --master-ip)
                MASTER_IP="$2"
                shift 2
                ;;
            --token)
                JOIN_TOKEN="$2"
                shift 2
                ;;
            --hash)
                JOIN_HASH="$2"
                shift 2
                ;;
            --image-repo)
                IMAGE_REPOSITORY="$2"
                shift 2
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --skip-preflight)
                SKIP_PREFLIGHT=true
                shift
                ;;
            --offline)
                OFFLINE_MODE=true
                shift
                ;;
            --help|-h)
                show_help
                exit 0
                ;;
            # 兼容旧版本参数
            --single)
                CLUSTER_MODE="single"
                shift
                ;;
            --multi)
                CLUSTER_MODE="master"
                if [[ -n "${2:-}" && ! "$2" =~ ^-- ]]; then
                    APISERVER_ADVERTISE_ADDRESS="$2"
                    shift
                fi
                shift
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# ============================================================================
# 参数验证
# ============================================================================
validate_arguments() {
    log_step "验证配置参数"

    # 验证集群模式
    if [[ ! "$CLUSTER_MODE" =~ ^(single|master|worker)$ ]]; then
        log_error "无效的集群模式: $CLUSTER_MODE"
        exit 1
    fi

    # Worker模式必需参数检查
    if [[ "$CLUSTER_MODE" == "worker" ]]; then
        if [[ -z "$MASTER_IP" ]]; then
            log_error "Worker模式必须指定 --master-ip"
            exit 1
        fi
        if [[ -z "$JOIN_TOKEN" ]] || [[ -z "$JOIN_HASH" ]]; then
            log_warn "未提供token或hash，将尝试自动获取（需要SSH访问主节点）"
        fi
    fi

    # 验证容器运行时
    if [[ ! "$CONTAINER_RUNTIME" =~ ^(docker|containerd)$ ]]; then
        log_error "不支持的容器运行时: $CONTAINER_RUNTIME"
        exit 1
    fi

    # 验证网络插件
    if [[ ! "$NETWORK_PLUGIN" =~ ^(flannel|calico|cilium)$ ]]; then
        log_error "不支持的网络插件: $NETWORK_PLUGIN"
        exit 1
    fi

    log_success "参数验证通过"
}

# ============================================================================
# 权限检查
# ============================================================================
check_privileges() {
    log_step "检查执行权限"

    if [[ $EUID -ne 0 ]]; then
        log_error "请使用root权限运行此脚本"
        log_info "提示: sudo $0 $*"
        exit 1
    fi

    log_success "权限检查通过"
}

# ============================================================================
# 环境检测
# ============================================================================
detect_environment() {
    log_step "检测系统环境"

    # 检测操作系统
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS=$ID
        OS_VERSION=$VERSION_ID

        case $OS in
            ubuntu|debian)
                OS="ubuntu"
                PKG_MANAGER="apt-get"
                ;;
            centos|rhel|rocky|almalinux)
                OS="centos"
                PKG_MANAGER="yum"
                ;;
            *)
                log_error "不支持的操作系统: $OS"
                log_info "仅支持 Ubuntu/Debian 和 CentOS/RHEL/Rocky/AlmaLinux"
                exit 1
                ;;
        esac
    else
        log_error "无法检测操作系统"
        exit 1
    fi

    log_info "操作系统: $OS $OS_VERSION"
    log_info "包管理器: $PKG_MANAGER"

    # 获取本机IP
    LOCAL_IP=$(hostname -I | awk '{print $1}')
    if [[ -z "$LOCAL_IP" ]]; then
        LOCAL_IP=$(ip route get 8.8.8.8 | awk -F"src " 'NR==1{split($2,a," ");print a[1]}')
    fi
    log_info "本机IP地址: $LOCAL_IP"

    # 如果未指定API Server地址，使用本机IP
    if [[ -z "$APISERVER_ADVERTISE_ADDRESS" ]] && [[ "$CLUSTER_MODE" != "worker" ]]; then
        APISERVER_ADVERTISE_ADDRESS="$LOCAL_IP"
        log_info "API Server地址设为: $APISERVER_ADVERTISE_ADDRESS"
    fi

    # 检测CPU核心数
    CPU_CORES=$(nproc)
    log_info "CPU核心数: $CPU_CORES"

    if [[ $CPU_CORES -lt 2 ]]; then
        log_warn "CPU核心数少于2，可能影响性能"
    fi

    # 检测内存大小
    TOTAL_MEM=$(free -m | awk 'NR==2{print $2}')
    log_info "总内存: ${TOTAL_MEM}MB"

    if [[ $TOTAL_MEM -lt 2048 ]]; then
        log_warn "内存小于2GB，可能影响稳定性"
    fi

    log_success "环境检测完成"
}

# ============================================================================
# 系统预检查
# ============================================================================
preflight_checks() {
    if [[ "$SKIP_PREFLIGHT" == true ]]; then
        log_warn "跳过预检查"
        return
    fi

    log_step "执行系统预检查"

    local checks_passed=true

    # 检查端口占用
    check_port() {
        local port=$1
        local desc=$2
        if ss -tuln | grep -q ":$port "; then
            log_warn "端口 $port ($desc) 已被占用"
            checks_passed=false
        fi
    }

    if [[ "$CLUSTER_MODE" != "worker" ]]; then
        check_port 6443 "API Server"
        check_port 2379 "etcd"
        check_port 2380 "etcd"
        check_port 10250 "Kubelet"
        check_port 10251 "kube-scheduler"
        check_port 10252 "kube-controller-manager"
    else
        check_port 10250 "Kubelet"
    fi

    # 检查必需的命令
    local required_commands=("curl" "wget" "systemctl")
    for cmd in "${required_commands[@]}"; do
        if ! command -v "$cmd" &> /dev/null; then
            log_error "未找到必需命令: $cmd"
            checks_passed=false
        fi
    done

    # 检查内核版本
    local kernel_version=$(uname -r | cut -d'-' -f1)
    local min_kernel="3.10"
    if [[ "$(printf '%s\n' "$min_kernel" "$kernel_version" | sort -V | head -n1)" != "$min_kernel" ]]; then
        log_warn "内核版本过低: $kernel_version (建议 >= $min_kernel)"
    fi

    if [[ "$checks_passed" == false ]]; then
        log_error "预检查未通过，请解决上述问题"
        read -p "是否继续? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    else
        log_success "预检查通过"
    fi
}

# ============================================================================
# 配置系统参数
# ============================================================================
configure_system() {
    log_step "配置系统参数"

    # 关闭swap
    log_info "关闭swap分区..."
    swapoff -a
    sed -i.bak '/ swap / s/^\(.*\)$/#\1/g' /etc/fstab
    log_success "已关闭swap分区"

    # 配置防火墙
    log_info "配置防火墙..."
    if systemctl is-active --quiet firewalld 2>/dev/null; then
        if [[ "$CLUSTER_MODE" != "worker" ]]; then
            # 主节点开放端口
            firewall-cmd --permanent --add-port=6443/tcp --add-port=2379-2380/tcp \
                --add-port=10250-10252/tcp --add-port=10255/tcp &>/dev/null || true
        fi
        # 所有节点开放端口
        firewall-cmd --permanent --add-port=10250/tcp --add-port=30000-32767/tcp &>/dev/null || true
        firewall-cmd --reload &>/dev/null || true
        log_success "已配置firewalld规则"
    elif systemctl is-active --quiet ufw 2>/dev/null; then
        ufw allow 6443/tcp &>/dev/null || true
        ufw allow 10250/tcp &>/dev/null || true
        ufw allow 30000:32767/tcp &>/dev/null || true
        log_success "已配置ufw规则"
    else
        log_info "未检测到防火墙服务"
    fi

    # 关闭SELinux
    if command -v getenforce &>/dev/null; then
        log_info "关闭SELinux..."
        setenforce 0 2>/dev/null || true
        sed -i.bak 's/^SELINUX=enforcing/SELINUX=disabled/g' /etc/selinux/config 2>/dev/null || true
        log_success "已关闭SELinux"
    fi

    # 配置内核参数
    log_info "配置内核参数..."
    cat > /etc/sysctl.d/k8s.conf << EOF
# Kubernetes required kernel parameters
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
net.ipv4.ip_forward = 1
vm.swappiness = 0
vm.overcommit_memory = 1
kernel.panic = 10
kernel.panic_on_oops = 1
net.ipv4.conf.all.route_localnet = 1
net.ipv4.conf.all.forwarding = 1
net.ipv6.conf.all.forwarding = 1
EOF

    sysctl --system > /dev/null 2>&1
    log_success "已加载内核参数"

    # 加载内核模块
    log_info "加载必需的内核模块..."
    cat > /etc/modules-load.d/k8s.conf << EOF
br_netfilter
overlay
ip_vs
ip_vs_rr
ip_vs_wrr
ip_vs_sh
nf_conntrack
EOF

    local modules=("br_netfilter" "overlay" "ip_vs" "ip_vs_rr" "ip_vs_wrr" "ip_vs_sh" "nf_conntrack")
    for module in "${modules[@]}"; do
        modprobe "$module" 2>/dev/null || log_warn "无法加载模块: $module"
    done
    log_success "已加载内核模块"

    # 配置时间同步
    log_info "配置时间同步..."
    if command -v timedatectl &>/dev/null; then
        timedatectl set-ntp true 2>/dev/null || true
        log_success "已启用NTP时间同步"
    fi

    log_success "系统参数配置完成"
}

# ============================================================================
# 安装容器运行时
# ============================================================================
install_container_runtime() {
    log_step "安装容器运行时: $CONTAINER_RUNTIME"

    case $CONTAINER_RUNTIME in
        docker)
            install_docker
            ;;
        containerd)
            install_containerd
            ;;
        *)
            log_error "不支持的容器运行时: $CONTAINER_RUNTIME"
            exit 1
            ;;
    esac
}

# 安装Docker
install_docker() {
    if command -v docker &>/dev/null; then
        local docker_version=$(docker --version | awk '{print $3}' | sed 's/,//')
        log_warn "Docker已安装 (版本: $docker_version)，跳过安装"
        return
    fi

    log_info "开始安装Docker..."

    if [[ "$OS" == "ubuntu" ]]; then
        # 安装依赖
        $PKG_MANAGER update -qq
        $PKG_MANAGER install -y apt-transport-https ca-certificates curl \
            software-properties-common gnupg lsb-release

        # 添加Docker GPG密钥和仓库
        curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
        echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] \
            https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
            tee /etc/apt/sources.list.d/docker.list > /dev/null

        # 安装Docker
        $PKG_MANAGER update -qq
        $PKG_MANAGER install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    elif [[ "$OS" == "centos" ]]; then
        # 安装依赖
        $PKG_MANAGER install -y yum-utils device-mapper-persistent-data lvm2

        # 添加Docker仓库
        yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

        # 安装Docker
        $PKG_MANAGER install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    fi

    # 配置Docker
    mkdir -p /etc/docker
    cat > /etc/docker/daemon.json << EOF
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.mirrors.ustc.edu.cn",
    "https://registry.docker-cn.com"
  ],
  "storage-driver": "overlay2",
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  },
  "live-restore": true,
  "max-concurrent-downloads": 10,
  "max-concurrent-uploads": 5,
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 64000,
      "Soft": 64000
    }
  }
}
EOF

    # 启动Docker
    systemctl daemon-reload
    systemctl enable docker
    systemctl start docker

    # 验证安装
    if docker info &>/dev/null; then
        log_success "Docker安装成功 ($(docker --version))"
    else
        log_error "Docker安装失败"
        exit 1
    fi
}

# 安装containerd
install_containerd() {
    if command -v containerd &>/dev/null; then
        local containerd_version=$(containerd --version | awk '{print $3}')
        log_warn "containerd已安装 (版本: $containerd_version)，跳过安装"

        # 确保配置正确
        configure_containerd
        return
    fi

    log_info "开始安装containerd..."

    if [[ "$OS" == "ubuntu" ]]; then
        $PKG_MANAGER update -qq
        $PKG_MANAGER install -y containerd

    elif [[ "$OS" == "centos" ]]; then
        $PKG_MANAGER install -y containerd.io
    fi

    configure_containerd

    # 验证安装
    if systemctl is-active --quiet containerd; then
        log_success "containerd安装成功 ($(containerd --version))"
    else
        log_error "containerd安装失败"
        exit 1
    fi
}

# 配置containerd
configure_containerd() {
    log_info "配置containerd..."

    # 创建配置目录
    mkdir -p /etc/containerd

    # 生成默认配置
    containerd config default > /etc/containerd/config.toml

    # 修改配置使用systemd cgroup driver
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/g' /etc/containerd/config.toml

    # 配置镜像加速
    if grep -q "registry.mirrors" /etc/containerd/config.toml; then
        sed -i '/\[plugins."io.containerd.grpc.v1.cri".registry.mirrors\]/a\        [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]\n          endpoint = ["https://docker.m.daocloud.io", "https://docker.mirrors.ustc.edu.cn"]' /etc/containerd/config.toml
    fi

    # 重启containerd
    systemctl daemon-reload
    systemctl enable containerd
    systemctl restart containerd

    log_success "containerd配置完成"
}

# ============================================================================
# 安装Kubernetes组件
# ============================================================================
install_k8s_components() {
    log_step "安装Kubernetes组件 (版本: $K8S_VERSION)"

    # 检查是否已安装
    if command -v kubeadm &>/dev/null && command -v kubelet &>/dev/null && command -v kubectl &>/dev/null; then
        local installed_version=$(kubelet --version | awk '{print $2}' | sed 's/v//')
        if [[ "$installed_version" == "$K8S_VERSION" ]]; then
            log_warn "Kubernetes组件已安装 (版本: $installed_version)，跳过安装"
            return
        else
            log_warn "已安装不同版本: $installed_version，将升级到: $K8S_VERSION"
        fi
    fi

    if [[ "$OS" == "ubuntu" ]]; then
        install_k8s_ubuntu
    elif [[ "$OS" == "centos" ]]; then
        install_k8s_centos
    fi

    # 启用kubelet
    systemctl enable kubelet

    log_success "Kubernetes组件安装完成"
}

# Ubuntu安装K8s
install_k8s_ubuntu() {
    log_info "在Ubuntu上安装Kubernetes组件..."

    # 安装依赖
    $PKG_MANAGER update -qq
    $PKG_MANAGER install -y apt-transport-https ca-certificates curl gpg

    # 添加Kubernetes GPG密钥
    mkdir -p /etc/apt/keyrings
    curl -fsSL https://pkgs.k8s.io/core:/stable:/v$(echo $K8S_VERSION | cut -d'.' -f1,2)/deb/Release.key | \
        gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

    # 添加Kubernetes仓库
    echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] \
        https://pkgs.k8s.io/core:/stable:/v$(echo $K8S_VERSION | cut -d'.' -f1,2)/deb/ /" | \
        tee /etc/apt/sources.list.d/kubernetes.list

    # 安装指定版本
    $PKG_MANAGER update -qq

    # 查找可用版本
    local version_suffix=$(apt-cache madison kubelet | grep "$K8S_VERSION" | head -1 | awk '{print $3}')
    if [[ -z "$version_suffix" ]]; then
        log_warn "未找到精确版本 $K8S_VERSION，将安装最新的 $(echo $K8S_VERSION | cut -d'.' -f1,2).x 版本"
        $PKG_MANAGER install -y kubelet kubeadm kubectl
    else
        $PKG_MANAGER install -y kubelet=$version_suffix kubeadm=$version_suffix kubectl=$version_suffix
    fi

    # 阻止自动更新
    apt-mark hold kubelet kubeadm kubectl

    log_success "Kubernetes组件安装完成"
}

# CentOS安装K8s
install_k8s_centos() {
    log_info "在CentOS上安装Kubernetes组件..."

    # 添加Kubernetes仓库
    cat <<EOF > /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://pkgs.k8s.io/core:/stable:/v$(echo $K8S_VERSION | cut -d'.' -f1,2)/rpm/
enabled=1
gpgcheck=1
gpgkey=https://pkgs.k8s.io/core:/stable:/v$(echo $K8S_VERSION | cut -d'.' -f1,2)/rpm/repodata/repomd.xml.key
EOF

    # 安装指定版本
    $PKG_MANAGER makecache

    # 查找可用版本
    if yum list --showduplicates kubelet | grep -q "$K8S_VERSION"; then
        $PKG_MANAGER install -y kubelet-$K8S_VERSION kubeadm-$K8S_VERSION kubectl-$K8S_VERSION
    else
        log_warn "未找到精确版本 $K8S_VERSION，将安装最新版本"
        $PKG_MANAGER install -y kubelet kubeadm kubectl
    fi

    log_success "Kubernetes组件安装完成"
}

# ============================================================================
# 初始化Kubernetes集群
# ============================================================================
init_kubernetes_cluster() {
    log_step "初始化Kubernetes集群"

    if [[ "$DRY_RUN" == true ]]; then
        log_info "干运行模式，跳过实际初始化"
        return
    fi

    # 安装yq工具
    install_yq

    # 设置镜像仓库
    setup_image_repositories

    # 生成正确的kubeadm配置
    local kubeadm_config_file
    kubeadm_config_file=$(generate_proper_kubeadm_config)

    # 验证配置文件语法
    if ! python3 -c "import yaml; yaml.safe_load(open('$kubeadm_config_file'))" 2>/dev/null; then
        log_error "配置文件语法错误"
        log_info "使用kubeadm内置配置生成..."
        kubeadm_config_file=$(generate_simple_kubeadm_config)
    fi

    # 拉取Kubernetes镜像
    log_info "拉取Kubernetes镜像..."
    if ! kubeadm config images pull --config="$kubeadm_config_file" 2>&1 | tee -a "$LOG_FILE"; then
        log_warn "镜像拉取失败，尝试使用备用镜像仓库"
        if ! try_alternative_image_repos "$kubeadm_config_file"; then
            log_error "镜像拉取完全失败"
            handle_image_pull_failure
            return 1
        fi
    fi

    # 初始化集群
    log_info "开始初始化集群..."

    local init_args=("--config" "$kubeadm_config_file")
    if [[ "$SKIP_PREFLIGHT" == true ]]; then
        init_args+=("--ignore-preflight-errors=all")
    fi

    local init_output
    if init_output=$(kubeadm init "${init_args[@]}" 2>&1 | tee -a "$LOG_FILE"); then
        log_success "集群初始化成功"
    else
        log_error "集群初始化失败"
        log_info "详细错误: $init_output"
        handle_init_failure
        return 1
    fi

    # 配置kubectl
    setup_kubectl_config

    # 单机模式：移除主节点的污点，允许调度Pod
    if [[ "$CLUSTER_MODE" == "single" ]]; then
        enable_master_scheduling
    fi

    # 清理临时文件
    rm -f "$kubeadm_config_file" "$kubeadm_config_file.bak" 2>/dev/null || true

    log_success "Kubernetes集群初始化完成"
}

# 简化配置生成（备用方案）
generate_simple_kubeadm_config() {
    local config_file="/tmp/kubeadm-simple-$(date +%s).yaml"

    cat > "$config_file" << EOF
apiVersion: kubeadm.k8s.io/v1beta3
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: ${APISERVER_ADVERTISE_ADDRESS}
  bindPort: 6443
nodeRegistration:
  criSocket: unix:///var/run/containerd/containerd.sock
---
apiVersion: kubeadm.k8s.io/v1beta3
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
controlPlaneEndpoint: "${CONTROL_PLANE_ENDPOINT:-${APISERVER_ADVERTISE_ADDRESS}:6443}"
networking:
  serviceSubnet: "${SERVICE_CIDR}"
  podSubnet: "${POD_NETWORK_CIDR}"
EOF

    echo "$config_file"
}

# 动态生成kubeadm配置
generate_kubeadm_config_dynamically() {
    local temp_file="/tmp/kubeadm-dynamic-$(date +%s).yaml"

    # 使用kubeadm打印默认配置作为基础
    kubeadm config print init-defaults > "$temp_file"

    # 修改关键配置项
    yq eval ".apiVersion = \"kubeadm.k8s.io/v1beta4\"" -i "$temp_file"
    yq eval ".kubernetesVersion = \"v${K8S_VERSION}\"" -i "$temp_file"
    yq eval ".controlPlaneEndpoint = \"${CONTROL_PLANE_ENDPOINT:-${APISERVER_ADVERTISE_ADDRESS}:6443}\"" -i "$temp_file"
    yq eval ".imageRepository = \"${IMAGE_REPOSITORY}\"" -i "$temp_file"
    yq eval ".networking.serviceSubnet = \"${SERVICE_CIDR}\"" -i "$temp_file"
    yq eval ".networking.podSubnet = \"${POD_NETWORK_CIDR}\"" -i "$temp_file"
    yq eval ".networking.dnsDomain = \"cluster.local\"" -i "$temp_file"

    echo "$temp_file"
}

# 设置kubectl配置
setup_kubectl_config() {
    mkdir -p $HOME/.kube
    if [[ -f /etc/kubernetes/admin.conf ]]; then
        cp -f /etc/kubernetes/admin.conf $HOME/.kube/config
        chown $(id -u):$(id -g) $HOME/.kube/config
        log_success "kubectl配置已设置"
    else
        log_warn "admin.conf文件不存在，kubectl配置可能需要手动设置"
    fi
}

# 启用主节点调度
enable_master_scheduling() {
    if kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null; then
        log_info "已移除控制平面污点（Kubernetes 1.24+）"
    elif kubectl taint nodes --all node-role.kubernetes.io/master- 2>/dev/null; then
        log_info "已移除主节点污点（Kubernetes 1.23及更早版本）"
    else
        log_warn "移除污点失败，主节点可能无法调度Pod"
    fi
}

# 处理初始化失败
handle_init_failure() {
    log_error "集群初始化失败，正在诊断问题..."

    # 检查系统日志
    log_info "检查kubelet日志:"
    journalctl -u kubelet --no-pager -l --lines=10 | tee -a "$LOG_FILE" || true

    # 检查容器运行时
    log_info "检查容器运行时状态:"
    systemctl status containerd --no-pager -l | tee -a "$LOG_FILE" || true

    # 检查网络连接
    log_info "检查网络连接:"
    ping -c 2 8.8.8.8 | tee -a "$LOG_FILE" || true

    log_info "建议的排查步骤:"
    log_info "1. 检查网络连接和DNS配置"
    log_info "2. 验证容器运行时状态: systemctl status containerd"
    log_info "3. 检查防火墙和SELinux设置"
    log_info "4. 查看详细日志: journalctl -u kubelet -f"
    log_info "5. 重置环境: kubeadm reset -f"

    read -p "是否立即重置集群？(y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        kubeadm reset -f
        log_info "集群已重置"
    fi
}

# 安装yq工具
install_yq() {
    if command -v yq &>/dev/null; then
        log_info "yq已安装: $(yq --version)"
        return 0
    fi

    log_info "安装yq工具..."

    local yq_version="v4.35.2"
    local yq_binary="yq_linux_amd64"

    # 下载yq
    if wget -q "https://github.com/mikefarah/yq/releases/download/${yq_version}/${yq_binary}" -O /usr/local/bin/yq; then
        chmod +x /usr/local/bin/yq
        log_success "yq安装成功"
    else
        # 备用安装方法
        if [[ "$OS" == "ubuntu" ]]; then
            sudo snap install yq || apt-get install -y yq
        elif [[ "$OS" == "centos" ]]; then
            yum install -y yq || dnf install -y yq
        fi
    fi

    # 验证安装
    if command -v yq &>/dev/null; then
        log_success "yq工具就绪: $(yq --version)"
    else
        log_error "yq安装失败，将使用备用配置方法"
        return 1
    fi
}

# kubeadm配置文件的API格式
generate_proper_kubeadm_config() {
    local config_file="/tmp/kubeadm-proper-$(date +%s).yaml"

    # 根据Kubernetes版本选择合适的API版本
    local major_version=$(echo $K8S_VERSION | cut -d'.' -f1)
    local minor_version=$(echo $K8S_VERSION | cut -d'.' -f2)
    local api_version="kubeadm.k8s.io/v1beta4"

    if [[ $major_version -eq 1 ]] && [[ $minor_version -lt 27 ]]; then
        api_version="kubeadm.k8s.io/v1beta3"
    fi

    log_info "生成kubeadm配置文件 (API: $api_version)"

    # 创建符合API规范的配置文件
    cat > "$config_file" << EOF
apiVersion: $api_version
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: ${APISERVER_ADVERTISE_ADDRESS}
  bindPort: 6443
nodeRegistration:
  criSocket: unix:///var/run/containerd/containerd.sock
  kubeletExtraArgs:
    cgroup-driver: systemd
---
apiVersion: $api_version
kind: ClusterConfiguration
kubernetesVersion: v${K8S_VERSION}
controlPlaneEndpoint: "${CONTROL_PLANE_ENDPOINT:-${APISERVER_ADVERTISE_ADDRESS}:6443}"
imageRepository: "${PRIMARY_IMAGE_REPO}"
networking:
  serviceSubnet: "${SERVICE_CIDR}"
  podSubnet: "${POD_NETWORK_CIDR}"
  dnsDomain: cluster.local
# 注意：extraArgs的正确格式是简单的key-value对，不是数组
apiServer:
  extraArgs:
    enable-admission-plugins: "NamespaceLifecycle,LimitRanger,ServiceAccount,DefaultStorageClass,ResourceQuota"
    allow-privileged: "true"
controllerManager:
  extraArgs:
    node-cidr-mask-size: "24"
    allocate-node-cidrs: "true"
scheduler:
  extraArgs:
    address: "0.0.0.0"
    port: "10251"
EOF

    echo "$config_file"
}

# 实现完整的镜像仓库重试逻辑
setup_image_repositories() {
    log_info "配置镜像仓库..."

    # 定义镜像仓库列表（按优先级排序）
    IMAGE_REPOSITORIES=(
        "registry.aliyuncs.com/google_containers"
        "registry.cn-hangzhou.aliyuncs.com/google_containers"
        "docker.io/google_containers"
        "k8s.gcr.io"
    )

    # 设置主仓库
    if [[ -n "$IMAGE_REPOSITORY" ]]; then
        PRIMARY_IMAGE_REPO="$IMAGE_REPOSITORY"
    else
        PRIMARY_IMAGE_REPO="${IMAGE_REPOSITORIES[0]}"
    fi

    # 设置备用仓库
    ALTERNATIVE_REPOS=()
    for repo in "${IMAGE_REPOSITORIES[@]}"; do
        if [[ "$repo" != "$PRIMARY_IMAGE_REPO" ]]; then
            ALTERNATIVE_REPOS+=("$repo")
        fi
    done

    log_info "主镜像仓库: $PRIMARY_IMAGE_REPO"
    log_info "备用仓库: ${ALTERNATIVE_REPOS[*]}"
}

handle_image_pull_failure() {
    log_error "镜像拉取失败处理"

    log_info "尝试手动拉取镜像..."

    # 定义需要拉取的镜像列表
    local images=(
        "kube-apiserver:v${K8S_VERSION}"
        "kube-controller-manager:v${K8S_VERSION}"
        "kube-scheduler:v${K8S_VERSION}"
        "kube-proxy:v${K8S_VERSION}"
        "pause:3.9"
        "etcd:3.5.12-0"
        "coredns:v1.11.1"  # 注意：这个版本需要根据K8s版本调整
    )

    for image in "${images[@]}"; do
        for repo in "${IMAGE_REPOSITORIES[@]}"; do
            local full_image="$repo/$image"
            log_info "尝试拉取: $full_image"
            if ctr images pull "$full_image" 2>/dev/null; then
                log_success "拉取成功: $full_image"
                break
            fi
        done
    done

    log_info "手动拉取完成，请重新运行初始化"
}

try_alternative_image_repos() {
    local config_file=$1
    local original_repo="$PRIMARY_IMAGE_REPO"

    log_warn "主仓库拉取失败，尝试备用仓库..."

    for repo in "${ALTERNATIVE_REPOS[@]}"; do
        log_info "尝试仓库: $repo"

        # 临时修改配置文件中的镜像仓库
        sed -i.bak "s|imageRepository:.*|imageRepository: \"$repo\"|" "$config_file"

        if kubeadm config images pull --config="$config_file" 2>/dev/null; then
            log_success "从备用仓库拉取成功: $repo"
            # 更新主仓库为成功的仓库
            PRIMARY_IMAGE_REPO="$repo"
            return 0
        fi

        # 恢复原始配置
        mv "$config_file.bak" "$config_file"
    done

    # 如果所有仓库都失败，尝试不使用自定义仓库
    log_warn "所有自定义仓库失败，尝试使用默认仓库"
    sed -i.bak "s|imageRepository:.*||" "$config_file"

    if kubeadm config images pull --config="$config_file" 2>/dev/null; then
        log_success "从默认仓库拉取成功"
        return 0
    fi

    # 恢复原始配置
    mv "$config_file.bak" "$config_file"
    log_error "所有镜像仓库尝试均失败"
    return 1
}

# ============================================================================
# 安装网络插件
# ============================================================================
install_network_addon() {
    log_step "安装网络插件: $NETWORK_PLUGIN"

    case $NETWORK_PLUGIN in
        flannel)
            install_flannel
            ;;
        calico)
            install_calico
            ;;
        cilium)
            install_cilium
            ;;
        *)
            log_error "不支持的网络插件: $NETWORK_PLUGIN"
            exit 1
            ;;
    esac
}

# 安装Flannel
install_flannel() {
    log_info "安装Flannel网络插件..."

    kubectl apply -f https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml

    # 等待网络插件就绪
    wait_for_pods "kube-system" "app=flannel"
    log_success "Flannel安装完成"
}

# 安装Calico
install_calico() {
    log_info "安装Calico网络插件..."

    kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/v3.26.1/manifests/tigera-operator.yaml
    kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/v3.26.1/manifests/custom-resources.yaml

    # 等待网络插件就绪
    wait_for_pods "calico-system"
    log_success "Calico安装完成"
}

# 安装Cilium
install_cilium() {
    log_info "安装Cilium网络插件..."

    curl -L --remote-name-all https://github.com/cilium/cilium-cli/releases/latest/download/cilium-linux-amd64.tar.gz
    sudo tar xzvfC cilium-linux-amd64.tar.gz /usr/local/bin

    cilium install

    # 等待网络插件就绪
    cilium status --wait
    log_success "Cilium安装完成"
}

# ============================================================================
# 工作节点加入集群
# ============================================================================
join_worker_node() {
    log_step "工作节点加入集群"

    if [[ -z "$JOIN_TOKEN" ]] || [[ -z "$JOIN_HASH" ]]; then
        log_error "加入集群需要token和hash参数"
        exit 1
    fi

    local join_args=(
        "$MASTER_IP:6443"
        "--token" "$JOIN_TOKEN"
        "--discovery-token-ca-cert-hash" "$JOIN_HASH"
    )

    if [[ "$SKIP_PREFLIGHT" == true ]]; then
        join_args+=("--skip-phases=preflight")
    fi

    kubeadm join "${join_args[@]}"

    log_success "工作节点加入集群完成"
}

# ============================================================================
# 工具函数
# ============================================================================

# 等待Pod就绪
wait_for_pods() {
    local namespace=$1
    local selector=${2:-""}
    local timeout=300
    local interval=5
    local elapsed=0

    log_info "等待 $namespace 命名空间下的Pod就绪..."

    while [[ $elapsed -lt $timeout ]]; do
        if kubectl get pods -n "$namespace" ${selector:+-l $selector} --field-selector=status.phase=Running | grep -q Running; then
            return 0
        fi
        sleep $interval
        elapsed=$((elapsed + interval))
        log_info "等待中... ${elapsed}/${timeout}秒"
    done

    log_error "Pod就绪超时"
    return 1
}

# ============================================================================
# 主执行流程
# ============================================================================
main() {
    log_step "开始Kubernetes集群部署"
    log_info "集群模式: $CLUSTER_MODE"
    log_info "Kubernetes版本: $K8S_VERSION"
    log_info "容器运行时: $CONTAINER_RUNTIME"
    log_info "网络插件: $NETWORK_PLUGIN"

    # 执行部署流程
    check_privileges
    parse_arguments "$@"
    validate_arguments
    detect_environment
    preflight_checks

    if [[ "$DRY_RUN" == true ]]; then
        log_info "干运行模式结束"
        return 0
    fi

    configure_system
    install_container_runtime

    if [[ "$CLUSTER_MODE" != "worker" ]]; then
        install_k8s_components
        init_kubernetes_cluster
        install_network_addon
        verify_cluster_status
    else
        install_k8s_components
        join_worker_node
    fi

    show_deployment_summary
}

# ============================================================================
# 验证集群状态
# ============================================================================
verify_cluster_status() {
    log_step "验证集群状态"

    log_info "检查节点状态..."
    kubectl get nodes

    log_info "检查系统Pod状态..."
    kubectl get pods -n kube-system

    log_info "集群信息:"
    kubectl cluster-info

    log_success "集群状态验证完成"
}

# ============================================================================
# 显示部署摘要
# ============================================================================
show_deployment_summary() {
    log_step "部署摘要"

    cat << EOF

${GREEN}🎉 Kubernetes集群部署成功！${NC}

${BOLD}部署信息:${NC}
  集群模式: $CLUSTER_MODE
  Kubernetes版本: $K8S_VERSION
  容器运行时: $CONTAINER_RUNTIME
  网络插件: $NETWORK_PLUGIN

${BOLD}访问配置:${NC}
  配置文件: $HOME/.kube/config
  使用命令: kubectl get nodes

EOF

    if [[ "$CLUSTER_MODE" != "worker" ]]; then
        # 显示加入集群命令
        local join_command=$(kubeadm token create --print-join-command 2>/dev/null || echo "无法生成加入命令")
        cat << EOF
${BOLD}工作节点加入命令:${NC}
  $join_command

${YELLOW}提示:${NC}
  1. 在工作节点上运行上述加入命令
  2. 使用 'kubectl get nodes' 查看节点状态

EOF
    fi

    log_info "日志文件: $LOG_FILE"
}

# ============================================================================
# 脚本入口点
# ============================================================================
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    # 设置信号处理
    trap 'log_error "脚本被用户中断"; exit 130' INT
    trap 'log_error "脚本终止"; exit 143' TERM

    # 执行主函数
    main "$@"

    # 记录完成时间
    log_info "脚本执行完成于: $(date)"
fi