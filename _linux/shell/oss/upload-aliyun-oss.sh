#!/usr/bin/env bash
# Author: 潘维吉
# Description:  Shell脚本方式上传阿里云OSS

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

# 根据阿里云OSS配置Key和秘钥 全性高和定制化的数据建议保存为Jenkins的“Secret file”类型的凭据并获取 无需放在代码中
#oss_key_id=""
#oss_key_secret=""
oss_host=$oss_bucket.$oss_endpoint
if [[ ${oss_key_id} == "" || ${oss_key_secret} == "" ]]; then
  echo -e "\033[31m请根据阿里云OSS配置Key或秘钥, 用于Shell脚本上传静态资源 ❌ \033[0m "
  exit 1
fi

resource="/${oss_bucket}/${target_file}"
content_type=$(file -ib ${source_file} | awk -F ";" '{print $1}')
date_value="$(TZ=GMT env LANG=en_US.UTF-8 date +'%a, %d %b %Y %H:%M:%S GMT')"
string_to_sign="PUT\n\n${content_type}\n${date_value}\n${resource}"
signature=$(echo -en $string_to_sign | openssl sha1 -hmac ${oss_key_secret} -binary | base64)

url=https://${oss_host}/${target_file}
echo "upload ${source_file} to ${url}"

curl -i -q -X PUT -T "${source_file}" \
  -H "Host: ${oss_host}" \
  -H "Date: ${date_value}" \
  -H "Content-Type: ${content_type}" \
  -H "charset: UTF-8" \
  -H "Authorization: OSS ${oss_key_id}:${signature}" \
  ${url}
