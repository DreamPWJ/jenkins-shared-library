#!/usr/bin/env bash
# Author: 潘维吉
# Description: NFS分布式网络文件存储服务
# 参考文章: https://cloud.tencent.com/developer/article/1914388

cd /my

echo "创建部署一个NFS服务"

# Ubuntu部署
sudo apt install -y nfs-kernel-server || true

# CentOS部署
sudo yum install -y rpcbind nfs-util || true

# 创建要共享的目录
mkdir -p /my/nfs/data/
# 设置文件夹权限否则可能导致PVC创建失败
chmod -R 777 /my/nfs/data/

# 编辑NFS配置并加入以下内容  允许访问NFS服务器的网段，也可以写 * ，表示所有地址都可以访问NFS服务和权限
sudo cat <<EOF >/etc/exports
/my/nfs/data/ *(rw,sync,no_all_squash,no_subtree_check)
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

# 通过showmount命令查看NFS共享情况
# showmount -e NFS服务器IP


# ------------------------- 创建部署一个NFS客户端 -----------------------------------

# NFS客户端连接NFS服务共享文件夹
# sudo apt-get install -y nfs-common
# 创建一个用于nfs共享目录的挂载点
# sudo mkdir -p /mnt/nfs_shared_client
# 挂在共享目录到客户端
# sudo mount serverIP:/nfs_shared_server /mnt/nfs_shared_client