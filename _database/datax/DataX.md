### DataX离线数据同步工具/平台 异构数据源之间高效的数据同步功能

#### 安装DataX

cd /my
wget http://datax-opensource.oss-cn-hangzhou.aliyuncs.com/datax.tar.gz
tar -xzvf datax.tar.gz  && rm -rf /my/datax/plugin/*/._*

cd /my/datax/bin && python2 datax.py /my/datax/job/datax_job.json

#### 参考文章

- https://juejin.cn/post/7077744714954309669

#### Ubuntu系统安装  现在DataX对Python2适配更好

sudo apt install -y python2 && python2 -V