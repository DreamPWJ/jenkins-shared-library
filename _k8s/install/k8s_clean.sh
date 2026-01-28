#!/bin/bash

#================================================================
# Kubernetes 完全清理脚本
# 彻底清除 K8s 所有组件和配置,恢复到初始状态
#================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

# 检查 root 权限
if [ "$EUID" -ne 0 ]; then
    log_error "请使用 root 权限运行此脚本"
    exit 1
fi

echo "=========================================="
echo "  Kubernetes 完全清理工具"
echo "=========================================="
echo ""
log_warn "此脚本将完全清除 Kubernetes 集群和所有相关配置"
log_warn "请确认你真的要执行此操作!"
echo ""
read -p "是否继续? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    log_info "操作已取消"
    exit 0
fi

echo ""
log_info "开始清理 Kubernetes..."
echo ""

# 1. 重置 kubeadm
log_step "1/15: 重置 kubeadm..."
kubeadm reset -f 2>/dev/null || log_warn "kubeadm reset 失败或未安装"
apt-get remove  kubeadm || true
log_info "kubeadm 已重置"

# 2. 停止所有 K8s 服务
log_step "2/15: 停止 Kubernetes 相关服务..."
systemctl stop kubelet 2>/dev/null || true
systemctl stop containerd 2>/dev/null || true
systemctl stop docker 2>/dev/null || true
log_info "服务已停止"

# 3. 删除所有运行中的容器
log_step "3/15: 删除所有容器和Docker环境..."
# 是否确定提示
if command -v crictl >/dev/null 2>&1; then
    crictl --runtime-endpoint unix:///var/run/containerd/containerd.sock rm -af 2>/dev/null || true
    log_info "crictl 容器已删除"
    rm -rf /var/lib/containerd/* || true
    rm -rf /run/containerd/* || true
    rm -rf /etc/containerd/config.toml || true
    log_info "✓ containerd 数据已清理"
fi

if command -v docker >/dev/null 2>&1; then
    docker rm -f $(docker ps -aq) 2>/dev/null || true
    log_info "docker 容器已删除"
    log_info "完全卸载 Docker..."
    apt-get remove -y docker docker-engine docker.io containerd runc docker-ce docker-ce-cli containerd.io 2>/dev/null || true
    apt-get purge -y docker docker-engine docker.io containerd runc docker-ce docker-ce-cli containerd.io 2>/dev/null || true
    apt-get autoremove -y 2>/dev/null || true
    log_info "✓ Docker 已卸载"
    log_info "清理 Docker 遗留数据..."
    rm -rf /var/lib/docker || true
    rm -rf /var/lib/dockershim  || true
    rm -rf /etc/docker  || true
    rm -rf /var/run/docker.sock  || true
    rm -rf /var/run/docker  || true
    rm -rf ~/.docker  || true
    log_info "✓ Docker 数据已清理"
fi

# 4. 删除所有 Pod 和镜像
log_step "4/15: 删除所有 Pod..."
if command -v crictl >/dev/null 2>&1; then
    crictl --runtime-endpoint unix:///var/run/containerd/containerd.sock rmp -af 2>/dev/null || true
fi

# 5. 删除 K8s 配置文件
log_step "5/15: 删除 Kubernetes 配置文件..."
rm -rf /etc/kubernetes/ 2>/dev/null || true
rm -rf ~/.kube/ 2>/dev/null || true
rm -rf /root/.kube/ 2>/dev/null || true
rm -rf /home/*/.kube/ 2>/dev/null || true
log_info "配置文件已删除"

# 6. 删除 CNI 网络配置
log_step "6/15: 删除 CNI 网络配置..."
rm -rf /etc/cni/net.d/ 2>/dev/null || true
rm -rf /opt/cni/bin/ 2>/dev/null || true
log_info "CNI 配置已删除"

# 7. 删除 etcd 数据
log_step "7/15: 删除 etcd 数据..."
rm -rf /var/lib/etcd/ 2>/dev/null || true
log_info "etcd 数据已删除"

# 8. 删除 kubelet 数据
log_step "8/15: 删除 kubelet 数据..."
apt-get remove kubelet  kubectl || true
rm -rf /var/lib/kubelet/ 2>/dev/null || true
log_info "kubelet 数据已删除"

