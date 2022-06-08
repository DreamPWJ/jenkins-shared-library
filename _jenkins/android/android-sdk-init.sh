#!/usr/bin/env bash
# Author: 潘维吉
# Description: Linux系统 Android SDK 初始化

if [[ ! $(command -v java) ]]; then
  echo "安装jdk"
  # yum install -y java-11-openjdk-devel.x86_64 # java-1.8.0-openjdk-devel.x86_64
  # sudo apt update
  sudo apt install -y openjdk-11-jdk || true
  java -version
  which java
fi

echo $JAVA_HOME && echo $PATH

if [[ ! $(command -v adb) ]]; then
  echo "安装Android SDK"
  # 下载命令工具: https://developer.android.com/studio#command-tools
  mkdir android && cd android
  wget https://dl.google.com/android/repository/commandlinetools-linux-8512546_latest.zip

  unzip commandlinetools-linux-8512546_latest.zip
  rm commandlinetools-linux-8512546_latest.zip

  cd cmdline-tools
  mkdir tools
  mv -i * tools

  echo '
  export ANDROID_HOME=/my/android
  export PATH=$ANDROID_HOME/cmdline-tools/tools/bin/:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools/:$PATH
' >>/etc/profile
  . /etc/profile

  sdkmanager
  sdkmanager --list
  #安装 Android SDK
  sdkmanager "platform-tools" "platforms;android-30" "build-tools;30.0.2" "ndk;21.0.6113669"
  adb --version

  # api-versions.xml文件不再存在
  mkdir $ANDROID_HOME/platform-tools/api/
  cp $ANDROID_HOME/platforms/android-30/data/api-versions.xml $ANDROID_HOME/platform-tools/api/

  sdkmanager --update
fi
echo $ANDROID_HOME && echo $JAVA_HOME && echo $PATH

# Linux安装Android SDK命令文档: https://proandroiddev.com/how-to-setup-android-sdk-without-android-studio-6d60d0f2812a
# Linux安装Android SDK  用Android Studio内安装Android SDK
# adb --version
