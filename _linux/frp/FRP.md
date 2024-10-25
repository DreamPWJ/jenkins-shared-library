### FRP (fast reverse proxy)内网穿透服务  分客户端和服务端(服务端需固定的公网ip)

- 源码地址: https://github.com/fatedier/frp
- 后台运行和开机自启动配置文档: https://gofrp.org/zh-cn/docs/setup/systemd/
- 自定义后台运行和开机自启动目录: /etc/systemd/system/

#### 下载frp相关平台包到相关目录中  直接执行go原生二进制包程序启动并设置开机自启动

- 启动frp服务端:  ./frps -c ./frps.toml
- Linux启动frp客户端: chmod +x frpc && chmod +x frpc.toml &&  ./frpc -c ./frpc.toml
- Windows启动frp客户端: frpc.exe -c frpc.toml

#### 启动frps or frpc

sudo systemctl start frps 或 frpc

#### 停止frp

sudo systemctl stop frps 或 frpc

#### 重启frp

sudo systemctl restart frps 或 frpc

#### 查看frp状态

sudo systemctl status frps 或 frpc

#### 开机自启动frp

sudo systemctl enable frps 或 frpc

#### 禁用frp

sudo systemctl disable frps 或 frpc