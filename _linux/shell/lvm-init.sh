#!/usr/bin/env bash
# Author: 潘维吉
# Description:  LVM（Logical Volume Manager）逻辑卷管理  初始化和无感在线扩容硬盘
# 超融合架构自带在线扩容  磁盘只能扩容不能缩容
# 如果没有提前设置LVM设置想扩容思路： 新增大硬盘做好LVM并挂载到临时目录 将数据复制到新存储  卸载旧磁盘再将新盘挂载到目标目录

# 查看磁盘分区情况
lsblk

# 安装
sudo apt-get install lvm2

# 第一次分LVM区设置 执行下面步骤
# 分区磁盘 分别选m n p t(t代表LVM分区表 code设置8e) p w  (fdisk支持2TB大小内分区 新的空GPT分区表解决)
fdisk /dev/sdb

# 新建一个物理卷 pvdisplay 查看所有物理卷信息
pvcreate /dev/sdb1
pvdisplay

# 新建一个卷组 vgdisplay 查看所有卷组信息
vgcreate vg_data /dev/sdb1
vgdisplay

# 新建一个LVM逻辑卷  lvdisplay 查看所有逻辑卷信息 -L 参数小于存储容量
lvcreate -L 99G -n lv_data vg_data
lvdisplay

# 格式化分区 注意会擦除数据！！！  执行 blkid 命令查看UUID和文件类型
mkfs.xfs /dev/vg_data/lv_data -f  # mkfs.ext4 /dev/vg_data/lv_data
fdisk -l

# 挂载分区
mkdir /mnt/data
mount /dev/vg_data/lv_data /var/lib/mysql

# 检查是否挂载成功
df -h

# 挂载永久生效  在 cat /etc/fstab 内保存 重启等永久有效!!!
vim /etc/fstab
# /dev/mapper/vg_data-lv_data /var/lib/mysql xfs defaults 0 1
# /dev/mapper/vg_data-lv_data /var/lib/mysql ext4 defaults 0 1
systemctl daemon-reload

# 卸载分区
umount /dev/vg_data/lv_data


# -------------------------- 已设置LVM重新在线扩容硬盘情况 -----------------------------
# 在线扩容硬盘  重新挂载新磁盘 从这直接开始  注意区分设置的vg和lv名称
lsblk
fdisk /dev/sdc # fdisk分区 分别选m n p t(t代表LVM分区表 code设置8e) p w
pvcreate /dev/sdc1 # 创建新物理卷
vgextend vg_data /dev/sdc1 # 扩展VG到新磁盘 将新盘添加到VG卷管理中  或ubuntu-vg

lvextend -L +2046G /dev/vg_data/lv_data # 扩容逻辑卷  -L 参数小于分区存储容量   系统盘默认设置了LVM扩容从这步开始
xfs_growfs /dev/vg_data/lv_data # xfs扩容文件系统 和 ext格式扩容 resize2fs /dev/ubuntu-vg/ubuntu-lv
df -h  # 查看扩容后分区大小
