#!/usr/bin/env bash
# Author: 潘维吉
# Description: 快速复制一个相同的数据库

# 源数据库信息
SOURCE_DB_HOST=ip
SOURCE_DB_USER=root
SOURCE_DB_PASSWORD=panweiji2019
SOURCE_DB_NAME=health_dev

# 目标数据库信息
TARGET_DB_HOST=ip
TARGET_DB_USER=root
TARGET_DB_PASSWORD=panweiji2019
TARGET_DB_NAME=health_test

createTableSql=""
insertDataSql=""

start_time=$(date +'%Y-%m-%d %H:%M:%S')

echo "复制数据库${SOURCE_DB_NAME}到${TARGET_DB_NAME}中...(可能需要一段时间)"
SOURCE_DB_CONNECT="-h ${SOURCE_DB_HOST} -u ${SOURCE_DB_USER} --password=${SOURCE_DB_PASSWORD}"
TARGET_DB_CONNECT="-h ${TARGET_DB_HOST} -u ${TARGET_DB_USER} --password=${TARGET_DB_PASSWORD}"

echo "DROP DATABASE IF EXISTS ${TARGET_DB_NAME}" | mysql ${TARGET_DB_CONNECT}
echo "CREATE DATABASE ${TARGET_DB_NAME}" | mysql ${TARGET_DB_CONNECT}

for TABLE in $(echo "SHOW TABLES" | mysql $SOURCE_DB_CONNECT $SOURCE_DB_NAME | tail -n +2); do
  createTable=$(echo "SHOW CREATE TABLE ${TABLE}" | mysql -B -r $SOURCE_DB_CONNECT $SOURCE_DB_NAME | tail -n +2 | cut -f 2-)
  createTableSql="${createTableSql} ; ${createTable}"
  insertData=" ${TARGET_DB_NAME}.${TABLE} SELECT * FROM ${SOURCE_DB_NAME}.${TABLE}"
  insertDataSql="${insertDataSql} ; ${insertData}"
done

echo "开始执行表创建"
echo "$createTableSql" | mysql $TARGET_DB_CONNECT $TARGET_DB_NAME
echo "开始执行表数据插入"
echo "$insertDataSql" | mysql $TARGET_DB_CONNECT $TARGET_DB_NAME

end_time=$(date +'%Y-%m-%d %H:%M:%S')
start_seconds=$(date --date="${start_time}" +%s)
end_seconds=$(date --date="${end_time}" +%s)
seconds=$((end_seconds - start_seconds + 1))
seconds_format=$(date -d@"${seconds}" -u +%Mmin%Ss) # 格式化时间显示
seconds_format=${seconds_format#"00min"}            # 去掉0分钟情况前缀

echo -e "\033[32m复制数据库${SOURCE_DB_NAME}到${TARGET_DB_NAME}完成 ✔ \033[0m"
echo "总耗时: ${seconds_format}"
