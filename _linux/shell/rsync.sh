#!/bin/bash
# Author: 潘维吉
# Description:  使用rsync工具迁移数据 全量+增量都支持  chmod +x rsync.sh

# 安装迁移工具 传输的双方都必须安装rsync  rsync是负责执行复制的工具  tmux是帮助查看进度的工具
sudo apt-get install -y rsync tmux
# sudo yum install -y rsync tmux


# 基本示例 -r表示递归，即包含子目录  source表示源目录 destination表示目标目录
rsync -r source destination

# 全量将远程内容同步到本地
rsync -avP username@remote_host:source/ destination

# 增量同步  rsync 的最大特点就是它可以完成增量备份，也就是默认只复制有变动的文件 rsync命令会先扫描源路径，所以即使增量数据不多，也可能需要较长的时间完成
# -delete参数删除只存在于目标目录、不存在于源目标的文件，即保证目标目录是源目标的镜像
rsync -avP --delete  /source/path /target/path