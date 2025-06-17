package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2025/6/17 08:30
 * @email 406798106@qq.com
 * @description API 文档、设计、开发、测试一体化协作平台
 * Apifox = Postman + Swagger + Mock + JMeter
 */
class ApiFox implements Serializable {

    /**
     * API 自动化测试 支持 1. 集成测试  2. 端到端测试  3. 回归测试  4. 性能测试
     * 参考文档: https://docs.apifox.com/5637756m0
     */
    static def autoTest(ctx) {
        ctx.sh " npm install -g apifox-cli "
        ctx.sh " apifox run examples/sample.apifox-cli.json -r cli,html "
    }

}
