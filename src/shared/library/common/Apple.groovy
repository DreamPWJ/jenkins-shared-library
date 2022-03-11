package shared.library.common

import shared.library.Utils
import shared.library.common.*

/**
 * @author 潘维吉
 * @date 2021/9/26 13:22
 * @email 406798106@qq.com
 * @description 苹果平台相关 如iOS、MacOS平台
 */
class Apple implements Serializable {

    /**
     * 基于Fastlane做App Store审核上架
     */
    static def reviewOn(ctx) {
        ctx.sh "fastlane app_store ipa:${ctx.ipaPackagePath} " +
                "submit_for_review:${ctx.params.IS_AUTO_SUBMIT_FOR_REVIEW} automatic_release:${ctx.params.IS_AUTO_RELEASE_APP_STORE} " +
                "version_num:${ctx.params.APP_VERSION_NUM} release_note:'${ctx.params.APP_VERSION_DESCRIPTION}' ${ctx.fastlaneIosReviewInfo}"
    }

    /**
     * 自定义CocoaPods依赖和版本
     */
    static def initCocoaPods(ctx, version = "1.10.2") {
        ctx.sh "pod --version"
        ctx.sh "sudo gem install cocoapods -v " + version
        // sudo gem uninstall cocoapods  // 卸载
    }

    /**
     * 设置指定Cocoapods版本  多版本并存切换
     */
    static def setCocoaPodsVersion(ctx, version = "1.10.2") {
        def podVersion = Utils.getShEchoResult(ctx, "pod --version")
        if (podVersion != version) {
            ctx.sh "pod _" + version + "_ install"
            ctx.sh "pod --version"
        }
    }

    /**
     *  App Store提交审核后创建定时检测任务 审核状态的变化后通知
     */
    static def appStoreCheckState(ctx, map) {
        if (true) {  // 是否开启App Store审核状态的变化后通知
            try {
                def operationDir = Utils.getShEchoResult(ctx, "echo ${ctx.SYSTEM_HOME}") + "/AppStore"
                def plistFilePath = "/Library/LaunchDaemons/com.lexiangappstore.plist"
                def executeFileName = "app-store.sh"
                try {
                    // 授权注册加载定时任务
                    ctx.sh """
             launchctl unload -w ${plistFilePath} || true
             chown root:wheel ${plistFilePath} || true
             launchctl load -w ${plistFilePath}
            """
                } catch (e) {
                }
                try {
                    // 启动定时任务
                    ctx.sh """
             launchctl start ${plistFilePath}
            """
                } catch (e) {
                }
                // 定时任务shell内容
                def jobInfo = ctx.readFile(file: "${operationDir}/${executeFileName}")
                // 如果定时job不存在 再创建新的job信息
                if (!"${jobInfo}".contains("${ctx.iosAppIdentifier}")) {
                    ctx.dir(operationDir) {
                        try {
                            ctx.sh "yargs"
                        } catch (error) {
                            ctx.sh "npm i -D yargs"
                        }
                    }

                    // 去掉Shell文件中文件中的换行符
                    // cat test.sh|tr -s '\n'
                    /*   new_shell=$(cat test.sh|tr -s '\n') &&
                         cat <<EOF >test.sh
                         $new_shell
                         EOF */
                    /*   ctx.sh """ new_shell=\$(cat ${operationDir}/${executeFileName}|tr -s '\\n') &&
                            cat <<EOF >${operationDir}/${executeFileName}
                            ${new_shell}
                            EOF
                            """*/

                    // ruby ${operationDir}/spaceship.rb "${ctx.iosAppIdentifier}" "${map.apple_store_connect_api_key_id}" "${map.apple_store_connect_api_issuer_id}" "${map.apple_store_connect_api_key_file_path}" "${ctx.BUILD_USER_MOBILE}"
                    ctx.sh """
echo '
node ${operationDir}/app-store-connect-api.js --appIdentifier="${ctx.iosAppIdentifier}" --appVersion="${ctx.appInfoVersion}" --apiKeyId="${map.apple_store_connect_api_key_id}" --issuerId="${map.apple_store_connect_api_issuer_id}" --privateKey="${map.apple_store_connect_api_key_file_path}" --phone="${ctx.BUILD_USER_MOBILE}"
' >>${operationDir}/${executeFileName}
chmod +x ${operationDir}/${executeFileName}
       """

                    ctx.sh """
echo ' ' >${operationDir}/is_notice.txt
chmod +x ${operationDir}/is_notice.txt
rm -f ${operationDir}/run.log
       """
                }
            } catch (e) {
                ctx.println "App Store提交审核后创建检测任务异常"
                ctx.println e.getMessage()
            }
        }
    }

    /**
     *  设置iOS版本号 不从代码内获取情況
     */
    static def setVersion(ctx, version) {
        ctx.println("设置iOS版本号: " + version)
        try {
            ctx.sh "fastlane "
        } catch (e) {
            ctx.println(e.getMessage())
            ctx.error("手动设置iOS版本号失败 ❌")
        }
    }

    /**
     * 构建
     */
    static def build(ctx) {

    }

}
