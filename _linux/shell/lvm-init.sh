#!/bin/bash
# Author: 潘维吉
# Description:  LVM（Logical Volume Manager）逻辑卷管理  初始化和无感在线扩容硬盘

# 查看磁盘分区情况
lsblk

# 安装
sudo apt-get install lvm2

# 分区磁盘 分别选m n p t(t代表LVM分区表 code设置8e) p w  (fdisk支持2TB大小内分区 新的空GPT分区表解决)
fdisk /dev/sdb

# 新建一个物理卷 pvdisplay 查看所有物理卷信息
pvcreate /dev/sdb1
pvdisplay

# 新建一个卷组 vgdisplay 查看所有卷组信息
vgcreate vg_data /dev/sdb1
vgdisplay

# 新建一个LVM逻辑卷  lvdisplay 查看所有逻辑卷信息 -L 参数小于存储容量
lvcreate -L 299G -n lv_data vg_data
lvdisplay

# 格式化分区
mkfs.xfs /dev/vg_data/lv_data -f
fdisk -l

# 挂载分区
mkdir /mnt/data
mount /dev/vg_data/lv_data /tidb-data

# 挂载永久生效  在 vim /etc/fstab内保存 重启等永久有效!!!
# /dev/mapper/vg_data-lv_data /tidb-data xfs defaults 0 0
vim /etc/fstab
systemctl daemon-reload

# 卸载分区
umount /dev/vg_data/lv_data


# 在线扩容硬盘  重新挂载新磁盘
lsblk
fdisk /dev/sdc # fdisk分区 分别选m n p t(t代表LVM分区表 code设置8e) p w
pvcreate /dev/sdc1 # 创建新物理卷
vgextend vg_data /dev/sdc1 # 扩展VG到新磁盘
lvextend -L +299G /dev/vg_data/lv_data # 扩容逻辑卷  -L 参数小于分区存储容量
xfs_growfs /dev/vg_data/lv_data # 扩容文件系统
df -h  # 查看扩容后分区大小
