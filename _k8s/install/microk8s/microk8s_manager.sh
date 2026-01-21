#!/bin/bash

#================================================================
# MicroK8s 集群管理工具
# 提供常用的集群管理功能
#================================================================

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# 检查 MicroK8s 是否安装
check_microk8s() {
    if ! command -v microk8s >/dev/null 2>&1; then
        log_error "MicroK8s 未安装"
    fi
}

# 显示状态
show_status() {
    echo "=========================================="
    echo "  MicroK8s 集群状态"
    echo "=========================================="
    echo ""
    microk8s status
    echo ""
    echo "节点信息:"
    microk8s kubectl get nodes -o wide
    echo ""
    echo "系统组件:"
    microk8s kubectl get pods -n kube-system
    echo ""
}

# 启用插件
enable_addon() {
    local addon=$1
    if [ -z "$addon" ]; then
        log_error "请指定要启用的插件名称"
    fi

    log_info "启用插件: $addon"
    microk8s enable $addon
    log_info "插件 $addon 已启用"
}

# 禁用插件
disable_addon() {
    local addon=$1
    if [ -z "$addon" ]; then
        log_error "请指定要禁用的插件名称"
    fi

    log_info "禁用插件: $addon"
    microk8s disable $addon
    log_info "插件 $addon 已禁用"
}

# 添加节点
add_node() {
    echo "=========================================="
    echo "  添加节点到集群"
    echo "=========================================="
    echo ""
    log_info "生成加入令牌..."
    microk8s add-node
    echo ""
    log_warn "令牌有效期: 1 小时"
    log_info "在新节点上运行显示的命令即可加入集群"
}

# 移除节点
remove_node() {
    local node=$1
    if [ -z "$node" ]; then
        echo "当前集群节点:"
        microk8s kubectl get nodes
        echo ""
        read -p "请输入要移除的节点名称: " node
    fi

    if [ -z "$node" ]; then
        log_error "节点名称不能为空"
    fi

    log_warn "移除节点: $node"
    microk8s remove-node $node
    log_info "节点已移除"
}

# 备份集群
backup_cluster() {
    local backup_dir=${1:-"/tmp/microk8s-backup-$(date +%Y%m%d-%H%M%S)"}

    log_info "备份集群到: $backup_dir"
    mkdir -p $backup_dir

    # 备份所有资源
    log_info "备份 Kubernetes 资源..."
    for ns in $(microk8s kubectl get ns -o jsonpath='{.items[*].metadata.name}'); do
        mkdir -p $backup_dir/$ns
        microk8s kubectl get all -n $ns -o yaml > $backup_dir/$ns/all-resources.yaml 2>/dev/null || true
        microk8s kubectl get configmap -n $ns -o yaml > $backup_dir/$ns/configmaps.yaml 2>/dev/null || true
        microk8s kubectl get secret -n $ns -o yaml > $backup_dir/$ns/secrets.yaml 2>/dev/null || true
    done

    # 备份 etcd (如果可能)
    log_info "尝试备份 etcd..."
    microk8s.etcdctl snapshot save $backup_dir/etcd-snapshot.db 2>/dev/null || log_warn "etcd 备份失败或不可用"

    log_info "备份完成: $backup_dir"
}

# 升级 MicroK8s
upgrade_microk8s() {
    local channel=$1

    if [ -z "$channel" ]; then
        echo "当前版本:"
        microk8s version
        echo ""
        echo "可用的版本通道:"
        echo "  latest/stable"
        echo "  1.31/stable"
        echo "  1.30/stable"
        echo "  1.29/stable"
        echo "  1.28/stable"
        echo ""
        read -p "请输入目标版本通道: " channel
    fi

    if [ -z "$channel" ]; then
        log_error "版本通道不能为空"
    fi

    log_warn "升级 MicroK8s 到: $channel"
    log_warn "这可能会导致服务短暂中断"
    read -p "是否继续? (yes/no): " confirm

    if [ "$confirm" != "yes" ]; then
        log_info "升级已取消"
        return
    fi

    log_info "开始升级..."
    snap refresh microk8s --channel=$channel

    log_info "等待服务就绪..."
    microk8s status --wait-ready

    log_info "升级完成!"
    microk8s version
}

