package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/4/13 13:22
 * @email 406798106@qq.com
 * @description 阿里云OSS相关
 */
class AliYunOSS implements Serializable {

    /**
     *  上传OSS
     */
    static def upload(ctx, map, sourceFile, targetFile, ossBucket = "archive-artifacts", ossEndpoint = "oss-cn-beijing.aliyuncs.com") {
        // 安全性高和定制化的数据建议保存为Jenkins的“Secret file”类型的凭据并获取 无需放在代码中
        // JSON 结构体 { "AccessKeyId": "", "AccessKeySecret": "", "BucketName": "", "Endpoint": "" }
        ctx.withCredentials([ctx.file(credentialsId: "${map.oss_credentials_id}", variable: 'ALI_YUN_OSS')]) {
            // ctx.println("阿里云OSS访问配置：${ctx.ALI_YUN_OSS}")
            def ossData = ctx.readFile(file: "${ctx.ALI_YUN_OSS}")
            //ctx.println("JSON信息：${ossData}")
            def json = ctx.readJSON text: "${ossData}"
            def accessKeyId = json.AccessKeyId
            def accessKeySecret = json.AccessKeySecret
            def bucketName = json.BucketName
            def endpoint = json.Endpoint
            def shellFile = "upload-aliyun-oss.sh"
            ctx.sh " cd ci/_linux/shell/oss/ && chmod +x ${shellFile} && ./${shellFile}" +
                    " -a ${sourceFile} -b ${targetFile} -c ${bucketName} -d ${endpoint} -e ${accessKeyId} -f ${accessKeySecret} "
            def ossUrl = "https://${bucketName}.${endpoint}/${targetFile}"
            return ossUrl
        }
    }

    /**
     *  下载资源
     */
    static def download(ctx) {
        def shellFile = "download-aliyun-oss.sh"
    }
}
