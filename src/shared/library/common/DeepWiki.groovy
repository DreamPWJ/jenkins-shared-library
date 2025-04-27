package shared.library.common

/**
 * @author 潘维吉
 * @date 2025/2/26 09:22
 * @email 406798106@qq.com
 * @description 基于Devin的DeepWiki自动生成软件工程文档
 */
class DeepWiki implements Serializable {

    /**
     *  Devin的文档生成能力主要通过 DeepWiki实现
     */
    static def generateDocs(ctx) {
        // 每次项目发布都自动更新文档
        ctx.sh " deepwiki-generate --repo ${PROJECT_URL} --output docs/ "
    }

}
