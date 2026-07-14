# OwnTV汉化版本GitHub部署快速指南

## 第一步：准备工作

### 1.1 准备GitHub账号
1. 访问 https://github.com
2. 登录或注册账号
3. 记下用户名（如：yourusername）

### 1.2 生成GitHub Token
1. 访问 https://github.com/settings/tokens
2. 点击 "Generate new token"
3. 填写Note: "OwnTV汉化版发布"
4. 选择权限: ✅ repo (全选)
5. 点击 "Generate token"
6. **立即复制token**（只会显示一次）：`ghp_xxxxxxxxxxxxxxxxxxxx`

### 1.3 检查本地代码
确保当前目录包含汉化后的OwnTV项目：
```bash
cd OwnTV
ls -la
# 应该能看到: app/, .github/, build-zh.sh等
```

## 第二步：推送到新仓库

### 方法A：使用脚本（推荐）
```bash
# 1. 运行推送脚本
./push-to-github.sh --help

# 2. 完整自动化推送（需要交互输入）
./push-to-github.sh

# 或使用参数
./push-to-github.sh \
  -u yourusername \
  -t ghp_xxxxxxxxxxxxxxxxxxxx \
  -n OwnTV-ZH
```

### 方法B：手动步骤
```bash
# 1. 创建新仓库
# 访问 https://github.com/new
# 仓库名: OwnTV-ZH
# 描述: OwnTV汉化版本
# Public, 不添加README

# 2. 重新初始化本地仓库
git init
git branch -M main
git add .
git commit -m "OwnTV完整汉化版本"

# 3. 添加远程仓库
git remote add origin https://github.com/yourusername/OwnTV-ZH.git

# 4. 推送代码（首次需要token）
git push -u origin main
# 用户名: yourusername
# 密码: ghp_xxxxxxxxxxxxxxxxxxxx
```

## 第三步：使用GitHub仓库

### 3.1 查看仓库
- 访问：`https://github.com/yourusername/OwnTV-ZH`
- 确认所有文件已上传

### 3.2 触发第一次编译
1. 进入 "Actions" 标签页
2. 找到 "汉化版本编译" 工作流
3. 点击 "Run workflow"
4. 选择构建类型: release
5. 等待5-10分钟
6. 下载APK文件

### 3.3 设置自动化（可选）
1. 进入 "Settings" → "Branches"
2. 添加 "main" 分支保护规则：
   - ✅ Require pull request reviews
   - ✅ Dismiss stale reviews
   - ✅ Require status checks
   - ✅ Include administrators

## 第四步：日常维护

### 4.1 后续推送更新
```bash
# 提交更改
git add .
git commit -m "更新汉化: 描述更改内容"
git push origin main

# GitHub会自动编译新版本
```

### 4.2 同步官方更新
```bash
# 添加上游仓库
git remote add upstream https://github.com/AvalonO98/OwnTV.git

# 获取官方更新
git fetch upstream

# 合并到本地
git merge upstream/main

# 解决冲突后推送
git push origin main
```

### 4.3 从仓库下载代码
```bash
# 克隆汉化仓库
git clone https://github.com/yourusername/OwnTV-ZH.git
cd OwnTV-ZH

# 本地编译测试
./build-zh.sh --type debug
```

## 重要提示

### 安全提醒：
- ⚠️ **Token保密**: GitHub Token具有仓库完全权限，不要泄露
- ⚠️ **不要提交Token**: 不要将Token写入代码或配置文件
- ⚠️ **定期更换Token**: 建议每90天更换一次

### 最佳实践：
1. ✅ 使用 `push-to-github.sh` 脚本自动化
2. ✅ 在GitHub设置中启用2FA
3. ✅ 定期备份本地代码
4. ✅ 关注官方OwnTV更新

### 常见问题：

**Q: 推送失败，显示权限错误**
A: 检查Token是否有repo权限，用户名是否正确

**Q: GitHub Actions编译失败**
A: 检查Actions日志，通常是依赖下载问题

**Q: 如何删除错误提交的敏感信息**
A: 使用git filter-branch或BFG工具清理历史

**Q: 想添加协作者**
A: 仓库Settings → Collaborators → Add people

## GitHub Actions使用

### 手动编译：
```
https://github.com/yourusername/OwnTV-ZH/actions/workflows/zh-build.yml
→ Run workflow → 选择类型 → Run workflow
```

### 预发布下载：
```
https://github.com/yourusername/OwnTV-ZH/releases
→ 下载最新APK
```

### 固定链接（总是最新版）：
```
https://github.com/yourusername/OwnTV-ZH/releases/latest/download/OwnTV.apk
```

## 仓库结构说明

```
OwnTV-ZH/
├── .github/workflows/         # GitHub Actions配置
│   ├── zh-build.yml           # 汉化编译工作流
│   └── android.yml            # 原版编译工作流
├── app/                       # Android应用代码
│   └── src/main/java/tv/own/owntv/
│       ├── features/shell/    # 汉化的UI界面
│       └── features/home/     # 主页汉化
├── build-zh.sh               # 本地编译脚本
├── push-to-github.sh         # 推送脚本
├── i18n-zh-summary.md        # 汉化总结
└── zh-build-guide.md         # 编译指南
```

## 获取帮助

1. **GitHub Issues**: 在仓库Issues页面提问
2. **Actions日志**: 详细的编译过程日志
3. **官方文档**: https://docs.github.com

---

✅ 完成以上步骤，你就拥有了一个：
- 全中文界面的OwnTV项目
- 自动编译APK的GitHub仓库
- 持续更新的汉化版本
- 可供他人使用的开源项目