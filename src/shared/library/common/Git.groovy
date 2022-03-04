package shared.library.common

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
                def userPassWordUrl = " http://${ctx.GIT_USERNAME}:${ctx.ENCODED_GIT_PASSWORD}" +
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
            println error.getMessage()
            println "Git提交文件到远程失败 ❌ "
        }
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
