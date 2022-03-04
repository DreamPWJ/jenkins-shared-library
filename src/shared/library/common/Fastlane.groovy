package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/4/1 13:22
 * @email 406798106@qq.com
 * @description Fastlane自动化相关
 */
class Fastlane implements Serializable {

    /**
     *  初始化环境变量
     */
    static def initEnv(ctx) {
        // 初始化环境变量
        try {
            // 自定义Ruby版本设置环境变量: /opt/homebrew/lib/ruby/gems/3.0.0/bin
            ctx.env.PATH = "${ctx.env.PATH}:/usr/local/bin:/opt/homebrew/opt/imagemagick/bin:" +
                    "/opt/homebrew/lib/ruby/gems/3.0.0/bin:" +
                    "${ctx.GEM_HOME}" //添加了系统环境变量上
        } catch (e) {
            ctx.println("初始化Fastlane环境变量失败")
            ctx.println(e.getMessage())
        }
    }

    /**
     *  Fastlane初始化
     */
    static def init(ctx) {
        ctx.sh """
              ruby -v && gem -v
              brew install fastlane
             """

        // 设置fastlane环境
/*        ctx.sh """
               cd ~/ && touch .bash_profile
               echo '
               export PATH="$HOME/.fastlane/bin:$PATH"
               ' >~/.bash_profile
               source .bash_profile
       """*/

        // 设置语言环境  locale -a
        ctx.sh """
               echo '
               export LC_ALL=en_US.UTF-8
               export LANG=en_US.UTF-8
               ' >~/.bashrc
               ource ~/.bashrc
       """

        // xcode环境
        ctx.sh """
           xcode-select --install 
           xcodebuild -version
       """

        ctx.sh """
           fastlane init
           fastlane --version
       """

    }

    /**
     *  Fastlane更新
     */
    static def update(ctx) {
        ctx.sh """
              gem  install fastlane  
              fastlane --version 
             """
    }
}
