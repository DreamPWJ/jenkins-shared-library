#!/bin/bash
# Author: 潘维吉
# Description:  下载阿里云OSS

host="oss-cn-shanghai.aliyuncs.com"
bucket="bucket名"
Id="AccessKey ID"
Key="Access Key Secret"

osshost=$bucket.$host

source="objecetename"
dest="localfilename"

resource="/${bucket}/${source}"
contentType=""
dateValue="`TZ=GMT env LANG=en_US.UTF-8 date +'%a, %d %b %Y %H:%M:%S GMT'`"
stringToSign="GET\n\n${contentType}\n${dateValue}\n${resource}"
signature=`echo -en $stringToSign | openssl sha1 -hmac ${Key} -binary | base64`

url=http://${osshost}/${source}
echo "download ${url} to ${dest}"

curl --create-dirs \
    -H "Host: ${osshost}" \
    -H "Date: ${dateValue}" \
    -H "Content-Type: ${contentType}" \
    -H "Authorization: OSS ${Id}:${signature}" \
    ${url} -o ${dest}
