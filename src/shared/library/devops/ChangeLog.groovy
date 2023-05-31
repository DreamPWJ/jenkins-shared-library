package shared.library.devops

import shared.library.GlobalVars
import shared.library.common.*
import shared.library.Utils
import jenkins.model.Jenkins

/**
 * @author 潘维吉
 * @date 2021/1/20 22:29
 * @email 406798106@qq.com
 * @description 自动生成变更日志
 */

@NonCPS
def genChangeLog(ctx, int maxRecordsNum = 100) {
    // Jenkins构建时只能获取变更日志 如果构建失败下次构建无法获取currentBuild变更日志
    // 思路: 可以在上次构建失败后获取到变更日志存储, 等下次构建发现无变更日志时候再获取, 获取成功后再删除日志存储
    try {
        ctx.println("开始生成自定义变更日志")
        def changeLogs = ""
        def featChangeLog = ""
        def fixChangeLog = ""
        def otherChangeLog = ""
        def changeLogSets = currentBuild.changeSets // 需要在方法前加上@NonCPS 需要对方法中的变量加上def定义
        for (int i = 0; i < changeLogSets.size(); i++) {
            def entries = changeLogSets[i].items //.unique() 数组去重
            def entriesLength = entries.length
            if (entriesLength > maxRecordsNum) {
                // 按照最新提交顺序截取
                entries = entries[(entriesLength - maxRecordsNum)..(entriesLength - 1)]
                entriesLength = maxRecordsNum  // 设置日志的最大记录数
            }
            for (int j = entriesLength - 1; j >= 0; j--) {
                def entry = entries[j]
                // ${new Date(entry.timestamp)  ${entry.affectedPaths}
                def truncatedMsg = entry.msg.take(60)  // 提交记录长度截取
                def combinationMsg = "- ${truncatedMsg} " + "([${entry.commitId.substring(0, 7)}](${ctx.REPO_URL.replace('.git', '')}/commit/${entry.commitId})) " + "@${entry.author.fullName} \n"
                // 提交记录信息去重
                if (!(featChangeLog.contains("${combinationMsg}") || fixChangeLog.contains("${combinationMsg}") || otherChangeLog.contains("${combinationMsg}"))) {
                    if (truncatedMsg.toString().startsWith(GlobalVars.gitCommitFeature)) {
                        featChangeLog += combinationMsg
                    } else if (truncatedMsg.toString().startsWith(GlobalVars.gitCommitFix)) {
                        fixChangeLog += combinationMsg
                    } else {
                        // 过滤无需生成的变更日志类型
                        // combinationMsg.contains("revert") || combinationMsg.contains("chore") || combinationMsg.contains("test")
                        if (combinationMsg.contains("docs(changelog)")) {
                            combinationMsg = ""
                        } else {
                            otherChangeLog += combinationMsg
                        }
                    }
                }
                changeLogs += combinationMsg
            }
        }
        if (!changeLogs) {
            // 获取Git某个时间段的提交记录 防止Jenkins日志失败构建导致为空
            changeLogs = getGitLogByTime(ctx, maxRecordsNum)  // @NonCPS方法无直接返回值
            if ("${changeLogs}".trim() == "") {
                changeLogs = GlobalVars.noChangeLog
            } else {
                changeLogs = changeLogs.toString().replaceAll("\\;", "\n")
            }
            println "${changeLogs}"
        } else {
            // 重新组合变更记录
            if (featChangeLog) {
                featChangeLog = "#### 新增功能 \n" + featChangeLog
            }
            if (fixChangeLog) {
                fixChangeLog = "#### 修复问题 \n" + fixChangeLog
            }
            if (otherChangeLog) {
                otherChangeLog = "#### 其它变更 \n" + otherChangeLog
            }
            changeLogs = featChangeLog + fixChangeLog + otherChangeLog
            // println "${changeLogs}"
        }
        return changeLogs
    } catch (e) {
        println "获取Git提交变更记录异常"
        println e.getMessage()
        return ""
    }
}

/**
 * 获取GIT某个时间段的提交记录，并且去除merge信息
 * --since 为时间戳或者日期格式
 */
@NonCPS
def getGitLogByTime(ctx, int maxRecordsNum = 100) {
    def gitLogs = ""
    try {
        // 如 git log --pretty=format:" %s @%an %cr (%H) ; " -n 10 --since='2023-03-28 15:55:00' --no-merges
        def jenkins = Jenkins.instance.getItem(ctx.env.JOB_NAME)
        def lsb = jenkins.getLastSuccessfulBuild()  // 上次成功的构建
        def lsbTime = lsb.getTime().format("yyyy-MM-dd HH:mm:ss")
        ctx.println("上次成功构建时间: " + lsbTime)
        gitLogs = Utils.getShEchoResult(ctx, "git log --pretty=format:\"- %s @%an ; \" -n ${maxRecordsNum}  --since='${lsbTime}' --no-merges")
        // 针对变更记录数组遍历可进行特殊化处理
        return gitLogs
    } catch (error) {
        ctx.println "获取GIT某个时间段的提交记录失败"
        ctx.println error.getMessage()
    }
    return gitLogs
}