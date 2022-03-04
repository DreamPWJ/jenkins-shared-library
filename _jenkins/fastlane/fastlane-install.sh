#!/usr/bin/env bash
# Author: 潘维吉
# Description:  Fastlane MacOs环境初始化

echo "安装ruby"
ruby -v && gem -v

echo "brew安装工具"
if [[ ! $(command -v brew) ]]; then
  /usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
fi
brew info && brew update

if [[ ! $(command -v xcode-select) ]]; then
  echo "安装Xcode"
  xcode-select --install
fi

if [[ ! $(command -v fastlane) ]]; then
  echo "安装Fastlane"
  sudo gem install fastlane
  cd ~/ && touch .bash_profile
  open .bash_profile
  export PATH="$HOME/.fastlane/bin:$PATH"
  source .bash_profile
  fastlane --version
  # gem update fastlane
fi

echo "设置终端语言、地区等国际化环境变量"
