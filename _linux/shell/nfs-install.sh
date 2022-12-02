#!/usr/bin/env bash
# Author: 潘维吉
# Description: NFS分布式网络文件存储服务
# 参考文章: https://cloud.tencent.com/developer/article/1914388
# 对外分别开通NFS服务的tcp 111 2049 端口 udp 111 4046端口并确保IP白名单可访问NFS服务

cd /my

echo "创建部署一个NFS服务"

# Ubuntu部署
sudo apt install -y nfs-kernel-server || true

# CentOS部署
sudo yum install -y rpcbind nfs-util || true

# 创建要共享的目录
mkdir -p /my/nfs/data/
# 设置文件夹权限否则可能导致PVC创建失败  is waiting to start: ContainerCreating Pod直接无法启动了
chmod -R 777 /my/nfs/data/

# 编辑NFS配置并加入以下内容  允许访问NFS服务器的网段，也可以写 * ，表示所有地址都可以访问NFS服务和权限
# secure 选项要求mount客户端请求源端口小于1024  非法端口号可能导致挂载被拒绝  客户端访问端口大于1024添加insecure才能访问
sudo cat <<EOF >>/etc/exports
/my/nfs/data/ *(insecure,rw,sync,no_all_squash,no_subtree_check)
EOF

# 载入配置
exportfs -rv

echo "启动NFS服务"
# Ubuntu启动
sudo service nfs-server start || true
sudo service nfs-server status || true
# CentOS启动
#sudo systemctl restart rpcbind && systemctl enable rpcbind || true
#sudo systemctl restart nfs-server && systemctl enable nfs-server || true

sleep 1s

# 查看NFS配置是否生效
cat /var/lib/nfs/etab

# 查看服务器版本
# nfsstat -s

# 通过showmount命令查看NFS共享情况
# showmount -e localhost
# rpcinfo -p localhost

# 查看NFS服务日志
# cat /var/log/messages | grep nfs

# 重启NFS服务
# sudo service nfs-server restart

# ------------------------- 创建部署一个NFS客户端 -----------------------------------

# NFS客户端连接NFS服务共享文件夹
# sudo apt-get install -y nfs-common
# sudo yum install nfs-utils -y
# 创建一个用于nfs共享目录的挂载点
# sudo mkdir -p /mnt/nfs_shared_client
# 挂在共享目录到客户端
# sudo mount -t nfs NFSServerIP:/mnt/nfs_shared_server /mnt/nfs_shared_client
# umount -f /mnt/nfs_shared_client
sudo mount -t nfs 58.58.179.242:/mnt/stor1 /mnt/stor1