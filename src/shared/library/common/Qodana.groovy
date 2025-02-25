package shared.library.common

/**
 * @author 潘维吉
 * @date 2022/06/28 13:22
 * @email 406798106@qq.com
 * @description Qodana是一个代码质量监控平台 报告更直观 JetBrains公司出品
 * 官网： https://www.jetbrains.com/qodana/
 */
class Qodana implements Serializable {

    /**
     * 分析代码
     * 文档: https://www.jetbrains.com/help/qodana/jenkins.html
     */
    static def analyse(ctx) {
        def qodanaReportDir = "${ctx.env.WORKSPACE}/qodana-report"
        def isCodeDiff = false // 是否增量代码检测
        def isFailThreshold = false // 是否设置质量阈值
        def isApplyFixes = false // 是否自动修复
        def earliestCommit = null  // 变更记录

        if (isCodeDiff) { // 是否增量代码检测
            // 获取jenkins变更记录 用于增量代码分析  优先尝试 changeSets
            def changeSets = ctx.currentBuild.changeSets
            if (changeSets != null && !changeSets.isEmpty()) {
                for (changeSet in changeSets) {
                    def commits = changeSet.items
                    if (commits != null && commits.length > 0) {
                        earliestCommit = commits[0].commitId
                    }
                }
            }
            ctx.env.EARLIEST_COMMIT = earliestCommit
        }

        ctx.println("Qodana开始扫描分析代码质量...")
        // 如果需要连接Qodana Cloud服务需要访问token  非社区版都需要Qodana Cloud配合
        ctx.sh "export QODANA_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJwcm9qZWN0IjoiYjhPcmEiLCJvcmdhbml6YXRpb24iOiJBYldWYiIsInRva2VuIjoicDBZa1AifQ.HnRUk9HsuqzOwN_iMzkcUiFQIsA23GTDpa_yb9oT2Dg"
        def qodanaParams = ""
        if (isCodeDiff) { // 是否增量代码检测
            qodanaParams = qodanaParams + " --diff-start=${ctx.env.EARLIEST_COMMIT} "
        }
        if (isFailThreshold) { // 是否设置质量阈值 避免大量累计技术债务
            qodanaParams = qodanaParams + " --fail-threshold 100 "
        }
        if (isApplyFixes) { // 是否自动修复
            qodanaParams = qodanaParams + " --apply-fixes "
        }
        // Qodana离线报告需要Web服务运行起来才能展示, 直接点击HTML单文件打开不显示
        ctx.sh " qodana scan --save-report ${qodanaParams} --report-dir=${qodanaReportDir} "

        // 自动修复并提交PR审核
        def changes = ctx.sh(script: 'git status --porcelain', returnStdout: true).trim()
        if (isApplyFixes && changes) {   // 检查是否有变更
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
                // 创建新分支
                def branchName = "qodana-auto-fixes"
                // 推送变更文件到远程仓库
                ctx.sh("""
                  ctx.sh "git checkout -b ${branchName}"
                  ctx.sh 'git add .'
                  ctx.sh "git commit -m \\"fix: Qodana auto fixes [${ctx.PROJECT_NAME}-${ctx.env.BUILD_NUMBER}]\\""
                  git push ${userPassWordUrl}
                   """)
            }
        }

/*      // CLI 应该从具有 qodana.yaml
        def qodanaFile = "${ctx.env.WORKSPACE}/ci/_jenkins/qodana/qodana.yaml"
        // ctx.sh "export QODANA=${qodanaFile}"
        // 质量门是 Qdana 可以在不导致 CI/CD 工作流或管道失败的情况下检测到的最大问题数量 一旦达到质量门限，Qodana 就会以退出代码 255 终止
        def qualityGate = " --fail-threshold 500 "
        // 执行分析命令
        ctx.sh "mkdir -p qodana-reports"
        ctx.sh "chmod +x qodana-reports"
        ctx.sh "qodana --show-report ${qualityGate}"*/


        // 发布 HTML 报告 显示在左侧菜单栏  需要安装插件 https://plugins.jenkins.io/htmlpublisher/
        // 确保Jenkins已调整CSP允许JavaScript执行
        ctx.System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;")
        ctx.publishHTML(target: [
                reportDir            : "${qodanaReportDir}",
                reportFiles          : 'index.html',
                reportName           : 'Qodana-Report',
                reportTitles         : 'Qodana-Report-Title',
                alwaysLinkToLastBuild: true,
                keepAll              : true,
                allowMissing         : false,
        ])

        // 归档生成的报告文件
        // ctx.archiveArtifacts artifacts: "${qodanaReportDir}/**", allowEmptyArchive: true

    }

}
