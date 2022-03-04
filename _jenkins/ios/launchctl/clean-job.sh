#!/usr/bin/env bash
# Author: 潘维吉
# Description: 清除定时任务的具体Job   echo $PATH

# 同时支持多个并行Apple Store审核状态通知

string=$1
file_name=$2

# shell 获得字符串所在行数及位置
num=$(cat $file_name | grep -n $string | awk -F ":" '{print $1}')
# echo $num

# 删除第几行
sed -ig "${num}d" $file_name
