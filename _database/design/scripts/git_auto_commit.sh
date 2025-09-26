#!/usr/bin/env bash
# Author: 潘维吉
# Linux的crontab自动定时任务 Git自动化管理数据库版本DDL文件

# 配置你的 Git 仓库路径和文件路径
REPO_PATH="/path/to/your/repo"
FILE_PATH="path/to/your/file_or_folder" # 相对于 REPO_PATH 的路径

# 配置 Git 用户信息（可选）
GIT_USER_NAME="Your Name"
GIT_USER_EMAIL="you@example.com"

# 配置提交信息
COMMIT_MESSAGE="Automated commit at $(date '+%Y-%m-%d %H:%M:%S')"

# 配置远程仓库URL和用户名密码
REMOTE_REPO_URL="https://username:password@github.com/yourusername/yourrepo.git"

# 进入 Git 仓库目录
cd $REPO_PATH || exit

# 设置 Git 用户信息（可选）
git config user.name "$GIT_USER_NAME"
git config user.email "$GIT_USER_EMAIL"

# 添加文件到 Git
git add $FILE_PATH

# 提交更改
git commit -m "$COMMIT_MESSAGE"

# 设置远程仓库URL
git remote set-url origin $REMOTE_REPO_URL

# 推送到远程仓库
git push origin main # 更改为你的分支名称


# 确保你给这个脚本执行权限  
# chmod +x git_auto_commit.sh
# crontab -e
# MySQL数据库DDL自动化定时备份   每几个小时执行 0 */2 * * *
# 0 2 * * * /bin/bash /my/git_auto_commit.sh
# 0 0,12 * * * /bin/bash /my/git_auto_commit.sh
# service crond restart  , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出