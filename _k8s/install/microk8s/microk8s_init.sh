#!/bin/bash

#================================================================
# MicroK8s 集群初始化脚本
# 支持: Ubuntu/Debian/CentOS/RHEL, 单机/集群模式, 多版本K8s
#================================================================

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
NC='\033[0m'

# 配置变量
MICROK8S_CHANNEL=${MICROK8S_CHANNEL:-"1.32/stable"}
CLUSTER_MODE=${CLUSTER_MODE:-"single"}
NODE_ROLE=${NODE_ROLE:-"master"}
ENABLE_HA=${ENABLE_HA:-"no"}
USE_CLASSIC=${USE_CLASSIC:-"yes"}
ADDONS=${ADDONS:-"dns storage ingress"}

# 超时配置
WAIT_TIMEOUT=${WAIT_TIMEOUT:-600}  # 等待就绪超时时间（秒）
RETRY_COUNT=${RETRY_COUNT:-3}      # 重试次数

# 日志函数
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

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

# 安装 snap (如果需要)
install_snap() {
    if ! command -v snap >/dev/null 2>&1; then
        log_step "安装 snapd..."

        case $OS in
            ubuntu|debian)
                apt-get update
                apt-get install -y snapd
                systemctl enable --now snapd.socket
                ;;
            centos|rhel)
                if [ "${OS_VERSION%%.*}" -ge 8 ]; then
                    yum install -y epel-release
                    yum install -y snapd
                    systemctl enable --now snapd.socket
                    ln -s /var/lib/snapd/snap /snap 2>/dev/null || true
                else
                    log_error "CentOS/RHEL 7 及更低版本不支持 snap"
                fi
                ;;
            fedora)
                dnf install -y snapd
                systemctl enable --now snapd.socket
                ln -s /var/lib/snapd/snap /snap 2>/dev/null || true
                ;;
            *)
                log_error "不支持的操作系统: $OS"
                ;;
        esac

        sleep 5
        log_info "snapd 安装完成"
    else
        log_info "snapd 已安装,跳过"
    fi
}

# 系统初始化配置
system_init() {
    log_step "系统初始化配置..."

    # 关闭 swap
    if [ "$(swapon --show | wc -l)" -gt 1 ]; then
        log_warn "检测到 swap 已启用"
        read -p "是否关闭 swap? (yes/no, 默认 yes): " CLOSE_SWAP
        CLOSE_SWAP=${CLOSE_SWAP:-yes}

        if [ "$CLOSE_SWAP" = "yes" ]; then
            swapoff -a
            sed -i '/swap/d' /etc/fstab
            log_info "swap 已关闭"
        fi
    fi

    # 配置防火墙规则
    log_info "配置防火墙规则..."
    PORTS=(16443 10250 10255 25000 12379 10257 10259 19001)

    if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
        log_info "配置 UFW 防火墙规则..."
        for port in "${PORTS[@]}"; do
            ufw allow $port/tcp 2>/dev/null || true
        done
        ufw allow 10250:10260/tcp 2>/dev/null || true
        ufw allow 4789/udp 2>/dev/null || true
    elif command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active --quiet firewalld; then
        log_info "配置 firewalld 规则..."
        for port in "${PORTS[@]}"; do
            firewall-cmd --permanent --add-port=${port}/tcp 2>/dev/null || true
        done
        firewall-cmd --permanent --add-port=10250-10260/tcp 2>/dev/null || true
        firewall-cmd --permanent --add-port=4789/udp 2>/dev/null || true
        firewall-cmd --reload 2>/dev/null || true
    else
        log_warn "未检测到防火墙或防火墙未启用,跳过配置"
    fi

    log_info "系统初始化完成"
}

