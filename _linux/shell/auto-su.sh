#!/usr/bin/env bash
# Author: 潘维吉
# Description:  自动切换用户 无需手动输入密码  自动化流程需要  chmod +x auto-su.sh

spawn su - root
expect "password:"
send "123456\r"
interact