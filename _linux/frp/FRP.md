### FRP (fast reverse proxy)内网穿透

- 源码地址: https://github.com/fatedier/frp
- 后台运行和开机自启动配置文档: https://gofrp.org/zh-cn/docs/setup/systemd/
- 自定义后台运行和开机自启动目录: /etc/systemd/system/

#### 下载frp相关平台包到相关目录中  直接执行go原生二进制包程序启动

- 启动服务器：./frps -c ./frps.toml
- 启动客户端：./frpc -c ./frpc.toml

#### 启动frps or frpc
sudo systemctl start frps
#### 停止frp
sudo systemctl stop frps
#### 重启frp
sudo systemctl restart frps
#### 查看frp状态
sudo systemctl status frps
#### 开启自启动frp状态
sudo systemctl enable frps
#### 禁用frp状态
sudo systemctl disable frps