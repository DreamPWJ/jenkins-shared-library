#!/usr/bin/env bash
# Author: 潘维吉
# Description:  Jenkins分布式node节点环境初始化

if [[ ! $(command -v git) ]]; then
  echo "安装Git"
  sudo yum update || true
  # sudo yum search git | grep -i 'git2'
  # sudo yum install -y libgit2.x86_64 || true
  sudo yum install -y git || true
  sudo apt-get install -y git || true
  git --version
  which git  # 在node节点工具位置选项配置 !!!
fi

if [[ ! $(command -v java) ]]; then
  echo "安装JDK" # 内切换JDK多版本 修改执行 vim /etc/profile 生效执行 source /etc/profile  如果jenkins node节点因jdk版本启动失败  在高级设置中'Java路径'配置which java路径
  sudo yum -y update
  sudo yum install -y java-25-openjdk  || true # 卸载版本 yum -y remove openjdk-25-jdk-headless
  # cd /usr/lib/jvm && wget https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz  && tar -zxvf jdk-25_linux-x64_bin.tar.gz
  sudo apt update || true
  sudo apt install -y openjdk-25-jdk || true  # 卸载版本 sudo apt-get autoremove openjdk-25-jdk-headless
  java -version
  which java
  # apt-get remove openjdk*
fi

if [[ ! $(command -v node) ]]; then
  echo "安装Nodejs"
  curl -sL https://rpm.nodesource.com/setup_22.x | sudo bash -
  yum install -y nodejs || true
  sudo apt install -y nodejs || true
  node -v && npm -v
  which node
fi

if [[ ! $(command -v mvn) ]]; then
  echo "安装Maven" # export HOMEBREW_BOTTLE_DOMAIN=''
  maven_version=3.9.11
  mkdir -p /opt/maven && cd /opt/maven
  wget https://archive.apache.org/dist/maven/maven-3/${maven_version}/binaries/apache-maven-${maven_version}-bin.tar.gz
  tar -xzvf apache-maven-${maven_version}-bin.tar.gz --strip-components=1

  # 写入数据到文件输出重定向 双 >> 是追加 , 单 > 是覆盖
  # export JAVA_HOME=/usr/bin/java
  # JAVA_HOME配置是有bin目录的层级文件夹
  echo "
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
export JRE_HOME=$JAVA_HOME/jre
export CLASSPATH=$JAVA_HOME/lib:$JRE_HOME/lib:$CLASSPATH
export MAVEN_HOME=/opt/maven/apache-maven-${maven_version}
export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH
" >>/etc/profile

  . /etc/profile

  mvn -v

  echo $MAVEN_HOME && echo $JAVA_HOME && echo $PATH
fi


if [[ ! $(command -v adb) ]]; then
  echo "安装Android SDK"
  # 下载命令工具: https://developer.android.com/studio#command-tools
  mkdir android && cd android
  wget https://dl.google.com/android/repository/commandlinetools-mac-7583922_latest.zip

  unzip commandlinetools-mac-7583922_latest.zip
  rm commandlinetools-mac-7583922_latest.zip

  cd cmdline-tools
  mkdir tools
  mv -i * tools

  vim ~/.zshrc
  export ANDROID_HOME=$HOME/android
  export PATH=$ANDROID_HOME/cmdline-tools/tools/bin/:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools/:$PATH
  source ~/.zshrc

  sdkmanager
  sdkmanager --list
  # 安装 Android SDK
  sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.2" "ndk;27.0.12077973"
  adb --version

  # api-versions.xml文件不再存在
  mkdir $ANDROID_HOME/platform-tools/api/
  cp $ANDROID_HOME/platforms/android-35/data/api-versions.xml $ANDROID_HOME/platform-tools/api/

  sdkmanager --update

  echo $ANDROID_HOME && echo $JAVA_HOME && echo $PATH
fi


# Jenkins新增节点设置 节点管理远程工作目录 为 MacOS设置./jenkins  Linux设置/my/jenkins  标签和名称一致  SSH安全策略选择 Non verifying Verification Strategy

# Flutter官网安装文档: https://docs.flutter.dev/get-started/install

# MacOS安装Android SDK命令文档: https://proandroiddev.com/how-to-setup-android-sdk-without-android-studio-6d60d0f2812a
# MacOS安装Android SDK  用Android Studio内安装Android SDK
# adb --version

# Mac OS 11 Big Sur 设置JAVA_HOME （如果是 bash 则在 ~/.bash_profile; 如果是 zsh，则在 ~/.zshrc 新macos系统默认是zsh）
# 1. nano ~/.zshenv  2. 写入export JAVA_HOME=$(/usr/libexec/java_home)  3. source ~/.zshenv  4. echo $JAVA_HOME && mvn -v

# Mac OS 多版本jdk设置和动态切换
#方案一 推荐 JEnv工具，它可以帮我们更好地管理 & 切换不同版本的JDK 满足多版本jdk同一时间同时使用需求 参考文章https://segmentfault.com/a/1190000020083040
# jenv versions ,  jenv shell 1.8  jenv enable-plugin maven jenv enable plugin export
#Maven内JDK版本跟随jenv切换 在用户目录下新建 ~/.mavenrc文件 必须是.mavenrc   source ~/.mavenrc
#内容是: JAVA_HOME=$(/usr/libexec/java_home -v $(jenv version-name))
# 查看 /usr/libexec/java_home -V  &&  mkdir -p ~/.jenv/versions
# jenv add /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
# jenv add /Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home

#方案二 在 shell 的配置文件中以指定 alias 的方式简化切换命令
# JDK 8、JDK 11 的 export 命令
#export JDK8_HOME="/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home"
#export JDK11_HOME="/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home"
# alias 命令链接到 export 命令
#alias jdk8="export JAVA_HOME=$JDK8_HOME"
#alias jdk11="export JAVA_HOME=$JDK11_HOME"
#source ~/.zshrc
#shell中jdk8、jdk11 命令切换不同的JDK版本 同一时间只能存在一个版本
