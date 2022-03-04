package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/4/13 13:22
 * @email 406798106@qq.com
 * @description 阿里云OSS相关
 */
class AliYunOss implements Serializable {

    /**
     *  上传OSS
     */
    static def upload(ctx, sourceFile, targetFile, ossBucket = "archive-artifacts", ossEndpoint = "oss-cn-beijing.aliyuncs.com") {
        def shellFile = "upload-aliyun-oss.sh"
        ctx.sh " cd ci/_linux/shell/oss/ && chmod +x ${shellFile} && ./${shellFile}" +
                " -a ${sourceFile} -b ${targetFile} -c ${ossBucket} -d ${ossEndpoint} "
        def ossUrl = "https://${ossBucket}.${ossEndpoint}/${targetFile}"
        return ossUrl
    }

    /**
     *  下载资源
     */
    static def download(ctx) {

    }
}
