package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/2/24 16:29
 * @email 406798106@qq.com
 * @description 分发平台上传  如蒲公英、Firebase、Fir、自建分发OSS等平台
 */
class DistributionPlatform implements Serializable {

    /**
     *  上传自建OSS分发 不依赖受限第三方分发平台
     */
    static def uploadOss(ctx, appName, filePosition) {
        ctx.println("上传自建OSS分发 🚀")
        def sourceFile = "${ctx.env.WORKSPACE}/${filePosition}/${appName}" // 源文件
        def targetFile = "${ctx.SYSTEM_TYPE_NAME.toLowerCase()}/${ctx.env.JOB_NAME}/${appName}" // 目标文件
        def packageOssUrl = AliYunOss.upload(ctx, sourceFile, targetFile)
        return packageOssUrl
    }

    /**
     *  上传蒲公英
     *  文档地址 https://www.pgyer.com/doc/view/api#uploadApp
     */
    static def uploadPgyer(ctx, appName, filePosition, description, apiKey) {
        if ("${ctx.IS_ANDROID_AAB}" == 'true') {
            throw new Exception("Android aab新格式 蒲公英分发平台暂时不支持 抛出异常后会上传到自建OSS上")
        } else {
            ctx.println("上传蒲公英分发平台 🚀")
            def uploadResult = ctx.sh(returnStdout: true, encoding: 'UTF-8',
                    script: """curl --connect-timeout 60 --max-time 150 -F 'file=@${filePosition}/${appName}' \
                                             -F '_api_key=${apiKey}' -F 'buildInstallType=1' -F 'buildInstallDate=2' \
                                             -F 'buildUpdateDescription=${description}' \
                                             https://www.pgyer.com/apiv2/app/upload """)
            return uploadResult
        }
    }

    /**
     *  上传Firebase
     *  文档地址 https://firebase.google.com/docs/app-distribution
     */
    static def uploadFirebase(ctx, filePath, changeLog) {
        ctx.println("上传Firebase分发平台 🚀")
        ctx.sh("fastlane firebase file_path:${filePath} changelog:'${changeLog}'")
    }

    /**
     *  上传Fir
     *  文档地址 https://github.com/FIRHQ/fastlane-plugin-fir_cli
     */
    static def uploadFir(ctx, filePath, changeLog) {
        ctx.println("上传Fir分发平台 🚀")
        ctx.sh("fastlane fir file_path:${filePath} changelog:'${changeLog}'")
    }

}
