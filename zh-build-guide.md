# OwnTV 汉化版本 GitHub 云编译指南

## 概述

本指南介绍如何使用GitHub Actions自动编译汉化后的OwnTV应用为APK文件，无需本地开发环境。

## 两种编译方式

### 1. 手动触发编译（推荐）

**步骤：**
1. 访问项目的 [Actions 页面](https://github.com/[你的用户名]/OwnTV/actions)
2. 选择 **"汉化版本编译"** 工作流程
3. 点击 **"Run workflow"** 按钮
4. 选择构建类型（debug 或 release）
5. 点击 **"Run workflow"** 开始编译

**特点：**
- 无需提交代码
- 可选择构建类型
- 立即开始编译

### 2. 自动触发编译

**触发条件：**
0. 推送代码到 `main` 分支
- 创建Pull Request
- 手动触发（同上）

**工作流程：**
```yaml
on:
  push:
    branches: [ "main" ]
  workflow_dispatch:    # 手动触发
  pull_request:        # PR触发
```

## 编译产物

编译完成后，可以获取以下文件：

### 在Actions页面获取：
1. 进入完成的构建工作流程
2. 点击 **"Artifacts"** 下拉菜单
3. 下载 **"汉化版本构建"** 压缩包
4. 解压后包含：
   - `OwnTV-zh-[类型]-[版本].apk` - APK安装文件
   - `汉化说明.md` - 汉化版本说明
   - `文件清单.txt` - 文件列表

### 在Releases页面获取（仅主分支推送）：
1. 访问 [Releases 页面](https://github.com/[你的用户名]/OwnTV/releases)
2. 找到以 `zh-` 开头的标签
3. 下载附件中的APK文件

## 版本命名规则

GitHub编译的汉化版本使用特殊版本号格式：

```
格式：zh-YYYYMMDD.HHMM-commit-hash
示例：zh-20240715.1430-a1b2c3d

说明：
- zh          : 汉化版本标识
- YYYYMMDD    : 构建日期
- HHMM        : 构建时间（24小时制）
- commit-hash : Git提交哈希前7位
```

## 本地使用编译脚本

项目包含一个完整的编译脚本，也可在本地运行：

### 本地编译要求：
- Java JDK 21
- Android SDK
- Git

### 常用命令：
```bash
# 显示帮助
./build-zh.sh --help

# 构建发布版本
./build-zh.sh --type release

# 构建调试版本
./build-zh.sh --type debug

# 清理并构建
./build-zh.sh --clean --type release

# 设置环境
./build-zh.sh --setup
```

## 汉化内容

编译的汉化版本包含以下界面翻译：

### 主要界面
- ✅ **导航菜单**: 搜索、首页、直播、电影、剧集、下载、节目指南、设置
- ✅ **设置界面**: 用户档案、播放列表、节目指南源、主题、备份、视频播放器、个性化等12个类别
- ✅ **按钮开关**: 直播预览、预览声音、自动播放、启动检查更新
- ✅ **对话框**: 退出确认、缩放警告、回看时间设置
- ✅ **侧边栏**: 用户信息、切换用户、浏览
- ✅ **主页**: 持续观看、最近频道、收藏频道、继续观看电影、继续观看剧集

### 技术保持
- 🔧 **技术术语**: HDR、PIN、M3U、Xtream、XMLTV等保持英文
- 🔧 **代码结构**: 所有变量名、函数名、类名不变
- 🔧 **注释日志**: 开发相关的注释和日志保持英文

## 安装到设备

### Android TV安装：
1. 在电视上安装 **"Downloader"** 应用
2. 在Downloader中输入APK下载链接
3. 下载完成后点击安装
4. 确保开启 **"未知来源"** 权限

### 手机/平板安装：
1. 下载APK到设备
2. 使用文件管理器找到APK文件
3. 点击安装，按提示操作

### 模拟器安装：
```bash
# 使用adb安装
adb install OwnTV-zh-*.apk

# 重新安装（覆盖）
adb install -r OwnTV-zh-*.apk
```

## 常见问题

### Q1: 编译失败怎么办？
**A:** 检查：
1. GitHub Actions日志中的具体错误信息
2. 确保项目代码完整
3. 检查是否有编译配置错误

### Q2: APK安装失败？
**A:** 可能原因：
1. 设备Android版本低于26（Android 8.0）
2. 未开启"未知来源"安装权限
3. 已有其他版本冲突，请先卸载

### Q3: 如何获取最新版本？
**A:** 访问：
```
https://github.com/[你的用户名]/OwnTV/releases/latest/download/OwnTV.apk
```
此链接始终指向最新的汉化发布版本

### Q4: 编译时间多久？
**A:** 通常需要5-10分钟，包括：
- 环境设置：2-3分钟
- 依赖下载：1-2分钟
- 实际编译：2-5分钟

## 自定义配置

### 修改构建类型：
在工作流程文件中可以修改：
```yaml
# .github/workflows/zh-build.yml
env:
  BUILD_TYPE: "release"  # 改为 "debug" 或 "release"
```

### 添加签名：
如需发布签名版本，在GitHub仓库设置中添加：
- `KEYSTORE_BASE64` - Base64编码的签名密钥
- `KEYSTORE_PASSWORD` - 密钥库密码
- `KEY_ALIAS` - 密钥别名
- `KEY_PASSWORD` - 密钥密码

## 贡献与反馈

### 报告问题：
1. 在GitHub Issues中创建新问题
2. 描述具体问题和复现步骤
3. 附上相关日志和截图

### 改进汉化：
1. 直接编辑相关Kotlin文件中的字符串
2. 提交Pull Request
3. GitHub会自动编译测试版本

---

**提示:** 每次编译都会生成详细的构建报告，包含版本信息、文件信息和安装说明，请仔细阅读。