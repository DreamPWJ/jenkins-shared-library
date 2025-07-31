### sharedLibrary全局共享库文档  面向用户的文档
- Pipeline方式可使用Jenkins自带回放功能进行调试 减少代码频繁提交
- 动态库加载 library('my-shared-library').shared.library.Utils.getVersionNum() 方式引入 可获取到库中的任何全局变量
- @Grab使用第三方库
