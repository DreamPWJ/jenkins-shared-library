#!/usr/bin/env bash
# Author: 潘维吉
# Description:  使用多线程rsync同步数据

# Define source, target, maxdepth and cd to source  同一机器
source="/nfsdata/ParkPicture/stor1/2023/"
target="/mnt/nfs_data/ParkPicture/stor1/2023/"
depth=3
cd "${source}"

# Set the maximum number of concurrent rsync threads
max_thread=5
# How long to wait before checking the number of rsync threads again
sleep_time=5

# Find all folders in the source directory within the maxdepth level
find . -maxdepth ${depth} -type d | while read dir
do
       # Make sure to ignore the parent folder
       if [ `echo "${dir}" | awk -F'/' '{print NF}'` -gt ${depth} ]
       then
           # Strip leading dot slash
           subfolder=$(echo "${dir}" | sed 's@^\./@@g')
           if [ ! -d "${target}/${subfolder}" ]
           then
               # Create destination folder
               mkdir -p "${target}/${subfolder}"
           fi
           # Make sure the number of rsync threads running is below the threshold
           while [ `ps -ef | grep -w [r]sync | awk '{print $NF}' | sort -nr | uniq | wc -l` -ge ${max_thread} ]
           do
               echo "Sleeping ${sleep_time} seconds"
               sleep ${sleep_time}
           done
           # Run rsync in background for the current subfolder and move one to the next one
           nohup rsync -avzP --bwlimit=5120 "root@119.188.90.222:${source}/${subfolder}/" "${target}/${subfolder}/" </dev/null >/dev/null 2>&1 &
       fi
done

# Find all files above the maxdepth level and rsync them as well
find . -maxdepth ${depth} -type f -print0 | rsync -avP --files-from=- --from0 ./ "${target}/"


# 执行脚本 chmod +x multi-thread-rsync.sh
# ./multi-thread-rsync.sh > /tmp/rsync.log 2>&1