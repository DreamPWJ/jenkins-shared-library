#!/usr/bin/ruby

# Ruby执行shell命令
# exec 'echo "hello world"'
appId = "AnTaiKeji.com.saasAnjiaHuaXin"
puts  " ./clean-job.sh #{appId} "
exec " ./clean-job.sh #{appId} "

# ruby /Users/liming/AppStore/spaceship.rb "AnTaiKeji.com.saasAnjiaHuaXin" "BNL32GR27G" "a0cae47e-ca4b-4503-a967-4282302c0c5a" "/Library/AuthKey_BNL32GR27G.p8" "15063309617"
