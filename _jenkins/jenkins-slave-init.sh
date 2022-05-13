#!/usr/bin/env bash
# Author: 潘维吉
# Description:  jenkins分布式节点环境初始化

if [[ ! $(command -v git) ]]; then
  echo "安装git"
  yum install -y git
  git --version
  which git
fi

if [[ ! $(command -v java) ]]; then
  echo "安装jdk"
  # yum install -y java-11-openjdk-devel.x86_64 # java-1.8.0-openjdk-devel.x86_64
  # sudo apt update
  sudo apt install -y openjdk-11-jdk || true
  java -version
  which java
fi

if [[ ! $(command -v node) ]]; then
  echo "安装nodejs"
  #  curl -sL https://deb.nodesource.com/setup_14.x | sudo -E bash -
  #  sudo apt -y install nodejs
  curl -sL https://rpm.nodesource.com/setup_14.x | sudo bash -
  yum install -y nodejs
  node -v && npm -v
  which node
fi

if [[ ! $(command -v mvn) ]]; then
  echo "安装maven" # export HOMEBREW_BOTTLE_DOMAIN=''
  mkdir -p /opt/maven && cd /opt/maven && wget --no-check-certificate https://mirror.its.dal.ca/apache/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz
  tar -xzvf apache-maven-3.6.3-bin.tar.gz
  # 写入数据到文件输出重定向 双 >> 是追加 , 单 > 是覆盖
  # export JAVA_HOME=/usr/bin/java
  echo '
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export JRE_HOME=$JAVA_HOME/jre
export CLASSPATH=$JAVA_HOME/lib:$JRE_HOME/lib:$CLASSPATH
export MAVEN_HOME=/opt/maven/apache-maven-3.6.3
export PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH
' >>/etc/profile
  . /etc/profile
  mvn -version
fi
echo $MAVEN_HOME && echo $JAVA_HOME && echo $PATH

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
#安装 Android SDK
sdkmanager  "platform-tools" "platforms;android-30" "build-tools;30.0.2" "ndk;21.0.6113669"
adb --version

# api-versions.xml文件不再存在
mkdir $ANDROID_HOME/platform-tools/api/
cp $ANDROID_HOME/platforms/android-30/data/api-versions.xml $ANDROID_HOME/platform-tools/api/

sdkmanager --update
fi
echo $ANDROID_HOME && echo $JAVA_HOME && echo $PATH

# MacOS jenkins节点管理远程工作目录 为 MacOS设置./jenkins  Linux设置/my/jenkins
# Flutter官网安装文档: https://docs.flutter.dev/get-started/install

# MacOS安装Android SDK命令文档: https://proandroiddev.com/how-to-setup-android-sdk-without-android-studio-6d60d0f2812a
# MacOS 安装 Android SDK  用Android Studio内安装Android SDK
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