# 等待 MicroK8s 服务完全就绪（增强版）
wait_for_microk8s_ready() {
    local max_wait=$WAIT_TIMEOUT
    local elapsed=0
    local interval=5

    log_info "等待 MicroK8s 服务完全启动..."

    # 步骤1: 等待基础服务启动
    while [ $elapsed -lt $max_wait ]; do
        if microk8s status --wait-ready --timeout 10 2>/dev/null; then
            log_info "MicroK8s 基础服务已启动"
            break
        fi
        sleep $interval
        elapsed=$((elapsed + interval))
        echo -n "."
    done
    echo ""

    if [ $elapsed -ge $max_wait ]; then
        log_error "MicroK8s 启动超时"
    fi

    # 步骤2: 等待 API Server 响应
    log_info "等待 API Server 就绪..."
    elapsed=0
    while [ $elapsed -lt 120 ]; do
        if microk8s kubectl get nodes >/dev/null 2>&1; then
            log_info "API Server 已就绪"
            break
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done

    # 步骤3: 等待节点就绪
    log_info "等待节点状态就绪..."
    elapsed=0
    while [ $elapsed -lt 120 ]; do
        NODE_STATUS=$(microk8s kubectl get nodes -o jsonpath='{.items[0].status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || echo "")
        if [ "$NODE_STATUS" = "True" ]; then
            log_info "节点已就绪"
            break
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done

    # 步骤4: 等待核心 Pods 运行
    log_info "等待核心系统 Pods 启动..."
    elapsed=0
    while [ $elapsed -lt 180 ]; do
        PENDING_PODS=$(microk8s kubectl get pods -n kube-system --field-selector=status.phase!=Running,status.phase!=Succeeded 2>/dev/null | grep -v NAME | wc -l)
        if [ "$PENDING_PODS" -eq 0 ]; then
            log_info "核心系统 Pods 已全部运行"
            break
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done

    # 最后确认
    sleep 10
    log_info "MicroK8s 完全就绪"
}

# 安装 MicroK8s
install_microk8s() {
    # 检查是否已安装
    if command -v microk8s >/dev/null 2>&1; then
        CURRENT_VERSION=$(microk8s version 2>/dev/null | grep MicroK8s | awk '{print $2}' || echo "unknown")
        log_info "检测到 MicroK8s 已安装,版本: $CURRENT_VERSION"

        read -p "是否重新安装? (yes/no, 默认 no): " REINSTALL
        REINSTALL=${REINSTALL:-no}

        if [ "$REINSTALL" != "yes" ]; then
            log_info "跳过安装,使用现有版本"
            return 0
        else
            log_info "卸载现有版本..."
            microk8s stop 2>/dev/null || true
            snap remove microk8s --purge 2>/dev/null || true
            sleep 5
        fi
    fi

    log_step "安装 MicroK8s (版本通道: $MICROK8S_CHANNEL)..."

    # 清理可能存在的旧数据
    rm -rf /var/snap/microk8s/common 2>/dev/null || true

    # 安装 MicroK8s
    if [ "$USE_CLASSIC" = "yes" ]; then
        snap install microk8s --classic --channel=$MICROK8S_CHANNEL
    else
        snap install microk8s --channel=$MICROK8S_CHANNEL
    fi

    log_info "MicroK8s 安装完成"

    # 启动并等待就绪（使用增强等待）
    log_info "启动 MicroK8s..."
    microk8s start 2>/dev/null || true

    wait_for_microk8s_ready

    # 验证安装
    log_info "验证安装..."
    if ! microk8s kubectl get nodes >/dev/null 2>&1; then
        log_error "MicroK8s 安装验证失败，请检查日志: journalctl -u snap.microk8s.daemon-kubelite -n 100"
    fi

    log_info "MicroK8s 安装验证成功"
}

# 配置用户权限
setup_user_permissions() {
    log_step "配置用户权限..."

    if [ -n "$SUDO_USER" ]; then
        usermod -a -G microk8s $SUDO_USER

        # 创建 .kube 目录
        mkdir -p /home/$SUDO_USER/.kube
        microk8s config > /home/$SUDO_USER/.kube/config 2>/dev/null || true
        chown -R $SUDO_USER:$SUDO_USER /home/$SUDO_USER/.kube

        log_info "已将用户 $SUDO_USER 添加到 microk8s 组"
        log_warn "请注销并重新登录以使组权限生效,或运行: su - $SUDO_USER"
    fi

    # 创建 kubectl 别名
    log_info "配置 kubectl 命令..."
    if [ -n "$SUDO_USER" ]; then
        grep -q "alias kubectl='microk8s kubectl'" /home/$SUDO_USER/.bashrc 2>/dev/null || \
            echo "alias kubectl='microk8s kubectl'" >> /home/$SUDO_USER/.bashrc
        grep -q "alias k='microk8s kubectl'" /home/$SUDO_USER/.bashrc 2>/dev/null || \
            echo "alias k='microk8s kubectl'" >> /home/$SUDO_USER/.bashrc
    fi

    grep -q "alias kubectl='microk8s kubectl'" ~/.bashrc 2>/dev/null || \
        echo "alias kubectl='microk8s kubectl'" >> ~/.bashrc
    grep -q "alias k='microk8s kubectl'" ~/.bashrc 2>/dev/null || \
        echo "alias k='microk8s kubectl'" >> ~/.bashrc

    log_info "已配置 kubectl 别名"
}

# 启用插件（带重试机制）
enable_addons() {
    log_step "启用 MicroK8s 插件..."

    if [ -z "$ADDONS" ]; then
        log_info "未指定要启用的插件"
        return 0
    fi

    log_info "启用插件: $ADDONS"

    for addon in $ADDONS; do
        local retry=0
        local success=false

        while [ $retry -lt $RETRY_COUNT ]; do
            log_info "启用插件: $addon (尝试 $((retry+1))/$RETRY_COUNT)"

            if microk8s enable $addon; then
                success=true
                log_info "插件 $addon 启用成功"
                break
            else
                log_warn "插件 $addon 启用失败，等待重试..."
                sleep 10
                retry=$((retry+1))
            fi
        done

        if [ "$success" = false ]; then
            log_error "插件 $addon 启用失败，已达最大重试次数"
        fi

        # 等待插件稳定
        sleep 5
    done

    log_info "等待所有插件就绪..."
    wait_for_microk8s_ready
    log_info "插件启用完成"
}

# 单机模式配置
setup_single_node() {
    log_step "配置单机模式..."
    log_info "单机模式配置完成"
}

# 集群主节点配置
setup_cluster_master() {
    log_step "配置集群主节点..."

    if [ "$ENABLE_HA" = "yes" ]; then
        log_info "启用高可用模式..."
        microk8s enable ha-cluster
        sleep 10
    fi

    # 生成加入令牌
    log_info "生成 Worker 节点加入令牌..."
    echo ""
    log_info "=========================================="
    log_info "Worker 节点加入集群命令:"
    log_info "=========================================="
    microk8s add-node
    echo ""
    log_info "=========================================="
    log_warn "注意: 令牌有效期为 1 小时"
    log_warn "如需生成新令牌,运行: microk8s add-node"
    log_info "=========================================="
}

# Worker 节点加入集群
join_cluster_worker() {
    log_step "Worker 节点加入集群..."

    echo ""
    log_info "请输入 Master 节点提供的 join 命令:"
    log_info "格式: microk8s join <master-ip>:25000/<token>"
    echo ""
    read -p "Join 命令: " JOIN_COMMAND

    if [ -z "$JOIN_COMMAND" ]; then
        log_error "Join 命令不能为空"
    fi

    log_info "执行加入集群..."
    eval $JOIN_COMMAND

    wait_for_microk8s_ready
    log_info "Worker 节点已成功加入集群"
}

# 显示集群信息
show_cluster_info() {
    log_step "集群信息..."

    echo ""
    log_info "=========================================="
    log_info "MicroK8s 集群状态"
    log_info "=========================================="
    microk8s status

    echo ""
    log_info "节点列表:"
    microk8s kubectl get nodes -o wide

    echo ""
    log_info "系统 Pods:"
    microk8s kubectl get pods -A

    echo ""
    log_info "=========================================="
    log_info "常用命令:"
    log_info "=========================================="
    echo "  查看状态: microk8s status"
    echo "  查看节点: microk8s kubectl get nodes"
    echo "  查看 Pods: microk8s kubectl get pods -A"
    echo "  查看日志: journalctl -u snap.microk8s.daemon-kubelite"
    echo "  启用插件: microk8s enable <addon>"
    echo "  禁用插件: microk8s disable <addon>"
    echo ""
    echo "  kubectl 别名 (需重新登录):"
    echo "  kubectl get nodes"
    echo "  k get pods -A"
    echo ""

    log_info "=========================================="
    log_info "故障排查命令:"
    log_info "=========================================="
    echo "  查看服务状态: microk8s inspect"
    echo "  查看系统日志: journalctl -u snap.microk8s.daemon-kubelite -f"
    echo "  重启服务: microk8s stop && microk8s start"
    echo "  重置集群: microk8s reset (危险操作)"
    echo ""
}

# 显示可用的版本通道
show_available_channels() {
    cat <<EOF
MicroK8s 可用版本通道:
========================================
最新版本:
  - latest/stable    (最新稳定版)
  - latest/candidate (最新候选版)

特定版本:
  - 1.32/stable      (Kubernetes 1.32)
  - 1.31/stable      (Kubernetes 1.31)
  - 1.30/stable      (Kubernetes 1.30)
  - 1.29/stable      (Kubernetes 1.29)
  - 1.28/stable      (Kubernetes 1.28)

查看完整列表: snap info microk8s
========================================
EOF
}

# 显示可用插件
show_available_addons() {
    cat <<EOF
MicroK8s 常用插件:
========================================
核心功能:
  dns              - CoreDNS
  storage          - 本地存储
  dashboard        - Kubernetes Dashboard
  ingress          - Nginx Ingress Controller
  metallb          - MetalLB 负载均衡器
  cert-manager     - 证书管理

监控和日志:
  prometheus       - Prometheus 监控
  metrics-server   - 资源指标
  observability    - 可观测性栈

其他:
  helm             - Helm 包管理器
  helm3            - Helm 3
  rbac             - RBAC 授权
  registry         - 容器镜像仓库
  gpu              - GPU 支持

查看完整列表: microk8s status
启用插件: microk8s enable <addon>
========================================
EOF
}

# 显示使用说明
show_usage() {
    cat <<EOF
使用说明:
========================================
环境变量:
  MICROK8S_CHANNEL - MicroK8s 版本通道 (默认: 1.32/stable)
  CLUSTER_MODE     - 集群模式 single/cluster (默认: single)
  NODE_ROLE        - 节点角色 master/worker (默认: master)
  ENABLE_HA        - 启用高可用 yes/no (默认: no)
  ADDONS           - 要启用的插件 (默认: "dns storage ingress")
  WAIT_TIMEOUT     - 等待超时时间秒 (默认: 600)
  RETRY_COUNT      - 插件启用重试次数 (默认: 3)

使用示例:
  sudo ./microk8s_init.sh
  sudo MICROK8S_CHANNEL=1.29/stable ./microk8s_init.sh
  sudo CLUSTER_MODE=cluster NODE_ROLE=master ./microk8s_init.sh

故障排查:
  如遇到 localnode.yaml 错误，脚本已优化等待逻辑
  查看详细日志: journalctl -u snap.microk8s.daemon-kubelite -n 200
========================================
EOF
}

# 主函数
main() {
    log_info "=== MicroK8s 集群初始化脚本 (修复增强版) ==="
    log_info "版本通道: $MICROK8S_CHANNEL | 模式: $CLUSTER_MODE | 角色: $NODE_ROLE"
    echo ""

    check_root
    detect_os
    install_snap
    system_init
    install_microk8s
    setup_user_permissions

    # 根据模式和角色执行不同操作
    if [ "$CLUSTER_MODE" = "single" ]; then
        setup_single_node
        enable_addons
    elif [ "$NODE_ROLE" = "master" ]; then
        enable_addons
        setup_cluster_master
    else
        join_cluster_worker
    fi

    show_cluster_info

    echo ""
    log_info "=========================================="
    log_info "MicroK8s 安装完成!"
    log_info "=========================================="
    echo ""

    if [ -n "$SUDO_USER" ]; then
        log_warn "请执行以下命令使权限生效:"
        echo "  su - $SUDO_USER"
        echo "  或注销并重新登录"
    fi
}

# 处理命令行参数
case "${1:-}" in
    -h|--help)
        show_usage
        exit 0
        ;;
    --list-channels)
        show_available_channels
        exit 0
        ;;
    --list-addons)
        show_available_addons
        exit 0
        ;;
    *)
        main
        ;;
esac