package shared.library.devops

/**
 * @author 潘维吉
 * @date 2021/1/20 22:29
 * @email 406798106@qq.com
 * @description Sonar代码扫描分析
 */


/**
 * 配置项目质量规则
 */
def configQualityProfiles(projectName, lang, qpname) {
    def apiUrl = "qualityprofiles/add_project?language=${lang}&project=${projectName}&qualityProfile=${qpname}"
    // 发请求
    response = httpReq("POST", apiUrl, "")
    println(response)
}

/**
 * 获取质量阈ID
 */
def getQualityGateId(gateName) {
    def apiUrl = "qualitygates/show?name=${gateName}"
    // 发请求
    response = httpReq("GET", apiUrl, "")
    // 对返回的文本做JSON解析
    response = readJSON text: """${response.content}"""
    // 获取total字段，该字段如果是0则表示项目不存在,否则表示项目存在
    result = response["id"]
    return result
}

/**
 * 更新质量阈规则
 */
def configQualityGate(projectKey, gateName) {
    // 获取质量阈id
    gateId = GetQualityGateId(gateName)
    apiUrl = "qualitygates/select?projectKey=${projectKey}&gateId=${gateId}"
    // 发请求
    response = httpReq("POST", apiUrl, "")
    println(response)
}

/**
 * 获取Sonar质量阈状态
 */
def getProjectStatus(projectName) {
    apiUrl = "project_branches/list?project=${projectName}"
    response = httpReq("GET", apiUrl, '')

    response = readJSON text: """${response.content}"""
    result = response["branches"][0]["status"]["qualityGateStatus"]
    //println(response)
    return result
}

/**
 * 封装HTTP请求
 */
def httpReq(requestType, requestUrl, requestBody) {
    // 定义sonar api接口
    def sonarServerApi = "${sonarServer}/api"
    result = httpRequest authentication: 'sonar-admin-user',
            httpMode: requestType,
            contentType: "APPLICATION_JSON",
            consoleLogResponseBody: true,
            ignoreSslErrors: true,
            requestBody: requestBody,
            url: "${sonarServerApi}/${requestUrl}"
    return result
}

/**
 * 获取sonar项目，判断项目是否存在
 */
def searchProject(projectName) {
    def apiUrl = "projects/search?projects=${projectName}"
    // 发请求
    response = httpReq("GET", apiUrl, "")
    println "搜索的结果：${response}"
    // 对返回的文本做JSON解析
    response = readJSON text: """${response.content}"""
    // 获取total字段，该字段如果是0则表示项目不存在,否则表示项目存在
    result = response["paging"]["total"]
    // 对result进行判断
    if (result.toString() == "0") {
        return "false"
    } else {
        return "true"
    }
}

/**
 * 创建sonar项目
 */
def createProject(projectName) {
    def apiUrl = "projects/create?name=${projectName}&project=${projectName}"
    // 发请求
    response = httpReq("POST", apiUrl, "")
    println(response)
}
