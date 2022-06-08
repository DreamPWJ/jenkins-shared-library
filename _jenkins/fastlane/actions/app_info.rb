# 弃用ruby语言版本 已使用JS语言方案实现
# sudo gem install app-info
# sudo gem install app-info -v 2.7.0 从2.7.0开始支持android的aab新格式
# 可能兼容问题 sudo gem uninstall google-protobuf && sudo gem install google-protobuf -v 3.18.2 --platform=ruby
# 终端执行  ruby app_info.rb
require 'app-info'

# Author: 潘维吉
# Description: 获取APP包信息

puts ARGV.to_s

# Automatic detect file extsion and parse
app = AppInfo.parse(ARGV[0])

# 属性文档： https://github.com/icyleaf/app_info/tree/master/spec/app_info
app_name = app.name
app_version = app.release_version
app_build_version = app.build_version
app_size = app.size(human_size: true)
app_identifier = app.bundle_id
app_os = app.os

app_info = "#{app_name},#{app_version},#{app_size},#{app_build_version},#{app_identifier},#{app_os}"
# Fastlane与Jenkins数据通信 使用存到文件再读取文件的方式
File.write(ARGV[1], "#{app_info}")
