package shared.library.devops

import shared.library.GlobalVars
import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2021/1/20 22:29
 * @email 406798106@qq.com
 * @description 生成tag和变更日志
 */

def genTagAndLog(ctx, tagVersion, gitChangeLog, repoUrl, gitCredentialsId) {
    try {
        ctx.println("开始生成tag和变更日志并推送到远程仓库")
        withCredentials([usernamePassword(credentialsId: gitCredentialsId, usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
            script {
                env.ENCODED_GIT_PASSWORD = URLEncoder.encode(GIT_PASSWORD, "UTF-8")
            }
            def userPassWordUrl = " http://${GIT_USERNAME}:${ENCODED_GIT_PASSWORD}" +
                    "@${repoUrl.toString().replace("http://", "").replace("https://", "")} "
            sh("""
                   git config --global user.email "406798106@qq.com"
                   git config --global user.name ${GIT_USERNAME}
                   git pull ${userPassWordUrl}
                   """)

            try {
                // 打版本tag  删除本地已存在tag
                if (Utils.getShEchoResult(this, "git tag").toString().contains(tagVersion)) {
                    sh "git tag -d ${tagVersion}"
                    // 删除远程tag 重新设置相同的新tag
                    sh "git push ${userPassWordUrl} :refs/tags/${tagVersion}"
                    // 删除所有本地标签
                    //sh "git tag -l | xargs git tag -d"
                }
            } catch (e) {
                println "删除本地和远程已存在tag异常"
                println e.getMessage()
            }

            if ("${gitChangeLog}" != GlobalVars.noChangeLog) {
                // 同时自动生成CHANGELOG.md文件并上传git远程
                def changeLogFile = ""
                def changeLogFileName = "CHANGELOG.md"
                // 针对monorepo仓库情况 CHANGELOG.md可以考虑放在具体的目录下细化变更日志  针对前端、后端、小程序、Android、iOS等单仓多包情况
                // dir("${monoRepoProjectDir}") {
                if (fileExists("${changeLogFileName}")) {
                    changeLogFile = readFile(file: "${changeLogFileName}")
                }
                //if (!changeLogFile.contains(gitChangeLog)) {  // 重复变更日志和无新增和修复记录 补丁小版本记录 不自动生成发布日志(存在新版本未被记录的情况) 频繁发布情况可以考虑按天打tag和记录 防止tag数量杂乱
                writeFile file: "${changeLogFileName}", text: "## ${tagVersion}\n`${Utils.formatDate()}`<br><br>\n${gitChangeLog}\n${changeLogFile}"
                sh("""
                          git add ${changeLogFileName}
                          git commit ${changeLogFileName}  -m "docs(changelog): 发布 v${tagVersion}" 
                          git push ${userPassWordUrl}
                           """)

                // 设置git远程tag
                sh("""
                          git tag -a ${tagVersion} -m '${gitChangeLog}'
                          git push ${userPassWordUrl} ${tagVersion}
                           """)
                //}
                //}
            } else {
                try {
                    // Git获取得两个tag之间的提交记录作为变更日志
                    // gitChangeLog = sh(script: "git log --pretty=oneline 1.0.0..2.0.0", returnStatus: true)
                    // 获取不到jenkins变更记录的情况 git只打tag作为归档版本记录
                    sh("""
                          git tag -a ${tagVersion} -m 'v${tagVersion}'
                          git push ${userPassWordUrl} ${tagVersion}
                           """)
                } catch (e) {
                    println e.getMessage()
                }
            }
        }
    } catch (e) {
        println "捕获Git生成Tag和变更日志异常"
        println e.getMessage()
    }
}
