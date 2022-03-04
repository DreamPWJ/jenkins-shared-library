#!/usr/bin/env bash
# 内存使用情况

mem_use_info=($(awk '/MemTotal/{memtotal=$2}/MemAvailable/{memavailable=$2}END{printf "%.2f %.2f %.2f",memtotal/1024/1024," "(memtotal-memavailable)/1024/1024," "(memtotal-memavailable)/memtotal*100}' /proc/meminfo))

echo total:${mem_use_info[0]}G used:${mem_use_info[1]}G Usage:${mem_use_info[2]}%
