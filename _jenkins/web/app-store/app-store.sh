#!/bin/sh

# Node环境变量
export PATH="$PATH:/usr/local/bin"
# Mac中的定时任务利器 launchctl

# node $HOME/AppStore/app-store-connect-api.js --appIdentifier='com.antai.propertyservice.store' --appVersion='1.0.0' --apiKeyId='RK28R5AN27' --privateKey="/Library/AuthKey_RK28R5AN27.p8" --issuerId='69a6de8d-bf99-47e3-e053-5b8c7c11a4d1' --phone='18863302302'
# node $HOME/AppStore/app-store-connect-api.js --appIdentifier='AnTaiKeji.com.saasMangerHaoYu' --appVersion='1.0.0' --apiKeyId='BNL32GR27G' --privateKey="/Library/AuthKey_BNL32GR27G.p8" --issuerId='a0cae47e-ca4b-4503-a967-4282302c0c5a' --phone='18863302302'

# 创建定时任务
# sudo crontab -e
# 每半个小时执行 0 0/30 * * * ?
# 0 0/30 * * * ? /bin/bash /my/app-store/app-store.sh >/my/crontab.log 2>&1
# service crond restart , Ubuntu 使用 sudo service cron start # 重启crond生效
# crontab -l # 查看crond列表
# GNU nano编辑器CTRL+O 再 CTRL+X 保存退出
