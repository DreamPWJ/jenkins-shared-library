package shared.library.common

import shared.library.common.*
import groovy.json.JsonOutput

/**
 * @author 潘维吉
 * @date 2021/1/26 13:22
 * @email 406798106@qq.com
 * @description 钉钉通知
 * 每个机器人每分钟最多发送20条消息到群里，如果超过20条，会限流10分钟
 */
class DingTalk implements Serializable {

    static def DING_TALK_URL = "https://oapi.dingtalk.com/robot/send?access_token=" // 钉钉通知请求地址
    static def DING_TALK_KEY_WORD = "潘维吉"   // 默认钉钉通知关键字

    /**
     * Markdown类型通知
     * 直接使用http交互更灵活 不再基于插件实现
     * 文档: https://open.dingtalk.com/document/robots/custom-robot-access
     */
    static def noticeMarkDown(ctx, credentialsIds, title, content, mobile = "") {
        // 支持多钉钉群同时通知
        credentialsIds.each { item ->
            def url = "${DING_TALK_URL}${item.token}"
            def keyword = item.keyword
            if (keyword == null || keyword == "") {
                keyword = DING_TALK_KEY_WORD
            }
            if (mobile != "") { // @通知 内容里必须包含通知的手机号
                content = content + "@" + mobile
            }
            def json = [
                    "msgtype" : "markdown",
                    "markdown": [
                            "title": "${title}-${keyword}",
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
    }

    /**
     * ActionCard整体跳转样式类型通知
     */
    static def noticeActionCard(ctx, credentialsIds, title, content, mobile = "") {
        // 支持多钉钉群同时通知
        credentialsIds.each { item ->
            def url = "${DING_TALK_URL}${item.token}"
            def keyword = item.keyword
            if (keyword == null || keyword == "") {
                keyword = DING_TALK_KEY_WORD
            }
            if (mobile != "") { // @通知 内容里必须包含通知的手机号
                content = content + "@" + mobile
            }
            def json = [
                    "msgtype"     : "action_card",
                    "markdown"    : [
                            "title": "${title}-${keyword}",
                            "text" : "${content}"
                    ],
                    "single_title": "查看详情",
                    "single_url"  : "https://open.dingtalk.com",
                    "at"          : [
                            "atMobiles": [
                                    "${mobile}"
                            ],
                            "isAtAll"  : false
                    ]
            ]
            def data = HttpUtil.post(ctx, url, JsonOutput.toJson(json))
            // ctx.println("钉钉通知结果: ${data}")
        }
    }

    /**
     * 通知  插件方式 不再推进继续使用
     * 文档: https://open.dingtalk.com/document/robots/custom-robot-access
     * 插件: https://jenkinsci.github.io/dingtalk-plugin/
     */
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
