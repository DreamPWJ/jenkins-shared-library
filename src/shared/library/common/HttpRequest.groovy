package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/1/26 13:22
 * @email 406798106@qq.com
 * @description http请求工具类
 */
class HttpRequest implements Serializable {

    /**
     *  Get请求
     */
    static def get(ctx, requestUrl) {
        def response = ctx.httpRequest authentication: '',
                httpMode: "GET",
                contentType: "APPLICATION_JSON",
                consoleLogResponseBody: false,
                ignoreSslErrors: true,
                url: "${requestUrl}"
        //println("=======Status: " + response.status)
        //println("=======Content: " + response.content)
        return response.content
    }

/**
 *  Post请求
 */
    static def post(ctx, requestUrl, requestBody) {
        //def json = JsonOutput.toJson(["name": "", "age": ""])
        def response = ctx.httpRequest authentication: '',
                httpMode: "POST",
                contentType: "APPLICATION_JSON",
                consoleLogResponseBody: true,
                ignoreSslErrors: true,
                requestBody: requestBody,
                url: "${requestUrl}"
        return response.content
    }

    /**
     *  curl方式网络请求
     *  curl支持授权和文件上传等
     */
    static def curl(ctx, url, token) {
        return ctx.sh(returnStdout: true, script: "curl -s -H 'Authorization: token $token' $url").trim()
    }

}

