package shared.library.common

import shared.library.common.*
import groovy.json.JsonOutput

/**
 * @author 潘维吉
 * @date 2021/1/26 13:22
 * @email 406798106@qq.com
 * @description 钉钉通知
 */
class DingTalk implements Serializable {

    static def DING_TALK_URL = "https://oapi.dingtalk.com/robot/send?access_token=" // 钉钉通知请求地址
    static def DING_TALK_KEY_WORD = "蓝能科技"   // 钉钉通知关键字

    /**
     * Markdown类型通知
     * 不基于插件实现 直接使用http交互更灵活
     * 文档: https://open.dingtalk.com/document/robots/custom-robot-access
     */
    static def noticeMarkdown(ctx, credentialsId, title, content, mobile = "") {
        def url = "${DING_TALK_URL}${credentialsId}"
        def json = [
                "msgtype" : "markdown",
                "markdown": [
                        "title": "${title}-${DING_TALK_KEY_WORD}",
                        "text" : "${content}"
                ],
                "at"      : [
                        "atMobiles": [
                                "${mobile}"
                        ],
                        "isAtAll"  : false
                ]
        ]
        def data = HttpUtil.post(ctx, url, JsonOutput.toJson(json))
        // ctx.println("钉钉通知结果: ${data}")
    }

    /**
     * 通知
     * 文档: https://open.dingtalk.com/document/robots/custom-robot-access
     * 插件: https://jenkinsci.github.io/dingtalk-plugin/
     */
    static def notice(ctx, credentialsId, title, content, mobile = "") {
        ctx.dingtalk(
                robot: "${credentialsId}",
                type: 'MARKDOWN',
                title: "${title}",
                text: [
                        "### ${title}",
                        "${content}",
                        "##### 请及时处理 🏃",
                ],
                at: ["${mobile}"]
        )
    }

    /**
     * 通知 图片类型
     */
    static def noticeImage(ctx, credentialsId, imageUrl, title, content, mobile = "") {
        ctx.dingtalk(
                robot: "${credentialsId}",
                type: 'MARKDOWN',
                title: "${title}",
                text: [
                        "![screenshot](${imageUrl})",
                        "### ${title}",
                        "${content}",
                        "##### 请及时处理 🏃",
                ],
                btnLayout: 'V',
                btns: [
                        [
                                title    : '查看图片',
                                actionUrl: "${imageUrl}"
                        ]
                ],
                at: ["${mobile}"]
        )
    }

    /**
     * 通知 Link连接类型
     */
    static def noticeLink(ctx, credentialsId, url, title, content, mobile = "") {
        ctx.dingtalk(
                robot: "${credentialsId}",
                type: 'LINK',
                title: "${title}",
                text: [
                        "${content}",
                ],
                messageUrl: "${url}",
                picUrl: "${url}",
                at: ["${mobile}"]
        )
    }

}
