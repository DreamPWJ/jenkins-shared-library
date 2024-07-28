#!/bin/bash
# Author: 潘维吉
# 数据库健康检查


# MySQL服务器配置
DB_HOST="localhost"
DB_USER="root"
DB_PASS="password"
DB_NAME="mydatabase"

# 检查数据库服务是否运行
if ! systemctl is-active mysql.service > /dev/null 2>&1; then
    echo "MySQL服务未运行"
    exit 1
fi

# 尝试连接数据库并执行简单查询
QUERY="SELECT 1"
if ! mysql -h $DB_HOST -u $DB_USER -p$DB_PASS -e "$QUERY" < /dev/null; then
    echo "无法连接到数据库"
    exit 1
fi

# 检查表空间
TABLESPACE_QUERY="SHOW TABLE STATUS WHERE Engine='InnoDB'"
tablespaces=$(mysql -h $DB_HOST -u $DB_USER -p$DB_PASS $DB_NAME -e "$TABLESPACE_QUERY")
if [[ $tablespaces == *"Data_free"* ]]; then
    echo "存在数据空间碎片"
    exit 1
fi

# 检查慢查询
SLOW_QUERY_LOG="/var/log/mysql/slowquery.log"
if [ -s $SLOW_QUERY_LOG ]; then
    echo "检测到慢查询"
    exit 1
fi

echo "数据库健康检查通过"
exit 0
