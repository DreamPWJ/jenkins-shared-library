#!/bin/bash
# Author: 潘维吉
# Description:  使用rsync工具远程迁移数据 全量+增量同步都支持

# 安装迁移工具 传输的双方都必须安装rsync  rsync是负责执行复制的工具  tmux是帮助查看进度的工具
sudo apt-get install -y rsync tmux
# sudo yum install -y rsync tmux


# 基本示例 -r表示递归，即包含子目录  source表示源目录 target表示目标目录
rsync -r /source/ /target/

# 全量将远程内容同步到本地
rsync -avP root@119.188.90.222:/mnt/ /nfsdata/

# 增量同步  rsync 的最大特点就是它可以完成增量备份，也就是默认只复制有变动的文件 rsync命令会先扫描源路径，所以即使增量数据不多，也可能需要较长的时间完成
# -delete参数删除只存在于目标目录、不存在于源目标的文件，即保证目标目录是源目标的镜像
rsync -avP --delete  root@119.188.90.222:/mnt/ /nfsdata/

# 检查迁移结果  检查目标文件系统是否与源文件系统一致  如果两者数据一致，应该显示以下信息，中间不包含任何文件路径
sudo rsync -rvn /mnt/volumeA/ /mnt/volumeB/