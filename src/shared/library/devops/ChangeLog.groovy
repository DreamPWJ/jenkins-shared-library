package shared.library.devops

import shared.library.GlobalVars

/**
 * @author 潘维吉
 * @date 2021/1/20 22:29
 * @email 406798106@qq.com
 * @description 生成变更日志
 */

@NonCPS
def genChangeLog(ctx, int maxRecordsNum = 100) {
    try {
        ctx.println("开始生成自定义变更日志")
        def changeLog = ""
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
                // ${new Date(entry.timestamp)  ${entry.commitId}
                def truncatedMsg = entry.msg.take(60)  // 提交记录长度截取
                def combinationMsg = "- ${truncatedMsg} " + "([${entry.commitId.substring(0, 7)}](${ctx.REPO_URL.replace('.git', '')}/commit/${entry.commitId})) " + "@${entry.author} \n"
                // 提交记录信息去重
                if (!(featChangeLog.contains("${combinationMsg}") || fixChangeLog.contains("${combinationMsg}") || otherChangeLog.contains("${combinationMsg}"))) {
                    if (truncatedMsg.toString().startsWith(GlobalVars.gitCommitFeature)) {
                        featChangeLog += combinationMsg
                    } else if (truncatedMsg.toString().startsWith(GlobalVars.gitCommitFix)) {
                        fixChangeLog += combinationMsg
                    } else {
                        if ((combinationMsg.contains("docs") || combinationMsg.contains("chore") || combinationMsg.contains("test"))) {
                            combinationMsg = ""
                        } else {
                            otherChangeLog += combinationMsg
                        }
                    }
                }
                changeLog += combinationMsg
            }
        }
        if (!changeLog) {
            changeLog = GlobalVars.noChangeLog
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
            changeLog = featChangeLog + fixChangeLog + otherChangeLog
            // println "${changeLog}"
        }
        return changeLog
    } catch (e) {
        println "获取git提交变更记录异常"
        println e.getMessage()
    }
}
