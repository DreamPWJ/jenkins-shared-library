# Spaceship太空船同时公开了Apple Developer Center和App Store Connect API。它超快速，经过良好测试，并支持您可以通过浏览器执行的所有操作。
# 它为Fastlane的某些部分提供动力，并可用于更高级的fastlane功能。编写Developer Center工作流程的脚本从未如此简单
# 文档地址: https://github.com/fastlane/fastlane/blob/master/spaceship/docs/AppStoreConnect.md
# 终端执行  ruby spaceship.rb

# sudo gem install spaceship dingbot json
require 'spaceship'
require 'dingbot'

# Author: 潘维吉
# Description: 自定义Apple Store审核状态变更自动通知

puts ARGV.to_s

SYSTEM_HOME = ENV['HOME'] || "/Users/panweiji"

token = Spaceship::ConnectAPI::Token.create(
  key_id: ARGV[1] || 'BNL32GR27G',
  issuer_id: ARGV[2] || 'a0cae47e-ca4b-4503-a967-4282302c0c5a',
  filepath: File.absolute_path(SYSTEM_HOME + (ARGV[3] || "/Library/AuthKey_BNL32GR27G.p8"))
)

Spaceship::ConnectAPI.token = token

# Find a specific app based on the bundle identifier
# app对象源码地址  https://github.com/fastlane/fastlane/blob/76f9fc411118d51c2b715152d6926d273978e3dc/spaceship/lib/spaceship/connect_api/models/app.rb
app = Spaceship::ConnectAPI::App.find(ARGV[0])

# app.get_edit_app_store_version # the version that's in `Prepare for Submission` mode
# app.get_live_app_store_version # the version that's currently available in the App Store
app_store_version = app.get_edit_app_store_version || app.get_latest_app_store_version || app.get_live_app_store_version

# p app
# p app_store_version

puts "#{app.id}"
puts "#{app.name}"
puts "#{app.bundle_id}"
puts "#{app_store_version.version_string}"
# build_version = app_store_version.build.nil? ? app.get_live_app_store_version.build.version.to_f + 1 : app_store_version.build.version
# puts "#{build_version}"
puts "#{app_store_version.app_store_state}" # PREPARE_FOR_SUBMISSION / WAITING_FOR_REVIEW / IN_REVIEW  / PENDING_DEVELOPER_RELEASE / PROCESSING_FOR_APP_STORE / READY_FOR_SALE / REJECTED / METADATA_REJECTED / DEVELOPER_REJECTED
puts "#{app_store_version.release_type}" # MANUAL / AFTER_APPROVAL / SCHEDULED
# uploaded_date = Time.parse(app_store_version.build.nil? ? app.get_latest_app_store_version.created_date : app_store_version.build.uploaded_date).localtime.strftime("%Y-%m-%d %H:%M")
# puts "#{uploaded_date}"

# 文档地址: https://github.com/thierryxing/dingtalk-bot
# 局部配置方式
DingBot.endpoint = 'https://oapi.dingtalk.com/robot/send'
DingBot.access_token = 'ac8c99031afc2d0973bfd4c15b7f80e66a1b4dc11a5371236723b35476276b3a' # 7e0a34d57be41808ab02b1955ed2f19d64d1fbd95e521331eff8cfe16e05b861

# 发送独立跳转ActionCard类型消息
def send_independent_action_card(id, name, version, app_store_state, release_type)
  message = DingBot::Message::IndependentActionCard.new(
    "#{name} v#{version} App Store审核上架通知 乐享科技",
    '![screenshot](@lADOpwk3K80C0M0FoA)**' + name + ' iOS v' + version + '版本, 审核上架状态为: ' + app_store_state + '** 。
    上架类型: ' + release_type + ' ,  请及时处理 !  🏃' + "@" + (ARGV[4] || ""),
    [
      DingBot::Message::ActionBtn.new('App Store Connect', 'https://appstoreconnect.apple.com/apps/'),
      DingBot::Message::ActionBtn.new('App Store应用主页', 'https://apps.apple.com/cn/app/apple-store/id' + id)
    ],
    [(ARGV[4] || "")]
  )
  DingBot.send_msg(message)
end

# 检测通知唯一次数 是否通知
def is_notice(id, name, version, build_version, app_store_state)

  begin
    unique_id = id + '-' + version + '-' + build_version.to_s + '-' + app_store_state
    file_name = SYSTEM_HOME + "/AppStore/is_notice.txt"

    content_file = File.new(file_name, "r+")

    content = ""
    IO.foreach(file_name) { |block|
      puts block
      content = block
    }

    if content_file
      if content.include?(unique_id) == false
        content_file.syswrite(content + "," + unique_id)
      end
    else
      puts "无法打开文件 !"
    end

    return content.include?(unique_id) ? false : true

  rescue => ex
    print ex.message, "\n"
  end

end

# 审核通过和被拒绝 钉钉群通知
if app_store_version.app_store_state == 'PENDING_DEVELOPER_RELEASE' || app_store_version.app_store_state == 'READY_FOR_SALE' || app_store_version.app_store_state.include?('REJECTED')
  app_store_state = "#{app_store_version.app_store_state} #{app_store_version.app_store_state.include?("REJECTED") ? '❌' : '✅' }"
  puts "审核状态: #{app_store_state}"
  is_notice = is_notice(app.id, app.name, app_store_version.version_string, "1.0", app_store_version.app_store_state)

  if (is_notice)
    puts "钉钉通知"
    send_independent_action_card(app.id, app.name, app_store_version.version_string,
                                 app_store_state, app_store_version.release_type)
  end

  if app_store_version.app_store_state == 'READY_FOR_SALE'
    delete_file_name = SYSTEM_HOME + "/AppStore/app-store.sh"
    clean_job_file = SYSTEM_HOME + "/AppStore/clean-job.sh"
    if File.exist?(delete_file_name)
      puts "上架成功后删除具体的执行任务"
      # 同时支持多个并行Apple Store审核状态通知  清除定时任务的具体Job
      exec ".#{clean_job_file} #{ARGV[0]} #{delete_file_name}"
      # Ruby执行shell命令
      # exec 'echo "hello world"'
    end
  end

end



