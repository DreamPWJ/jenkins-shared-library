package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/9/26 13:22
 * @email 406798106@qq.com
 * @description C++语言
 */
class Cpp implements Serializable {

    /**
     * 构建
     */
    static def build(ctx) {
        // 查看gcc的版本   gcc 和 g++ 分别是GNU的C 和 C++编译器
        ctx.sh " g++ -v "
        // 注意Linux上部署需要在Linux机器构建
        // 源码g++编译成一个可执行文件
        ctx.sh " g++ app.cpp -o app "
    }

}
