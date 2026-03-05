<div align="center">

# One For All DevOps MonoRepo

[English](./README.en.md) | [简体中文](./README.md)

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Jenkins](https://img.shields.io/badge/Jenkins-Pipeline-blue?logo=jenkins)](https://jenkins.io/)
[![CI/CD](https://img.shields.io/badge/CI%2FCD-Auto-blue?logo=github-actions)]()
[![K8s](https://img.shields.io/badge/Kubernetes-Ready-blue?logo=kubernetes)](https://kubernetes.io/)
[![Docker](https://img.shields.io/badge/Docker-Support-blue?logo=docker)](https://docker.com/)

[📖 Documentation](https://deepwiki.com/DreamPWJ/jenkins-shared-library)

**Powerful · Flexible · Easy-to-use · Cross-platform DevOps Solution**

</div>

---

## 🎯 Original Intention

> **Take from open source, give back to open source, give roses to others, and leave fragrance in hand**
>
> Welcome everyone to contribute PRs to build the ecosystem. Pipelines have passed **tens of thousands** of enterprise-level builds

### Vision: All In One

Design a general and fully automated DevOps system to improve the efficiency and quality of product delivery, truly improve the quality and efficiency of each end, and ultimately improve the user experience to achieve the best balance between **DX** and **UX**.

- 🧑‍💻 **Developer Experience** - Let developers focus on creative and interesting work, hand over repetitive work to the machine
- 👤 **User Experience** - Ensure high-quality and stable development of products to create greater value
- 🔄 **Continuous Iteration** - Continuous iteration and maintenance is the foundation and advantage of this project, making the software have endless vitality

---

## 📱 Supported Pipeline Types

<div align="center">

| Type | Technology Stack | Diagram |
|:---:|:---|:---:|
| **📱 APP** | Android、iOS、Flutter、React Native、Unity | ![app](./docs/images/app.png) |
| **🖥️ Server** | Java、Go、Python、C++ and more | ![server](./docs/images/img.png) |
| **💡 Mini Program** | Native、Taro、uni-app、mpvue、Remax | ![mini](./docs/images/mini.png) |
| **🖥️ Desktop** | Electron、Unity (Windows、MacOS、Linux) | ![desktop](./docs/images/desktop.png) |
| **🌐 Web** | Npm、Static Web、SSR、Flutter/React Native/Unity For Web、WebAssembly | ![web](./docs/images/web.png) |
| **🔗 IoT** | Embedded、VR/AR/XR、MetaVerse | ![iot](./docs/images/iot.png) |

</div>

---

## ✨ Features

### Supported Platforms

| Platform | Technology Stack |
|:---|:---|
| 📱 **Mobile** | Android、iOS、Flutter、React Native |
| 💡 **Mini Program** | Native、Cross-platform Mini Programs |
| 🖥️ **Frontend** | JavaScript Static Web、Multi-version Node、Mainstream Frameworks |
| ⚙️ **Server** | Multi-version Java、Go、Python、C++ |
| 🖥️ **Desktop** | Electron、Unity (Windows、MacOS、Linux) |
| 🔗 **IoT** | Embedded (PlatformIO、Arduino) |
| 🎮 **Visual Engine** | Unity、WebGL、Cocos |

### Repository & Deployment

| Category | Support Content |
|:---|:---|
| **Repository** | MonoRepo (Single warehouse multi-package), MultiRepo (Multi-warehouse single package) |
| **Deployment** | Stand-alone, Mirror warehouse, Blue-green, Rolling, Distributed, K8S cluster auto-scaling, One-click rollback, Automatic review and listing, Springboard transmission |
| **App Market** | App Store, Mini Program Platform, Huawei Store, Xiaomi Store, Self-built OSS, Firebase, Dandelion, Fir |
| **Notification** | Release results, Release logs, Audit status |
| **Operating System** | MacOS、Linux |

---

## 🏗️ Project Directory

```
jenkins-shared-library/
├── .ci/                    # Container deployment scripts and configurations
├── pipelines/              # Pipeline configuration file entry
├── resources/              # Non-Groovy file storage (loaded via libraryResource)
├── src/                    # Pipeline general classes, utilities and constants
├── vars/                   # Core Pipeline workflows and documentation
├── docs/                   # Documentation
├── _jenkins/               # Jenkins related configurations
├── _docker/                # Docker related configurations
├── _k8s/                   # Kubernetes related configurations
├── _linux/                 # Linux related scripts and configurations
├── _nginx/                 # Nginx related configurations
├── _database/              # Database related configurations
├── Jenkinsfile.*           # Base variable config files for different environments/projects
└── README.md               # Project documentation
```

---

## 🔧 Technology Stack

| Category | Technology |
|:---|:---|
| **Core** | Jenkins Pipeline, Groovy, Python |
| **Container** | Kubernetes, Docker, Shell |
| **Mobile** | Fastlane, Ruby |
| **Frontend** | Node.js, Playwright, C# |

---

## 📖 Usage Guide

### Quick Start

> For detailed documentation, please refer to [Online Documentation](https://deepwiki.com/DreamPWJ/jenkins-shared-library)

### Core Concepts

- **Pipeline Shared Library** - Can be imported in Jenkins via `@Library('my-shared-library')`
- **Dynamic Library Loading** - Import via `library('my-shared-library').shared.library.Utils.getVersionNum()`
- **Third-party Dependencies** - Support `@Grab` annotation to use third-party libraries

### Debugging Tips

- Use Jenkins built-in **Replay** feature for debugging to reduce frequent code commits

---

## 🤝 Contributing

We welcome contributions in various forms:

- 🐛 Submit bug reports
- 💡 Propose feature suggestions
- 📝 Improve documentation
- 🔀 Submit PRs to fix issues or add new features

---

## 👥 Core Contributors

- **Pan WeiJi** - Project Author

---

## 📄 License

This project is open source under the [MIT](LICENSE) license.

---

<div align="center">

**⭐ If this project helps you, please give it a Star!**

</div>