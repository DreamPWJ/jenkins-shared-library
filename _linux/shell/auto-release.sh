#!/bin/bash
# Author: 潘维吉
# Description: 执行自动化发布部署shell脚本 用于独立部署的项目或无外网的项目或大量的终端部署项目情况 主动触发升级部署

echo -e "\033[32m执行自动化部署Java语言脚本  👇 \033[0m"

#!/bin/bash

# 定义项目路径、Git仓库地址及分支
PROJECT_DIR="/opt/my-java-app"
GIT_REPO="https://github.com/user/repo.git"
GIT_BRANCH="develop"

# 拉取最新代码
cd "$PROJECT_DIR" || exit
git fetch origin "$GIT_BRANCH"
git checkout "$GIT_BRANCH"
git pull origin "$GIT_BRANCH"

# 使用Maven进行构建，包括清理、编译、测试与打包
MVN_HOME="/opt/mvn/bin"
$MVN_HOME/mvn clean install

# 停止当前运行的Java应用（假设是通过PID）
APP_PID=$(pgrep -f "java -jar my-app.jar")
if [[ -n "$APP_PID" ]]; then
    kill "$APP_PID"
fi

# 复制生成的新jar到部署目录，并赋予执行权限
DEPLOY_DIR="$PROJECT_DIR/deploy"
cp "$PROJECT_DIR/target/my-app.jar" "$DEPLOY_DIR/"
chmod +x "$DEPLOY_DIR/my-app.jar"

# 启动新的Java应用实例
nohup java -jar "$DEPLOY_DIR/my-app.jar" &>/dev/null &

echo "Java application upgraded successfully."

# （可选）清理旧版本和日志等
# rm -rf ...


# crontab -e
# 0 2 * * * /bin/bash /my/auto-release.sh
# service crond restart  , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表