# 查看日志
view_logs() {
    local component=$1

    if [ -z "$component" ]; then
        echo "可用的组件:"
        echo "  apiserver"
        echo "  controller-manager"
        echo "  scheduler"
        echo "  kubelet"
        echo "  containerd"
        echo ""
        read -p "请选择组件 (默认: apiserver): " component
        component=${component:-apiserver}
    fi

    log_info "查看 $component 日志..."
    microk8s kubectl logs -n kube-system -l component=$component --tail=100
}

# 重启 MicroK8s
restart_microk8s() {
    log_warn "重启 MicroK8s 服务..."
    read -p "是否继续? (yes/no): " confirm

    if [ "$confirm" != "yes" ]; then
        log_info "操作已取消"
        return
    fi

    microk8s stop
    sleep 5
    microk8s start

    log_info "等待服务就绪..."
    microk8s status --wait-ready

    log_info "MicroK8s 已重启"
}

# 获取 kubeconfig
get_kubeconfig() {
    local output_file=${1:-"$HOME/.kube/microk8s-config"}

    mkdir -p $(dirname $output_file)
    microk8s config > $output_file

    log_info "kubeconfig 已保存到: $output_file"
    echo ""
    log_info "使用方法:"
    echo "  export KUBECONFIG=$output_file"
    echo "  kubectl get nodes"
}

# 诊断问题
diagnose() {
    echo "=========================================="
    echo "  MicroK8s 诊断信息"
    echo "=========================================="
    echo ""

    echo "1. MicroK8s 版本:"
    microk8s version
    echo ""

    echo "2. 服务状态:"
    microk8s status
    echo ""

    echo "3. 节点状态:"
    microk8s kubectl get nodes -o wide
    echo ""

    echo "4. 系统 Pods 状态:"
    microk8s kubectl get pods -A
    echo ""

    echo "5. 检查问题 Pods:"
    microk8s kubectl get pods -A --field-selector=status.phase!=Running,status.phase!=Succeeded
    echo ""

    echo "6. 最近的事件:"
    microk8s kubectl get events -A --sort-by='.lastTimestamp' | tail -20
    echo ""

    echo "7. 资源使用情况:"
    microk8s kubectl top nodes 2>/dev/null || log_warn "metrics-server 未启用,无法显示资源使用"
    echo ""
}

# 显示帮助
show_help() {
    cat <<EOF
MicroK8s 集群管理工具
========================================

用法: $0 <command> [options]

命令:
  status              - 显示集群状态
  enable <addon>      - 启用插件
  disable <addon>     - 禁用插件
  add-node            - 添加节点到集群
  remove-node [name]  - 从集群移除节点
  backup [dir]        - 备份集群配置
  upgrade [channel]   - 升级 MicroK8s
  logs [component]    - 查看组件日志
  restart             - 重启 MicroK8s
  config [file]       - 获取 kubeconfig
  diagnose            - 诊断集群问题
  help                - 显示帮助信息

示例:
  $0 status
  $0 enable dashboard
  $0 add-node
  $0 backup /backup/k8s
  $0 upgrade 1.29/stable
  $0 logs apiserver
  $0 diagnose

快捷命令 (直接使用 microk8s):
  microk8s kubectl get nodes
  microk8s kubectl get pods -A
  microk8s enable dns storage ingress
  microk8s disable dashboard

========================================
EOF
}

# 主函数
main() {
    check_microk8s

    local command=${1:-help}
    shift || true

    case $command in
        status)
            show_status
            ;;
        enable)
            enable_addon "$@"
            ;;
        disable)
            disable_addon "$@"
            ;;
        add-node)
            add_node
            ;;
        remove-node)
            remove_node "$@"
            ;;
        backup)
            backup_cluster "$@"
            ;;
        upgrade)
            upgrade_microk8s "$@"
            ;;
        logs)
            view_logs "$@"
            ;;
        restart)
            restart_microk8s
            ;;
        config)
            get_kubeconfig "$@"
            ;;
        diagnose)
            diagnose
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "未知命令: $command (使用 'help' 查看帮助)"
            ;;
    esac
}

main "$@"