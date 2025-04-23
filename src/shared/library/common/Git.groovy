package shared.library.common

import shared.library.Utils
import shared.library.common.*
import jenkins.model.Jenkins

/**
 * @author 潘维吉
 * @date 2021/4/25 13:22
 * @email 406798106@qq.com
 * @description Git工具类
 */
class Git implements Serializable {

    /**
     * Git提交文件到远程通用方法
     */
    static def pushFile(ctx, filePath, commitMsg) {
        try {
            ctx.withCredentials([ctx.usernamePassword(credentialsId: ctx.GIT_CREDENTIALS_ID,
                    usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                ctx.script {
                    ctx.env.ENCODED_GIT_PASSWORD = URLEncoder.encode(ctx.GIT_PASSWORD, "UTF-8")
                }
                def repoUrlProtocol = ctx.REPO_URL.toString().split("://")[0]
                def userPassWordUrl = repoUrlProtocol + "://${ctx.GIT_USERNAME.replace("@", "%40")}:${ctx.ENCODED_GIT_PASSWORD.replace("@", "%40")}" +
                        "@${ctx.REPO_URL.toString().replace("http://", "").replace("https://", "")} "
                // 先从远程下载最新代码  防止推送的时候冲突
                ctx.sh("""
                   git config --global user.email "406798106@qq.com"
                   git config --global user.name ${ctx.GIT_USERNAME}
                   git pull ${userPassWordUrl}
                   """)
                // 推送变更文件到远程仓库
                ctx.sh("""
                  git add ${filePath}
                  git commit ${filePath}  -m "${commitMsg}" 
                  git push ${userPassWordUrl}
                   """)
            }
        } catch (error) {
            ctx.println "Git提交文件到远程失败 ❌ "
            ctx.println error.getMessage()
        }
    }

    /**
     * Git变更记录中是否存在指定的文件
     * 可用于判断各端依赖文件是否变化, 只有变化后才重新下载依赖, 减少CI无效资源和时间的浪费
     */
    static boolean isExistsChangeFile(ctx, fileName = "package.json", lockFileName = "package-lock.json") {
        try {
            def changedFiles = JenkinsCI.getChangedFilesList(ctx)
            if (changedFiles.isEmpty()) { // 无变更文件 可判断是否初始化过依赖 防止重复安装浪费资源和时间
                // 是否存在node_modules文件夹
                if (ctx.fileExists("node_modules")) {
                    // ctx.println "node_modules文件夹存在"
                    return false
                } else {
                    ctx.println "node_modules文件夹不存在"
                    return true
                }
            } else {
                def isExistsFile = changedFiles.findAll { a ->
                    changedFiles.any { (a.contains(fileName) || a.contains(lockFileName) || a.contains("yarn.lock") || a.contains("pnpm-lock.yaml") || a.contains("Podfile")) }
                }
                if (isExistsFile) {
                    ctx.println "依赖包配置管理文件在Git代码中上发生了变化"
                }
                return isExistsFile
            }
        } catch (error) {
            ctx.println "获取Git变更记录中是否存在指定的文件失败"
            ctx.println error.getMessage()
        }
        return true
    }

    /**
     * git获取tag最大语义化版本号
     */
    static def getGitTagMaxVersion(ctx) {
        ctx.withCredentials([ctx.usernamePassword(credentialsId: ctx.GIT_CREDENTIALS_ID,
                usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
            ctx.script {
                ctx.env.ENCODED_GIT_PASSWORD = URLEncoder.encode(ctx.GIT_PASSWORD, "UTF-8")
            }
            def repoUrlProtocol = ctx.REPO_URL.toString().split("://")[0]
            def userPassWordUrl = repoUrlProtocol + "://${ctx.GIT_USERNAME.replace("@", "%40")}:${ctx.ENCODED_GIT_PASSWORD.replace("@", "%40")}" +
                    "@${ctx.REPO_URL.toString().replace("http://", "").replace("https://", "")} "
            // 更新远程所有分支tag标签  先更新标签 后按照标签时间和版本号排序
            ctx.sh("git fetch --tags --force ${userPassWordUrl} || true")

            // 执行 git tag -l 命令获取所有标签
            def tags = ctx.sh(returnStdout: true, script: 'git tag -l').trim().replaceAll("v", "").replaceAll("V", "").split('\n')
            def validTags = []
            def pattern = ~/^[0-9]+\.[0-9]+\.[0-9]+$/
            // 筛选出符合语义化版本号格式的标签
            for (tag in tags) {
                if (tag ==~ pattern) {
                    validTags.add(tag)
                }
            }
            // 对语义化版本号进行排序
            versionSort(validTags)
            ctx.println(validTags.toString())
            // 获取最大的语义化版本号
            def latestTag = validTags.isEmpty() ? null : validTags.last()
            if (latestTag) {
                ctx.echo "最大的Git Tag语义化版本号是: ${latestTag}"
                return latestTag
            } else {
                ctx.echo "未找到Git Tag符合语义化版本号格式的标签。"
                return "1.0.0"
            }
        }
    }

    /**
     * 语义化版本排序
     */
    @NonCPS
    public static java.util.List versionSort(java.util.ArrayList validTags) {
        // sort方法需要@NonCPS避免序列化限制  否则只执行一次
        validTags.sort { a, b ->
            def aParts = a.split('\\.').collect { it.toInteger() }
            def bParts = b.split('\\.').collect { it.toInteger() }
            for (int i = 0; i < Math.min(aParts.size(), bParts.size()); i++) {
                if (aParts[i] != bParts[i]) {
                    return aParts[i] - bParts[i]
                }
            }
            return aParts.size() - bParts.size()
        }
    }

    /**
     * 获取GIT某个时间段的提交记录，并且去除merge信息
     * --since 为时间戳或者日期格式
     */
    @NonCPS
    static def getGitLogByTime(ctx, int maxRecordsNum = 100) {
        def gitLogs = ""
        try {
            // 如 git log --pretty=format:" %s @%an %cr (%H) ; " -n 10 --since='2023-03-28 15:55:00' --no-merges
            def jenkins = Jenkins.instance.getItem(ctx.env.JOB_NAME)
            def lsb = jenkins.getLastSuccessfulBuild()  // 上次成功的构建
            def lsbTime = lsb.getTime().format("yyyy-MM-dd HH:mm:ss")
            ctx.println("上次成功构建时间: " + lsbTime)
            gitLogs = Utils.getShEchoResult(ctx, "git log --pretty=format:\"- %s @%an ;\" -n ${maxRecordsNum}  --since='${lsbTime}' --no-merges")
            // 针对变更记录数组遍历可进行特殊化处理
            return gitLogs
        } catch (error) {
            ctx.println "获取GIT某个时间段的提交记录失败"
            ctx.println error.getMessage()
        }
        return gitLogs
    }


    /**
     * git提交记录
     */
    static def getGitCommitMsg(ctx, num = 1) {
        return ctx.sh(script: "git log -${num} --pretty=format:'%s' --abbrev-commit", returnStdout: true)
    }

    /**
     * git提交作者
     */
    static def getGitCommitAuthor(ctx) {
        return ctx.sh(script: "git log -1 --pretty=format:'%an'", returnStdout: true)
    }

    /**
     * git提交人邮箱
     */
    static def getGitCommitMail(ctx) {
        return ctx.sh(script: "git log -1 --pretty=format:'%ae'", returnStdout: true)
    }

    /**
     * git提交记录是否包含存在 失败返回0
     */
    static int isGitCommitMsgExist(ctx, flag) {
        return ctx.sh(script: "git log -1 | grep ${flag}", returnStatus: true)
    }

    /**
     * git远程仓库地址
     */
    static def getRepoUrl(ctx) {
        return ctx.sh(returnStdout: true, script: 'git config --get remote.origin.url').trim()
    }

    /**
     * git远程仓库名称
     */
    static def getRepoName(ctx) {
        return ctx.scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split('\\.git')[0]
    }

    /**
     * 获取指定git tag时间
     */
    static int getTagTime(ctx, tagName) {
        return ctx.sh(script: "git log -1 --format=%ai ${tagName}", returnStatus: true)
    }

    /**
     *  给ci仓库本地打tag
     */
    static def ciLocalTag(ctx) {
        // 给CI源码打本地tag  可作为内测环境回滚方案 一般生产环境本地线上都有tag记录

    }

    /**
     *  git提交规范校验
     */
    static boolean checkGitCommit(ctx) {
        // 符合git提交规范 https://www.conventionalcommits.org/
        // 同时校验  feat: 修复了bug等 类型和文案不匹配的情况
        return true
    }

}