# 9. 删除容器运行时数据
log_step "9/15: 删除容器运行时数据..."
rm -rf /var/lib/containerd/ 2>/dev/null || true
rm -rf /var/lib/docker/ 2>/dev/null || true
log_info "容器运行时数据已删除"

# 10. 清理 iptables 规则
log_step "10/15: 清理 iptables 规则..."
iptables -F 2>/dev/null || true
iptables -X 2>/dev/null || true
iptables -t nat -F 2>/dev/null || true
iptables -t nat -X 2>/dev/null || true
iptables -t mangle -F 2>/dev/null || true
iptables -t mangle -X 2>/dev/null || true
iptables -P INPUT ACCEPT 2>/dev/null || true
iptables -P FORWARD ACCEPT 2>/dev/null || true
iptables -P OUTPUT ACCEPT 2>/dev/null || true

# IPv6
ip6tables -F 2>/dev/null || true
ip6tables -X 2>/dev/null || true
ip6tables -t nat -F 2>/dev/null || true
ip6tables -t nat -X 2>/dev/null || true
ip6tables -t mangle -F 2>/dev/null || true
ip6tables -t mangle -X 2>/dev/null || true
ip6tables -P INPUT ACCEPT 2>/dev/null || true
ip6tables -P FORWARD ACCEPT 2>/dev/null || true
ip6tables -P OUTPUT ACCEPT 2>/dev/null || true

log_info "iptables 规则已清理"

# 11. 删除虚拟网络接口
log_step "11/15: 删除虚拟网络接口..."
ifconfig cni0 down 2>/dev/null || true
ifconfig flannel.1 down 2>/dev/null || true
ifconfig docker0 down 2>/dev/null || true
ip link delete cni0 2>/dev/null || true
ip link delete flannel.1 2>/dev/null || true
ip link delete docker0 2>/dev/null || true
log_info "虚拟网络接口已删除"

# 12. 清理 IPVS 规则
log_step "12/15: 清理 IPVS 规则..."
if command -v ipvsadm >/dev/null 2>&1; then
    ipvsadm --clear 2>/dev/null || true
    log_info "IPVS 规则已清理"
fi

# 13. 删除临时配置文件
log_step "13/15: 删除密钥和临时配置文件..."
rm -f /etc/apt/keyrings/kubernetes-apt-keyring.gpg || true
rm -f /etc/apt/sources.list.d/kubernetes.list || true
rm -f /tmp/kubeadm-config*.yaml 2>/dev/null || true
log_info "临时文件已删除"

# 14. 清理系统配置文件
log_step "14/15: 清理系统配置文件..."
rm -f /etc/systemd/system/kubelet.service.d/*.conf 2>/dev/null || true
rm -rf /etc/systemd/system/kubelet.service.d/ 2>/dev/null || true
systemctl daemon-reload
log_info "系统配置已清理"

# 15. 重启容器运行时
log_step "15/15: 重启容器运行时..."
if systemctl list-unit-files | grep -q containerd; then
    systemctl start containerd 2>/dev/null || log_warn "containerd 启动失败"
    systemctl enable containerd 2>/dev/null || true

    if systemctl is-active --quiet containerd; then
        log_info "containerd 已重启"
    else
        log_warn "containerd 未能成功启动"
    fi
fi

echo ""
echo "=========================================="
log_info "清理完成!"
echo "=========================================="
echo ""

log_info "已清理的内容:"
echo "  ✓ kubeadm 配置已重置"
echo "  ✓ 所有容器已删除"
echo "  ✓ Kubernetes 配置文件已删除"
echo "  ✓ CNI 网络配置已删除"
echo "  ✓ etcd 数据已删除"
echo "  ✓ kubelet 数据已删除"
echo "  ✓ 容器运行时数据已删除"
echo "  ✓ iptables 规则已清理"
echo "  ✓ 虚拟网络接口已删除"
echo "  ✓ IPVS 规则已清理"
echo ""

log_info "系统状态:"
echo "  - containerd: $(systemctl is-active containerd 2>/dev/null || echo '未安装/未运行')"
echo "  - kubelet: $(systemctl is-active kubelet 2>/dev/null || echo '未运行(正常)')"
echo ""

log_info "现在可以重新安装 Kubernetes 了!"
echo ""
log_warn "建议操作:"
echo "1. 检查系统状态: systemctl status containerd"
echo "2. 重新初始化: kubeadm init --config=your-config.yaml"
echo ""