package shared.library.common

import shared.library.Utils
import shared.library.common.*

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
     * 全量分析 + 增量分析 每次构建或者每次提交代码时都扫描代码
     */
    static def analyse(ctx, map) {
        ctx.println("Qodana开始扫描分析代码质量 ... 🔍")

        def qodanaReportDir = "${ctx.env.WORKSPACE}/qodana-report"
        def qodanaYamlPath = "${ctx.env.WORKSPACE}/ci/_jenkins/qodana/" // Qodana YAML 配置文件路径
        def isCodeDiff = true // 是否增量代码检测
        def isFailThreshold = true // 是否设置质量阈值
        def isApplyFixes = false // 是否自动修复  社区版不支持高级功能
        def earliestCommit = null  // 变更记录

        if (isCodeDiff) { // 是否增量代码检测
            // 获取jenkins变更记录 用于增量代码分析 从父提交到当前提交的代码变更
            ctx.env.EARLIEST_COMMIT = ctx.sh(script: 'git rev-parse HEAD^', returnStdout: true).trim()
        }

        // 如果需要连接Qodana Cloud服务需要访问token  非社区版都需要Qodana Cloud配合
        // ctx.sh "export QODANA_TOKEN="
        def qodanaParams = ""
        if (isCodeDiff) { // 是否增量代码检测
            qodanaParams = qodanaParams + " --diff-start=${ctx.env.EARLIEST_COMMIT} "
        }
        if (isFailThreshold) { // 是否设置质量阈值 避免大量累计技术债务
            int failNum = 1000 // 质量门最大错误数 全量和增量检测动态改变
            qodanaParams = qodanaParams + " --fail-threshold ${failNum} "
        }
        if (isApplyFixes) { // 是否自动修复
            qodanaParams = qodanaParams + " --apply-fixes "
        }
        if ("${ctx.COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) { // 自定义yaml检测规则文件
            qodanaParams = qodanaParams + " --config " + qodanaYamlPath + "qodana.yaml"
        }
        // Qodana离线报告需要Web服务运行起来才能展示, 直接点击HTML单文件打开不显示
        ctx.sh " qodana scan --save-report ${qodanaParams} " +
                " --source-directory ${ctx.env.WORKSPACE} --report-dir=${qodanaReportDir}  "
        // --baseline qodana-baseline

        if (isApplyFixes) {  // 是否自动修复并提交PR审核
            def changes = ctx.sh(script: 'git status --porcelain', returnStdout: true).trim()
            ctx.println(changes)
            // 检查是否有变更
            if (!changes || changes == "") {
                return
            }
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
                   """)
                // 创建新分支
                def branchName = "qodana-auto-fix"
                // 推送变更文件到远程仓库
                ctx.sh("""
                  git rev-parse --verify ${branchName} >/dev/null 2>&1 && git checkout ${branchName} || git checkout -b ${branchName}
                  git add *.java
                  git commit -m "fix: Qodana auto fix [${ctx.PROJECT_NAME}#${ctx.env.BUILD_NUMBER}]" || true
                  git push ${userPassWordUrl} || true
                   """)
            }
        }

        // 发布 HTML 报告 显示在左侧菜单栏  需要安装插件 https://plugins.jenkins.io/htmlpublisher/
        // 在页面系统管理脚本命令杭州执行 确保Jenkins已调整CSP允许JavaScript执行
        try {
            System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:;")
        } catch (e) {
            ctx.println("Jenkins调整CSP允许JavaScript执行失败")
        }
        def reportName = "Qodana-Report"
        ctx.publishHTML(target: [
                reportDir            : "${qodanaReportDir}",
                reportFiles          : 'index.html',
                reportName           : "${reportName}",
                reportTitles         : 'Qodana-Report-CI/CD',
                alwaysLinkToLastBuild: true,
                keepAll              : true,
                allowMissing         : false,
        ])

        // 归档生成的报告文件
        // ctx.archiveArtifacts artifacts: "${qodanaReportDir}/**", allowEmptyArchive: true

        // 钉钉通知质量报告 形成信息闭环
        // if ("${ctx.IS_CODE_QUALITY_ANALYSIS}" == 'true')  // 是否钉钉通知
        DingTalk.notice(ctx, "${map.ding_talk_credentials_id}", "![screenshot](https://blog.jetbrains.com/wp-content/uploads/2022/06/DSGN-13163-Static-analysis-with-Qodana-banners_featured.png) "
                + " 静态代码分析质量报告 ${ctx.env.JOB_NAME} ${ctx.PROJECT_TAG}  📑",
                "\n\n #### 代码质量分析结果: [查看报表](${ctx.env.JOB_URL}${reportName}) 📈"
                        + "\n 持续交付可读、易维护和安全的高质量代码 ✨ "
                        + "\n ###### 执行人: ${ctx.BUILD_USER} \n ###### 完成时间: ${Utils.formatDate()} (${Utils.getWeek(ctx)})", "")
    }

}
