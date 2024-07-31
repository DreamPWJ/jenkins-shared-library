#!/bin/bash
# Author: 潘维吉
# 自动化生成数据库版本DDL文件

# 数据库连接信息
DB_USER="your_username"
DB_PASS="your_password"
DB_NAME="your_database"

# 输出文件
OUTPUT_FILE="ddl.sql"

# 清空输出文件
> $OUTPUT_FILE

# 导出 DDL
mysql -u $DB_USER -p$DB_PASS -N -e "SELECT TABLE_NAME FROM information_schema.tables WHERE table_schema = '$DB_NAME';" | while read TABLE; do
    echo "SHOW CREATE TABLE $TABLE;" | mysql -u $DB_USER -p$DB_PASS $DB_NAME >> $OUTPUT_FILE
done

# 添加分割线
echo "------------------------" >> $OUTPUT_FILE

# 打印完成信息
echo "DDL exported to $OUTPUT_FILE"
