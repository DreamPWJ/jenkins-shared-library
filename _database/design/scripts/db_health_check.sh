#!/bin/bash
# Author: 潘维吉
# 数据库健康状态检查并自我修复

# MySQL服务器配置
DB_SSH_IP="172.16.100.185" # 数据主机SSH IP
DB_HOST="localhost"  # 数据库连接地址 可能是负载均衡地址
DB_PORT="3306"
DB_USER="root"
DB_PASS="password"
DB_NAME="db_name"

# 检查数据库服务是否运行
#if ! systemctl is-active mysql.service > /dev/null 2>&1; then
#    echo "MySQL服务未运行"
#    systemctl start mysql.service
#    exit 1
#fi

# 尝试连接数据库并执行简单查询
QUERY="SELECT 1"
if ! mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS -e "$QUERY" < /dev/null; then
    echo "无法连接到数据库, 当前时间: $(date +'%Y-%m-%d %H:%M:%S')"
    ssh root$DB_SSH_IP ' systemctl restart mysql.service ' # 远程重启服务
    # systemctl restart mysql.service
    exit 1
fi

# 检查表空间
# TABLESPACE_QUERY="SHOW TABLE STATUS WHERE Engine='InnoDB'"
# tablespaces=$(mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASS $DB_NAME -e "$TABLESPACE_QUERY")
# if [[ $tablespaces == *"Data_free"* ]]; then
#     echo "存在数据空间碎片"
#     exit 1
# fi

# 检查慢查询
# SLOW_QUERY_LOG="/var/log/mysql/slowquery.log"
# if [ -s $SLOW_QUERY_LOG ]; then
#     echo "检测到慢查询"
#     exit 1
# fi

echo "数据库健康检查通过, 当前时间: $(date +'%Y-%m-%d %H:%M:%S')"

exit 0



# 执行授权  chmod +x /my/db_health_check.sh
# crontab -e
# */5 * * * * /bin/bash /my/db_health_check.sh >> /my/db-health-check.log 2>&1
# service crond restart  , Ubuntu 使用 sudo service cron restart # 重启crond生效
# crontab -l # 查看crond列表