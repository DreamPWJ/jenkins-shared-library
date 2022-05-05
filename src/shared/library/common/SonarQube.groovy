package shared.library.common

import shared.library.GlobalVars

/**
 * @author 潘维吉
 * @date 2021/2/18 8:41
 * @email 406798106@qq.com
 * @description Sonar代码质量扫描
 */
class SonarQube implements Serializable {

    /**
     *  SonarQube服务端地址
     */
    static def sonarServer = "http://172.16.1.55:9000"

    /**
     *  Sonar扫描
     */
    static def scan(ctx, projectName) {
        // 以时间戳为版本
        def scanTime = ctx.sh returnStdout: true, script: 'date +%Y%m%d%H%M%S'
        scanTime = scanTime - "\n"
        // 不同项目的参数组合
        def diffParams = ""
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            diffParams = " -Dsonar.sources=. -Dsonar.javascript.exclusions=node_modules,dist " +
                    " -Dsonar.exclusions=**/*.css,**/*.html -Dsonar.javascript.node.maxspace=4096 "
        }
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
            diffParams = "  -Dsonar.sources=. -Dsonar.java.binaries=. "
        }
        // docker pull newtmitch/sonar-scanner:4-alpine
        ctx.sh """
     docker run -i -v \$(pwd):/usr/src --link sonarqube:community  newtmitch/sonar-scanner:4-alpine  \
    -Dsonar.host.url=${SonarQube.sonarServer}  \
    -Dsonar.login=admin \
    -Dsonar.password=123456 \
    -Dsonar.ws.timeout=30 \
    -Dsonar.projectKey=${projectName}  \
    -Dsonar.projectName=${projectName}  \
    -Dsonar.projectDescription="${projectName}"  \
    -Dsonar.projectVersion=${scanTime} \
    -Dsonar.links.homepage=${ctx.REPO_URL} \
    -Dsonar.sourceEncoding=UTF-8 \
    ${diffParams}

    echo "${projectName}项目扫描成功 ✅"
    """
    }

    /**
     * 获取sonar项目的状态
     */
    static def getStatus(ctx, projectName) {
        def apiUrl = "${SonarQube.sonarServer}" + "/project_branches/list?project=${projectName}"
        // 发请求
        def responses = HttpRequest.get(ctx, apiUrl)
        ctx.println(responses)
        // 对返回的文本做JSON解析
        def response = ctx.readJSON text: "${responses}"
        // 获取状态值
        def result = response["branches"][0]["status"]["qualityGateStatus"]
        ctx.println(result)
        return result
    }

}
