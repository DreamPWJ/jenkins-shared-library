#!/usr/bin/env bash
# Author: 潘维吉
# Description:  LVM（Logical Volume Manager）逻辑卷管理  初始化和无感在线扩容硬盘
# 超融合架构自带在线扩容  磁盘只能扩容不能缩容
# 如果没有提前设置LVM设置想扩容思路： 新增大硬盘做好LVM并挂载到临时目录 将数据复制到新存储  卸载旧磁盘再将新盘挂载到目标目录

# 使用root用户执行
sudo -i
# 查看磁盘分区情况
lsblk

# 安装
sudo apt-get install lvm2
sudo yum install lvm2

# 第一次分LVM区设置 执行下面步骤
# 创建新空间分区 分别选m n p (Select可用默认) t(t代表LVM分区表 code设置8e) p w  (fdisk支持2TB大小内分区 新的空GPT分区表解决)
fdisk /dev/sdb  # 物理sda 和 虚拟vda   根据lsblk的外层名称
# 新建一个物理卷 pvdisplay 查看所有物理卷信息
pvcreate /dev/sdb1
pvdisplay

# 新建一个卷组 vgdisplay 查看所有卷组信息
vgcreate data-vg /dev/sdb1
vgdisplay

# 新建一个LVM逻辑卷  lvdisplay 查看所有逻辑卷信息 -L 参数小于存储容量
lvcreate -L 239G -n data-lv data-vg
lvdisplay

# 格式化分区 注意会擦除数据！！！
mkfs.xfs /dev/data-vg/data-lv -f  # mkfs.ext4 /dev/data-vg/data-lv
fdisk -l

# 挂载分区
mkdir /mnt/data
mount /dev/data-vg/data-lv /mnt/data

# 检查是否挂载成功
df -h

# 挂载永久生效  在 cat /etc/fstab 内保存 重启等永久有效!!!
vim /etc/fstab
# /dev/mapper/data-vg-data-lv /mnt/data xfs defaults 0 1
# /dev/mapper/data-vg-data-lv /mnt/data ext4 defaults 0 1
# 执行 blkid 命令查看UUID和文件类型
# UUID= /data xfs defaults 0 1
systemctl daemon-reload

# 卸载分区
umount /dev/data-vg/data-lv


# -------------------------- 已设置LVM重新在线扩容硬盘情况  -----------------------------
# 在线扩容硬盘  重新挂载新磁盘 从这直接开始  注意区分设置的vg和lv名称
lsblk
fdisk /dev/sdc # fdisk分区 根据fdisk -l显示SIZE的NAME名称 分别选m n p t(t代表LVM分区表 code设置8e) p w
pvcreate /dev/sdc1 # 创建新物理卷
vgextend data-vg /dev/sdc1 # 扩展VG到新磁盘 将新盘添加到VG卷管理中  或ubuntu-vg

# 新系统已设置LVM  跳过所有上面步骤 执行下面步骤！！！
lvextend -L +2046G /dev/data-vg/data-lv # 扩容逻辑卷  -L 参数小于分区存储容量   系统盘默认设置了LVM扩容从这步开始
xfs_growfs /dev/data-vg/data-lv # xfs扩容文件系统 和 ext格式扩容 resize2fs /dev/ubuntu-vg/ubuntu-lv
df -h  # 查看扩容后分区大小


# -------------------------- 新系统安装的Ubuntu初始化设置LVM  -----------------------------
# 创建新的逻辑卷（LV 使用卷组（ubuntu-vg）中的全部剩余空间创建一个名为 data-lv的新逻辑卷
sudo -i
sudo vgdisplay
sudo lvcreate -l 100%FREE -n data-lv  ubuntu-vg # 创建逻辑卷
#sudo lvextend -l +100%FREE /dev/ubuntu-vg/ubuntu-lv # 扩展逻辑卷 已有逻辑卷有Free PE/Size空间
sudo mkfs.xfs /dev/ubuntu-vg/data-lv    # xfs格式化逻辑卷
sudo resize2fs /dev/ubuntu-vg/data-lv   # ext4格式化逻辑卷

# 临时挂载
sudo mkdir /data
sudo mount /dev/ubuntu-vg/data-lv /data
#为了避免服务器重启后需要手动重新挂载，需要将挂载信息写入 /etc/fstab文件 ！！！