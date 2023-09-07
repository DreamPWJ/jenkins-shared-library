package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2022/1/9 13:22
 * @email 406798106@qq.com
 * @description HuggingFace人工智能AI预训练集大仓库
 */
class HuggingFace implements Serializable {

    /**
     * 初始化
     */
    static def init(ctx) {
        // 安装
        ctx.sh """
        pip install transformers
        pip install torch
        pip install tensorflow
        """
        // 初始化
        ctx.sh " python -m pip install huggingface_hub"
    }

}
