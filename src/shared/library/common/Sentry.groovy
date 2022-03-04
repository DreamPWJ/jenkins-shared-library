package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/12/12 13:22
 * @email 406798106@qq.com
 * @description Sentry实时的错误事件日志和聚合平台  支持大部分语言
 */
class Sentry implements Serializable {

    /**
     * 集成错误日志监控
     * 文档: https://docs.sentry.io/
     */
    static def integration(ctx) {
        ctx.sh "npm install --save @sentry/browser @sentry/tracing"
    }

}
