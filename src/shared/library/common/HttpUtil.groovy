package shared.library.common

/**
 * @author 潘维吉
 * @date 2025/6/10 08:22
 * @email 406798106@qq.com
 * @description http请求工具类
 * 基于Groovy原生HTTP库 零依赖，完全编程控制
 */
class HttpUtil implements Serializable {

    /**
     *  GET请求
     */
    static def get(ctx, requestUrl) {
        def get = new URL(requestUrl).openConnection()
        get.setRequestProperty("Accept", "application/json")
        def responseCode = get.getResponseCode()
        if (responseCode == 200) {
            def content = get.getInputStream().getText()
            echo "GET获取数据: ${content}"
        }
    }

    /**
     *  POST请求
     */
    static def post(ctx, requestUrl,  jsonBody) {
        // POST请求（带JSON体）
        def post = new URL(requestUrl).openConnection()
        post.setRequestMethod("POST")
        post.setDoOutput(true)
        post.setRequestProperty("Content-Type", "application/json")
        post.getOutputStream().write(jsonBody.getBytes("UTF-8"))
        def postCode = post.getResponseCode()
        if (postCode == 200) {
            def content = post.getInputStream().getText()
            echo "POST获取数据: ${content}"
        }
    }

}

