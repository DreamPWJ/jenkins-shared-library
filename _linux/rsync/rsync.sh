#!/bin/bash
# Author: 潘维吉
# Description:  使用rsync工具远程迁移数据 全量+增量同步方式 也支持-P断点续传

# 安装迁移工具 传输的双方都必须安装rsync  rsync是负责执行复制的工具  tmux是帮助查看进度的工具
sudo apt-get install -y rsync
# sudo yum install -y rsync
rsync --version

# 基本示例 -r表示递归，即包含子目录  source表示源目录 target表示目标目录
rsync -r /source/ /target/

# 全量将远程内容同步到本地  --bwlimit=1000 限速单位KB/s  -P允许恢复中断的传输和显示进度  多线程只需在不同目录执行同步不同目录的rsync命令即可！！！
# nohup输入密码后按ctrl+z 中断进程 紧接着输入bg后台运行(需要实现ssh免密登录才不会中断) 退出执行exit保证任务后台正常运行！！！ tail -f nohup.out查看日志 不要删除日志断点续传用
# --include='*.txt' 将包括所有txt文件  --exclude='*.log' 将排除所有log文件   rsync命令中的源路径结尾必须带有/，否则同步后数据路径不能匹配
nohup rsync -avzP --bwlimit=5120 --include "1/" --exclude "/*"  root@119.188.90.222:/nfsdata/ParkPicture/stor1/2024/ /mnt/nfs_data/ParkPicture/stor1/2024/
nohup rsync -avzP --bwlimit=5120 --include "12/" --exclude "/*"  root@119.188.90.222:/nfsdata/ParkPicture/stor1/2023/ /mnt/nfs_data/ParkPicture/stor1/2023/
nohup rsync -avzP --bwlimit=5120  root@119.188.90.222:/nfsdata/ParkPicture/stor1/epark/ /mnt/nfs_data/ParkPicture/stor1/epark/
nohup rsync -avzP --bwlimit=5120  root@119.188.90.222:/nfsdata/ParkPicture/ocr/ /mnt/nfs_data/ParkPicture/ocr/
nohup rsync -avzP --bwlimit=5120  root@119.188.90.222:/nfsdata/ParkPicture/inspectorateImgs/ /mnt/nfs_data/ParkPicture/inspectorateImgs/
nohup rsync -avzP --bwlimit=5120  root@119.188.90.222:/nfsdata/ParkPicture/leagureImgs/ /mnt/nfs_data/ParkPicture/leagureImgs/

# 增量同步  rsync 的最大特点就是它可以完成增量备份，也就是默认只复制有变动的文件 rsync命令会先扫描源路径，所以即使增量数据不多，也可能需要较长的时间完成
# -delete参数删除只存在于目标目录、不存在于源目标的文件，即保证目标目录是源目标的镜像
rsync -avzP --delete  root@119.188.90.222:/nfsdata/ /mnt/nfs_data/

# 检查迁移结果  检查目标文件系统是否与源文件系统一致  如果两者数据一致，应该显示以下信息，中间不包含任何文件路径
sudo rsync -rvn /mnt/volumeA/ /mnt/volumeB/


# 启动rsync服务
rsync --daemon
# 停止rsync命令
kill $(cat /var/run/rsyncd.pid)


# 查看运行的rsync进程  kill -9 PID 停止rsync命令
ps -ef | grep rsync


# 多线程rsync同步  rsync命令中的源路径结尾必须带有/，否则同步后数据路径不能匹配
# 文档地址: https://www.opshub.cn/2023/11/16/rsync-duo-jin-cheng-bing-fa-chuan-shu-z6pmta.html
# 文档地址: https://help.aliyun.com/zh/nas/user-guide/migrate-data-by-using-the-rsync-command-line-tool
# 多线程rsync同步 -P线程数
ssh root@$A_ip "cd /$src_dir;find  . -type f -print0" | xargs -0 -I%  -P5 rsync -avuP  root@$A_ip:/$src_dir/% /$dest_dir/

