launchctl是一个统一的服务管理框架，可以启动、停止和管理守护进程、应用程序、进程和脚本等。 launchctl是通过配置文件来指定执行周期和任务的。

/Library/LaunchDaemons -->只要系统启动了，哪怕用户不登录系统也会被执行

进入/Library/LaunchDaemons 创建一个plist文件com.lexiangappstore.plist

####  授权

sudo chown root:wheel /Library/LaunchDaemons/com.lexiangappstore.plist

####  注册加载任务, -w选项会将plist文件中无效的key覆盖掉，建议加上

launchctl load -w /Library/LaunchDaemons/com.lexiangappstore.plist

####  开始任务立即运行

launchctl start /Library/LaunchDaemons/com.lexiangappstore.plist

####  AppStore文件夹权限 定时任务写操作
sudo -i 到 root  改写plist定时任务文件
cd /Users/用户名 && sudo chmod -R 777 AppStore/

####  删除任务

launchctl unload -w /Library/LaunchDaemons/com.lexiangappstore.plist

####  查看任务列表, 使用 grep '任务部分名字' 过滤

launchctl list | grep 'com.lexiangappstore.plist'

####  结束任务

launchctl stop /Library/LaunchDaemons/com.lexiangappstore.plist

####  查看启动日志

cat /var/log/system.log | grep 'com.lexiangappstore.plist'
