#!/bin/bash

#================================================================
# MicroK8s 完全清理脚本
#================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 检查 root 权限
if [ "$EUID" -ne 0 ]; then
    log_error "请使用 root 权限运行此脚本"
    exit 1
fi

echo "=========================================="
echo "  MicroK8s 完全清理工具"
echo "=========================================="
echo ""
log_warn "此脚本将完全卸载 MicroK8s 及其所有数据"
log_warn "包括: 所有工作负载、配置、存储数据"
echo ""
read -p "是否继续? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    log_info "操作已取消"
    exit 0
fi

echo ""
log_info "开始清理 MicroK8s..."
echo ""

# 1. 停止 MicroK8s
log_info "步骤 1/6: 停止 MicroK8s..."
if command -v microk8s >/dev/null 2>&1; then
    microk8s stop 2>/dev/null || true
    log_info "MicroK8s 已停止"
else
    log_warn "MicroK8s 未安装,跳过停止步骤"
fi

# 2. 重置 MicroK8s (如果支持)
log_info "步骤 2/6: 重置 MicroK8s..."
if command -v microk8s >/dev/null 2>&1; then
    microk8s reset 2>/dev/null || true
    log_info "MicroK8s 已重置"
fi

# 3. 卸载 MicroK8s snap
log_info "步骤 3/6: 卸载 MicroK8s snap 包..."
if snap list | grep -q microk8s; then
    snap remove microk8s --purge
    log_info "MicroK8s snap 已卸载"
else
    log_warn "MicroK8s snap 未安装"
fi

# 4. 清理残留数据
log_info "步骤 4/6: 清理残留数据..."
rm -rf /var/snap/microk8s 2>/dev/null || true
rm -rf ~/snap/microk8s 2>/dev/null || true
rm -rf /root/snap/microk8s 2>/dev/null || true
rm -rf /home/*/snap/microk8s 2>/dev/null || true
log_info "残留数据已清理"

# 5. 清理用户组
log_info "步骤 5/6: 清理用户组..."
if getent group microk8s >/dev/null 2>&1; then
    # 从组中移除所有用户
    for user in $(getent group microk8s | cut -d: -f4 | tr ',' ' '); do
        gpasswd -d $user microk8s 2>/dev/null || true
        log_info "已从 microk8s 组移除用户: $user"
    done
    groupdel microk8s 2>/dev/null || log_warn "无法删除 microk8s 组"
fi

# 6. 清理配置文件中的别名
log_info "步骤 6/6: 清理 shell 配置..."
for bashrc in /root/.bashrc /home/*/.bashrc; do
    if [ -f "$bashrc" ]; then
        sed -i "/alias kubectl='microk8s kubectl'/d" "$bashrc" 2>/dev/null || true
        sed -i "/alias k='microk8s kubectl'/d" "$bashrc" 2>/dev/null || true
    fi
done
log_info "shell 配置已清理"

echo ""
echo "=========================================="
log_info "清理完成!"
echo "=========================================="
echo ""
log_info "已清理的内容:"
echo "  ✓ MicroK8s 服务已停止"
echo "  ✓ MicroK8s snap 已卸载"
echo "  ✓ 所有数据和配置已删除"
echo "  ✓ 用户组已清理"
echo "  ✓ Shell 别名已清理"
echo ""
log_info "系统已恢复到安装 MicroK8s 之前的状态"
echo ""