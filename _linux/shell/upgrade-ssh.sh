#!/bin/bash
# Author: 潘维吉
# Description:  升级OpenSSH 版本  支持更高级特性 比如跳板机jump host -J 模式


compare_ssh_versions() {
  # 将传入的版本字符串转化为数组
  local -a version1=($(echo "$1" | tr '_' ' ' | awk '{print $2}'))
  local -a version2=($(echo "$2" | tr '_' ' ' | awk '{print $2}'))

  # 按点分隔将版本号分解为数组元素
  IFS='.' read -ra ver1 <<< "${version1[0]}"
  IFS='.' read -ra ver2 <<< "${version2[0]}"

  # 对比每个部分的整数值，直到找到不同或所有部分都相同为止
  for ((i=0; i<${#ver1[@]}; i++)); do
    if [[ ${ver1[i]} -gt ${ver2[i]} ]]; then
      return 0 # 如果第一个版本大于第二个，则返回真（0）
    elif [[ ${ver1[i]} -lt ${ver2[i]} ]]; then
      return 1 # 如果第一个版本小于第二个，则返回假（1）
    fi
  done

  # 如果所有部分都相等，则认为版本号相同
  return 0
}

# 获取当前系统ssh客户端版本
current_version=$(ssh -V | head -n1 | awk '{print $3}')

# 假设要比较的目标版本
target_version="OpenSSH_7.3p1"

# 比较版本号
if compare_ssh_versions "$current_version" "$target_version"; then
  echo "当前SSH版本高于或等于目标版本$target_version"
else
  echo "当前SSH版本低于目标版本$target_version"
  echo "OpenSSH版本低于7.3 版本, 无法支持跳板机 -J 模式, 执行自动升级OpenSSH版本"

  sudo yum upgrade -y openssh || true
  sudo yum upgrade -y openssh-clients || true
  sudo yum upgrade -y openssh-server || true

  sudo apt update -y || true
  sudo apt upgrade -y openssh-client || true
  sudo apt upgrade -y openssh-server || true

  ssh -V
fi