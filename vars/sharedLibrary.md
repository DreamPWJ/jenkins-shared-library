# Shared Library 共享库文档

> Jenkins Pipeline 全局共享库使用指南

---

## 📖 什么是共享库

Jenkins Pipeline 共享库（Shared Library）是一种代码复用机制，允许你将常用的 Pipeline 逻辑封装成可重用的组件。

### 优势

- ✅ **代码复用** - 避免重复代码，一处修改多处生效
- ✅ **易于维护** - 集中管理通用逻辑，降低维护成本
- ✅ **版本控制** - 支持分支管理，可追溯历史变更
- ✅ **动态加载** - 运行时动态引入，灵活性高

---

## 🏗️ 目录结构

```
vars/
├── sharedLibrary.md      # 本说明文档
├── *.groovy              # 全局变量/函数定义
├── tests/                # 测试文件
└── README.md             # 目录说明

src/
├── com/                  # Java/Groovy 类
│   └── devops/
│       └── utils/
│           ├── Utils.groovy      # 通用工具类
│           └── Constants.groovy  # 常量定义
└── ...

resources/                # 非 Groovy 资源文件
└── config/
    └── template.json     # 通过 libraryResource 加载
```

---

## 🚀 使用方法

### 方式一：标准引入

```groovy
// 引入整个共享库
@Library('my-shared-library') _

// 直接使用 vars 目录中定义的变量/函数
mySharedFunction()
```

### 方式二：动态加载

```groovy
// 动态加载特定功能
library('my-shared-library').shared.library.Utils.getVersionNum()

// 获取版本号
def version = library('my-shared-library').shared.library.Utils.getVersionNum()
```

### 方式三：使用第三方库

```groovy
// 使用 @Grab 注解引入 Maven 依赖
@Grab('org.apache.commons:commons-lang3:3.12.0')
@GrabResolver('https://repo1.maven.org/maven2/')

import org.apache.commons.lang3.StringUtils

// 使用第三方库
def result = StringUtils.capitalize("hello")
```

---

## 📝 调试技巧

### 使用 Jenkins 回放功能

Jenkins 提供 **Replay（回放）** 功能，可以在不提交代码的情况下调试 Pipeline：

1. 进入流水线页面
2. 点击左侧菜单 **回放**
3. 修改 Pipeline 脚本或共享库代码
4. 点击 **运行** 测试

> 💡 **提示**: 使用回放功能可以减少代码频繁提交，提高调试效率

### 日志输出

```groovy
// 打印普通日志
println "普通日志信息"

// 打印带时间戳的日志
echo "[${new Date()}] 带时间戳的日志"

// 打印错误日志
error "错误信息"
```

---

## 🔧 常用工具函数

### 版本管理

```groovy
// 获取版本号
def version = library('my-shared-library').shared.library.Utils.getVersionNum()
```

### 通知发送

```groovy
// 发送钉钉通知
sendDingTalkMessage("构建成功")

// 发送带日志的通知
sendDingTalkWithLog(buildId, status, logUrl)
```

### 代码检查

```groovy
// 执行代码质量检查
runCodeQualityCheck()

// 执行单元测试
runUnitTest()
```

---

## 📚 资源文件加载

使用 `libraryResource` 步骤加载非 Groovy 文件:

```groovy
// 加载 JSON 配置文件
def config = libraryResource('config/template.json').readJSON()

// 加载文本文件
def content = libraryResource('templates/email.txt')
```

---

## 🤝 贡献指南

欢迎贡献更多实用工具和函数：

1. 在 `vars/` 目录添加新的全局变量/函数
2. 在 `src/` 目录添加新的工具类
3. 编写对应的测试用例
4. 更新本文档

---

## 👥 维护者

**潘维吉** (Pan WeiJi)

---

## 📄 许可证

MIT License