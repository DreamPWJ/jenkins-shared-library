#!/usr/bin/env bash
# Author: 潘维吉
# Linux的crontab自动定时任务 自动化备份mysql数据库
# chmod +x mysql-auto-backup.sh　 给执行文件可执行权限
# 常量和环境变量名 全部大写，用下划线分隔，声明在文件的顶部
# 本地变量 全部小写 声明局部变量以确保其只在函数内部和子函数中可见。避免污染全局命名空间和不经意间设置可能具有函数之外重要性的变量
# 防止和系统环境变量冲突 ，变量命名规则 下划线分割 如果它是你的变量，小写它。如果你导出它，大写它，而${}这种方式引用，使引用变量更明确，以减少不必要的麻烦

HOST="127.0.0.1"                                         #数据库连接ip
DATABASE="test"                            #数据库名
USER_NAME="test"                           #数据库用户名
PASSWORD="panweiji2021!@#"                              #数据库密码
ADMINISTRATOR="406798106@qq.com"                         #管理员

date=$(date '+%Y%m%d-%H%M')                              #日期格式（作为文件名）
backup_dir=/my/backup                                    #备份文件存储路径
dump_file=${DATABASE}-${date}.sql                        #备份文件名(数据库名+备份时间)
mysql_options="-u${USER_NAME} -p${PASSWORD} ${DATABASE}" #备份参数组合

# 宿主机方式压缩备份
sudo mysqldump ${mysql_options} | gzip >${backup_dir}/${dump_file}.gz
# 宿主机方式不压缩备份
# sudo  mysqldump ${mysql_options} >${backup_dir}/${dump_file}
# 执行mysql备份和备份存储宿主位置 docker容器运行mysql的情况
# sudo docker exec mysql mysqldump ${mysql_options} | gzip >${backup_dir}/${dump_file}.gz

# 备份账号相关信息
# 只导出mysql库中的user，db，tables_priv表数据
sudo mysqldump -u${USER_NAME} -p${PASSWORD} mysql user db tables_priv -t --skip-extended-insert >${backup_dir}/user_info.sql
#sudo docker exec mysql mysqldump -u${USER_NAME} -p${PASSWORD} mysql user db tables_priv -t --skip-extended-insert >${backup_dir}/user_info.sql

# 删除7天之前的备份文件
find ${backup_dir} -name "*.sql.gz" -type f -mtime +7 -exec rm -rf {} \; >/dev/null 2>&1

# crontab -e
# MySQL数据库自动化定时备份
# 0 */1 * * * /bin/bash /my/backup/mysql-auto-backup.sh
# 0 0,12 * * * /bin/bash /my/backup/mysql-auto-backup.sh
# service cron restart # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出

# 新版的mysqldump默认启用了一个新标志  禁用统计--column-statistics=0

# 备份数据库: mysqldump -h localhost -u root -p123456 db_name > /my/dump.sql
# 还原数据库: mysql -h localhost -u root -p123456 db_name < /my/dump.sql
