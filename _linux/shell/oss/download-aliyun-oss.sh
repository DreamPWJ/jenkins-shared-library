#!/usr/bin/env bash
# Author: 潘维吉
# Description:  下载阿里云OSS资源

echo "使用getopts的方式进行shell参数传递"
while getopts ":a:b:c:d:e:f:z:" opt; do
  case $opt in
  a)
    echo "source_file=$OPTARG"
    source_file=$OPTARG # 源文件
    ;;
  b)
    echo "target_file=$OPTARG"
    target_file=$OPTARG # 目标文件
    ;;
  c)
    echo "oss_bucket=$OPTARG"
    oss_bucket=$OPTARG # OSS桶名称
    ;;
  d)
    echo "oss_endpoint=$OPTARG"
    oss_endpoint=$OPTARG # OSS端点
    ;;
  e)
    echo "oss_key_id=$OPTARG"
    oss_key_id=$OPTARG # OSS的AccessKeyId
    ;;
  f)
    echo "oss_key_secret=$OPTARG"
    oss_key_secret=$OPTARG # OSS的AccessKeySecret
    ;;
  z) ;;

  ?)
    echo "未知参数"
    exit 1
    ;;
  esac
done

osshost=$oss_bucket.$oss_endpoint

resource="/${oss_bucket}/${source_file}"
contentType=""
dateValue="$(TZ=GMT env LANG=en_US.UTF-8 date +'%a, %d %b %Y %H:%M:%S GMT')"
stringToSign="GET\n\n${contentType}\n${dateValue}\n${resource}"
signature=$(echo -en $stringToSign | openssl sha1 -hmac ${oss_key_secret} -binary | base64)

url=http://${osshost}/${source_file}
echo "download ${url} to ${target_file}"

curl --create-dirs \
  -H "Host: ${osshost}" \
  -H "Date: ${dateValue}" \
  -H "Content-Type: ${contentType}" \
  -H "Authorization: OSS ${oss_key_id}:${signature}" \
  ${url} -o ${target_file}
