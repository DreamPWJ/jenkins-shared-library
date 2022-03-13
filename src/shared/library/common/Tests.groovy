package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/8/10 13:22
 * @email 406798106@qq.com
 * @description 各种测试相关
 */
class Tests implements Serializable {

    /**
     * 运行 GitHub Super-Linter
     */
    static def runGitHubSuperLinter(ctx, disableErrors) {
        ctx.sh("\$(pwd)/scripts/run-github-super-linter -e DISABLE_ERRORS=$disableErrors " +
                "-v /etc/ssl/certs/:/etc/ssl/certs/ -v /usr/local/share/ca-certificates/:/usr/local/share/ca-certificates/")
    }

    /**
     * 创建JUnit报告
     */
    static def createJUnitReport(ctx) {
        ctx.junit('test-output/junit.xml')
    }

    /**
     * 创建JMeter性能报告
     */
    static def createJMeterReport(ctx) {
        // 参考文档: https://www.jenkins.io/doc/book/using/using-jmeter-with-jenkins/
    }

    /**
     * 创建冒烟测试报告
     */
    static def createSmokeReport(ctx) {

    }

    /**
     * PostMan自动化API测试
     */
    static def runPostman(ctx) {
        // Postman+Newman集成测试方案
        ctx.sh 'npm install -g newman'
        ctx.sh 'npm install -g newman-reporter-html'
        ctx.sh "cd _test/postman/ && newman run postman_collection.json " +
                "-e postman_environment.json -g postman_globals.json --bail newman --delay-request 100 " +
                "-r cli,html --reporter-html-export reports/postman-report-${ctx.env.BUILD_NUMBER}.html"
        ctx.dir("${ctx.env.WORKSPACE}/_test/postman") {
            ctx.publishHTML(target: [
                    allowMissing         : false,
                    alwaysLinkToLastBuild: false,
                    keepAll              : true,
                    reportDir            : 'reports',
                    reportFiles          : "postman-report-${ctx.env.BUILD_NUMBER}.html",
                    reportName           : "Postman Report"
            ])
        }
    }

}
