### [Fastlane](https://docs.fastlane.tools/) 是自动化iOS和Android应用程序Beta部署和发布的最简单方法 🚀

##### 它可以处理所有繁琐的任务 [常见问题](https://docs.fastlane.tools/faqs/)

- 生成屏幕截图
- 处理代码签名和打包
- 发布应用程序
  
##### App Store安装XCode(M1可开启Rosetta方式兼容)  后绑定Apple ID账号和账号管理添加证书配置!!!
##### 打开到Xcode并双击安装开发和分发两个p12 将证书导出并始终允许钥匙串访问(否则导致最后编译完成签名错误)!!!
##### 重要: 打开Xcode查看签名配置正确  在CI机器的Xcode打开.xcworkspace工程手动打包测试环境OK后再使用Fastlane自动化打包!!!
##### 如果p12证书和mobileprovision不匹配等 苹果开发者中心删除对应的mobileprovision文件 fastlane会在执行的时候自动创建两者
##### 确保苹果开发者中心和Apple Store Connect平台的不定期更新协议已同意
##### 苹果Mac自动化环境稳定性可以使用自带的时光机功能做备份

xcode-select --install && xcodebuild -version
softwareupdate --install -a

##### 初始化依赖环境 CocoaPods镜像使用 加速pod install or update
- 安装brew  
  /usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
  brew info && brew update
  
- gem sources -a https://gems.ruby-china.com/
- sudo gem install -n /usr/local/bin cocoapods -v 1.10.2 
- cd ~/.cocoapods/repos
- pod repo remove master
- git clone https://mirrors.tuna.tsinghua.edu.cn/git/CocoaPods/Specs.git master
- pod --version
- gem list --local | grep cocoapods
- sudo gem uninstall cocoapods  // 卸载

- 工程的Podfile文件内第一行加上 官方源 https://github.com/CocoaPods/Specs.git
- source 'https://mirrors.tuna.tsinghua.edu.cn/git/CocoaPods/Specs.git'
- source 'https://github.com/aliyun/aliyun-specs.git'

- github下载连接失败443等 [IPAddress](https://www.ipaddress.com/) [参考文章](https://www.jianshu.com/p/070a762d47d0)
- Linux或MacOS 在 sudo vim /etc/hosts设置读写权限 在Windows10 管理员身份执行CMD cd C:\Windows\System32\Drivers\etc 执行notepad hosts
  140.82.112.4 github.com 
  199.232.5.194 github.global.ssl.fastly.net 
  185.199.108.133 raw.githubusercontent.com
  47.246.22.227 registry.npm.taobao.org 
  209.95.59.244 registry.npm.org
  172.67.129.60 raster.shields.io
  
##### 安装Fastlane
ruby -v && gem -v

brew install fastlane
sudo gem install fastlane

cd ~/ && touch .bash_profile
open .bash_profile
export PATH="$HOME/.fastlane/bin:$PATH"
source .bash_profile

fastlane --version

##### brew方式安装Ruby 参考文章: https://mac.install.guide/ruby/13.html
brew install ruby
gem install bundler && bundle install && bundle update

- Apple Silicon 苹果芯片设置在~/.zshrc新版本Ruby环境变量
if [ -d "/opt/homebrew/opt/ruby/bin" ]; then
export PATH=/opt/homebrew/opt/ruby/bin:$PATH
export PATH=`gem environment gemdir`/bin:$PATH
fi

##### rvm方式管理多版本Ruby 集成到Jenkins: https://rvm.io/integration/jenkins
rvm reinstall ruby --disable-binary

curl -L https://get.rvm.io | bash -s stable
rvm install ruby-3.0.3
rvm use 3.0.3
rvm use system
rvm --default use 3.0.3

which ruby
ruby -v && gem -v

##### 授权相关文件
Gemfile文件放在SCHEME目录下
gem install使用--user-install替代sudo解决权限错误  导入~/.zshrc中export GEM_HOME=$HOME/.gem 与 export PATH=$GEM_HOME/bin:$PATH
ERROR:  While executing gem ... (Gem::FilePermissionError)
You don't have write permissions for the /Library/Ruby/Gems/2.0.0 directory.

sudo chown -R $USER /Library/Ruby/Gems/
sudo chown -R $USER /usr/local/bin

##### 导航到您的iOS或Android应用代码并运行
fastlane --version
fastlane init

##### Fastfile文件定义 🎉
- 定义了2条不同的通道，一条用于beta部署，一条用于App Store。要在App Store中发布您的应用
- 执行 fastlane beta

##### 设置环境变量
- fastlane需要设置一些环境变量才能正确运行。特别是，如果您的语言环境未设置为UTF-8语言环境，则会导致构建和上传构建问题。在您的外壳配置文件中添加以下行用
- 解决ArgumentError - invalid byte sequence in US-ASCII错误
  export LC_ALL=zh_CN.UTF-8
  export LANG=zh_CN.UTF-8
  export LANGUAGE=zh_CN.UTF-8
- 您可以在您的壳轮廓~/.bashrc，~/.bash_profile，~/.profile或者~/.zshrc根据您的系统

##### 新的证书和配置文件 Git存储
- fastlane match init 
- fastlane match development
- 删除的配置文件/证书的 fastlane match nuke development 

##### fastlane 的各文件解释如下[参考文章](https://www.jianshu.com/p/f6aeddb50167)
- Appfile: 用于指定存储开发者账号相关信息 app_identifier, apple_id, team_id
- Fastfile: 核心文件，主要用于命令行调用和处理具体的流程，lane相对于一个方法或者函数 配置管理lane
- Deliverfile: deliver工具的配置文件 配置应用在 iTunes Connect中的各种信息和 Apple Developer Center 中的数据是一一对应的
- metadata:  元数据文件夹 包含应用在 iTunes Connect 中的各种信息
- screenshots: 截图数据文件夹

##### Fastlane常用命令
- fastlane actions: 展示所有有效action列表
- fastlane action [action_name]: 展示一个action的详细说明，使用方法等
- fastlane lanes: 展示Fastfile中的所有lane
- fastlane list: 展示Fastfile中的所有的有效的lane
- fastlane new_action: 创建一个新的action
- fastlane env: 打印fastlane、ruby环境，一般提bug到issue的时候会要求提供

##### Fastlane生命周期顺序
1. before_all 在执行lane之前只执行一次
2. before_each 每次执行lane之前都会执行一次
3. lane 自定义的任务
4. after_each 每次执行lane之后都会执行一次
5. after_all 在执行lane成功结束之后执行一次
6. error 在执行任意环境报错都会中止并执行一次

