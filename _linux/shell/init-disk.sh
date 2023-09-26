#!/bin/bash

# Author: 潘维吉
# Description:  初始化磁盘相当于Linux恢复出厂设置  先安装expect，yum -y install expect
# 参考文档: https://segmentfault.com/a/1190000021700495

# 执行 fdisk -l 命令查看磁盘信息
expect<<-EOF
spawn fdisk /dev/sdb    //启动进程并并跟踪
expect {
"获取帮助" {send "n\n";exp_continue}     //捕捉spawn的输出，匹配到期望字符，则向进程输入字符串
"default p" {send "p\n";exp_continue}    //n为新建分区，\n为回车，p为主分区，再回车
"默认 1" {send "1\n";exp_continue}          //分区号
"默认为 2048" {send "\n";exp_continue}      //分区大小
"+size" {send "\n"}
}
###当expect匹配到同样的字符串时的操作####
expect "获取帮助" {send "p\n";send "wq\n";exp_continue}   //p显示已建分区，wq保存配置，注意exp_continue，否则无法继续输入wq
EOF

mkdir /data    // 新建挂载目录
fdisk -l
mkfs.xfs /dev/sdb1   // 格式化
mount /dev/sdb1 /data    // 挂载使用
df -Th