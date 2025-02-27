package shared.library.common

/**
 * @author 潘维吉
 * @date 2025/2/26 09:22
 * @email 406798106@qq.com
 * @description AI人工智能DeepSeek结合DevOps
 */
class DeepSeek implements Serializable {

    /**
     *  智能代码评审 DeepSeek Coder
     */
    static def codeReview(ctx) {
        // 调用大模型API服务
        // ctx.sh " ollama run deepseek-coder "
        ctx.sh " curl -s -X POST https://api.deepseek.com/api/generate "
    }

}
