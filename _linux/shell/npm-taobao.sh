# ==========================================================
# NPM
# ==========================================================

npm set registry https://r.registry.npmmirror.com # 注册模块镜像
npm set disturl https://registry.npmmirror.com/dist # node-gyp 编译依赖的 node 源码镜像

## 以下选择添加
npm set sass_binary_site https://registry.npmmirror.com/node-sass # node-sass 二进制包镜像
npm set electron_mirror https://registry.npmmirror.com/electron/ # electron 二进制包镜像
npm set puppeteer_download_host https://registry.npmmirror.com/ # puppeteer 二进制包镜像
npm set chromedriver_cdnurl https://registry.npmmirror.com/chromedriver # chromedriver 二进制包镜像
npm set operadriver_cdnurl https://registry.npmmirror.com/operadriver # operadriver 二进制包镜像
npm set phantomjs_cdnurl https://registry.npmmirror.com/phantomjs # phantomjs 二进制包镜像
npm set selenium_cdnurl https://registry.npmmirror.com/selenium # selenium 二进制包镜像
npm set node_inspector_cdnurl https://registry.npmmirror.com/node-inspector # node-inspector 二进制包镜像

npm cache clean --force # 清空缓存

# ==========================================================
# YARN
# ==========================================================

yarn config set registry https://r.registry.npmmirror.com # 注册模块镜像
yarn config set disturl https://registry.npmmirror.com/dist # node-gyp 编译依赖的 node 源码镜像

## 以下选择添加
yarn config set sass_binary_site https://registry.npmmirror.com/node-sass # node-sass 二进制包镜像
yarn config set electron_mirror https://registry.npmmirror.com/electron/ # electron 二进制包镜像
yarn config set puppeteer_download_host https://registry.npmmirror.com/ # puppeteer 二进制包镜像
yarn config set chromedriver_cdnurl https://registry.npmmirror.com/chromedriver # chromedriver 二进制包镜像
yarn config set operadriver_cdnurl https://registry.npmmirror.com/operadriver # operadriver 二进制包镜像
yarn config set phantomjs_cdnurl https://registry.npmmirror.com/phantomjs # phantomjs 二进制包镜像
yarn config set selenium_cdnurl https://registry.npmmirror.com/selenium # selenium 二进制包镜像
yarn config set node_inspector_cdnurl https://registry.npmmirror.com/node-inspector # node-inspector 二进制包镜像

yarn cache clean # 清空缓存
