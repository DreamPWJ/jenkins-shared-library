package shared.library.common

/**
 * @author 潘维吉
 * @date 2022/06/28 13:22
 * @email 406798106@qq.com
 * @description Qodana是一个代码质量监控平台 JetBrains公司出品
 * 官网： https://www.jetbrains.com/qodana/
 */
class Qodana implements Serializable {

    /**
     * 分析
     * 文档: https://www.jetbrains.com/help/qodana/jenkins.html
     */
    static def analyse(ctx) {
        def qodanaReportDir = "${ctx.env.WORKSPACE}/qodana-report"
        ctx.sh " qodana  --save-report --report-dir=${qodanaReportDir} "

        // 仅分析新增代码 增量代码分析 --paths-to-exclude 参数来指定只分析变化的文件  https://www.jetbrains.com/help/qodana/analyze-pr.html
        // def gitStartHash = "" // 获取两次提交之间的更改文件列表 用逗号分隔的文件列表传递给Qodana
        // ctx.sh " qodana inspect --diff-start=${gitStartHash} "

/*      // CLI 应该从具有 qodana.yaml
        def qodanaFile = "${ctx.env.WORKSPACE}/ci/_jenkins/qodana/qodana.yaml"
        // ctx.sh "export QODANA=${qodanaFile}"
        // 质量门是 Qdana 可以在不导致 CI/CD 工作流或管道失败的情况下检测到的最大问题数量 一旦达到质量门限，Qodana 就会以退出代码 255 终止
        def qualityGate = " --fail-threshold 500 "
        // 执行分析命令
        ctx.sh "mkdir -p qodana-reports"
        ctx.sh "chmod +x qodana-reports"
        ctx.sh "qodana --show-report ${qualityGate}"*/

        // 展示HTML报告
/*      sh "mkdir -p /var/www/qodana/${config.qodana_path}"
        sh "cp -r qodana-reports/report/* /var/www/qodana/${config.qodana_path}/"
        // make a html-file we can archive in jenkins, that will redirect to our vhost that hosts the above folder
        sh "echo '<html><head><meta http-equiv=\"refresh\" content=\"0; url=https://qodana-host/${config.qodana_path}\" /></head></html>' > qodana-reports/qodana.html"
        archiveArtifacts artifacts: 'qodana-reports/qodana.html', fingerprint: true
        */


        // 发布 HTML 报告 显示在左侧菜单栏 需要安装插件 https://plugins.jenkins.io/htmlpublisher/
        ctx.publishHTML(target: [
                reportName           : 'Qodana质量报告',
                reportDir            : "${qodanaReportDir}/",
                reportFiles          : 'index.html',
                alwaysLinkToLastBuild: true,
                keepAll              : true
        ])

        // 归档生成的 SARIF 报告文件
        // ctx.archiveArtifacts artifacts: "${qodanaReportDir}/qodana.sarif.json", allowEmptyArchive: true

    }

}
