# 快速上手

> 一文读懂如何使用本 DevOps 流水线共享库

---

## 📖 简介

本项目是一个基于 **Jenkins Pipeline 共享库** 实现的跨端 CI/CD 解决方案，经过 **几万次** 企业级构建考验。

### 核心理念

- **取之开源，回馈开源** - 站在巨人的肩膀上，为开源社区贡献力量
- **DX × UX 最佳平衡** - 为开发者提供愉悦的开发体验，为用户提供优良的产品体验
- **持续迭代** - 让软件拥有生生不息的生命力

---

## 🎯 支持的技术栈

| 平台 | 技术支持 |
|:---|:---|
| 📱 **移动端** | Android、iOS、Flutter、React Native |
| 💡 **小程序** | 原生小程序、Taro、uni-app、mpvue、Remax |
| 🖥️ **服务端** | Java、Go、Python、C++ |
| 🌐 **Web** | 静态 Web、SSR、Flutter/React Native/Unity For Web、WebAssembly |
| 🖥️ **桌面端** | Electron、Unity (Windows/MacOS/Linux) |
| 🔗 **IoT** | 嵌入式 (PlatformIO/Arduino)、VR/AR/XR |
| 🎮 **视觉引擎** | Unity、WebGL、Cocos |

---

## 🏗️ 工程结构

```
jenkins-shared-library/
├── .ci/                    # 容器部署脚本和配置
├── pipelines/              # 流水线配置入口
├── resources/              # 资源文件 (通过 libraryResource 加载)
├── src/                    # 通用类、工具类、常量封装
├── vars/                   # 核心 Pipeline 工作流和全局变量
├── Jenkinsfile.*           # 不同项目的变量配置文件
└── docs/                   # 文档
```

---

## 🚀 快速开始

### 前置条件

1. **Jenkins** - 2.300 或更高版本
2. **Pipeline 插件** - 已安装
3. **共享库权限** - 已配置访问权限

### 配置共享库

1. 进入 **Jenkins 系统管理** → **系统配置**
2. 找到 **Pipeline 共享库** 配置项
3. 添加新的共享库:
   - **名称**: `my-shared-library`
   - **默认分支**: `master`
   - **代码仓库**: `https://github.com/DreamPWJ/jenkins-shared-library`

### 使用流水线

在 Jenkins Pipeline 中引入共享库:

```groovy
@Library('my-shared-library') _

// 使用共享库中的功能
library('my-shared-library').shared.library.Utils.getVersionNum()
```

### 配置项目

选择适合你项目的 Jenkinsfile 配置文件:

| 项目类型 | 配置文件 |
|:---|:---|
| APP 应用 | `Jenkinsfile.app` |
| Flutter | `Jenkinsfile.flutter` |
| Java 后端 | `Jenkinsfile.java` |
| Go 后端 | `Jenkinsfile.go` |
| Python | `Jenkinsfile.python` |
| C++ | `Jenkinsfile.cpp` |
| Web 前端 | `Jenkinsfile.web` |
| 小程序 | `Jenkinsfile.mini` |
| 桌面应用 | `Jenkinsfile.desktop` |
| Unity | `Jenkinsfile.unity` |
| IoT | `Jenkinsfile.iot` |
| MonoRepo | `Jenkinsfile.monorepo` |

---

## 🔧 部署方式

支持多种部署策略:

| 部署方式 | 适用场景 |
|:---|:---|
| 单机部署 | 小型项目 |
| 镜像仓库 | 容器化项目 |
| 蓝绿部署 | 零停机发布 |
| 滚动部署 | 渐进式发布 |
| K8s 集群 | 大规模微服务 |
| 一键回滚 | 快速故障恢复 |

---

## 📦 应用市场上架

支持自动提审上架到以下平台:

- **App Store** - iOS 应用商店
- **小程序平台** - 微信/支付宝/百度等小程序
- **华为商店** - 华为应用市场
- **小米商店** - 小米应用商店
- **Firebase** - Google 移动应用平台
- **蒲公英/Fir** - 国内应用分发平台

---

## 📢 通知能力

流水线运行结果通知:

- ✅ 发布结果通知
- 📋 发布日志
- 🔍 审核状态更新

---

## 🛠️ 调试技巧

### Pipeline 回放

Jenkins 提供 **回放 (Replay)** 功能，可以在不提交代码的情况下调试 Pipeline:

1. 进入流水线页面
2. 点击左侧菜单 **回放**
3. 修改 Pipeline 脚本
4. 点击 **运行** 测试

### 使用 @Grab 引入第三方库

```groovy
@Grab('org.apache.commons:commons-lang3:3.12.0')
import org.apache.commons.lang3.StringUtils

// 使用第三方库功能
```

---

## 📖 更多文档

- [Vars 目录文档](../vars/sharedLibrary.md)
- [Pipelines 配置](../pipelines/README.md)
- [在线文档](https://deepwiki.com/DreamPWJ/jenkins-shared-library?lang=zh-CN)

---

## 🤝 参与贡献

我们欢迎各种形式的贡献:

- 🐛 提交 Bug 报告
- 💡 提出功能建议
- 📝 改进文档
- 🔀 提交 PR 修复问题或添加新功能

---

## 👥 核心贡献者

**潘维吉** (Pan WeiJi) - 项目作者