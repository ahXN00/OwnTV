#!/usr/bin/env bash
# OwnTV 汉化版本 GitHub 推送脚本
# 自动设置和推送到新的GitHub仓库

set -e  # 出错时退出

# 彩色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

step() {
    echo -e "${CYAN}==>${NC} $1"
}

# 显示帮助
show_help() {
    cat << EOF
OwnTV 汉化版本 GitHub 推送脚本

用法: ./push-to-github.sh [选项]

选项:
  -h, --help          显示此帮助信息
  -r, --repo URL      目标GitHub仓库URL (如: https://github.com/username/OwnTV-ZH.git)
  -n, --name NAME     仓库名称 (如: OwnTV-ZH)
  -u, --user USERNAME GitHub用户名
  -t, --token TOKEN   GitHub Personal Access Token
  --init-only         仅初始化仓库，不推送
  --push-only         仅推送代码，不初始化

环境变量:
  GITHUB_TOKEN        GitHub Personal Access Token
  GITHUB_USER         GitHub用户名
  GITHUB_REPO         目标仓库名称

示例:
  1. 完整流程:
     ./push-to-github.sh -u yourname -t ghp_xxxxxx -n OwnTV-ZH
  
  2. 分步执行:
     ./push-to-github.sh --init-only -n OwnTV-ZH
     ./push-to-github.sh --push-only -r https://github.com/yourname/OwnTV-ZH.git
  
  3. 使用环境变量:
     export GITHUB_TOKEN=ghp_xxxxxx
     export GITHUB_USER=yourname
     export GITHUB_REPO=OwnTV-ZH
     ./push-to-github.sh

GitHub Token权限要求:
  - public_repo (创建和推送公共仓库)
  - 或 full repo (私有仓库)

准备工作:
  1. 在GitHub创建新仓库 (如 OwnTV-ZH)
  2. 生成Personal Access Token
  3. 确保本地有所有汉化文件
EOF
}

# 检查Git
check_git() {
    step "检查Git环境"
    
    if ! command -v git &> /dev/null; then
        error "未安装Git，请先安装Git"
        exit 1
    fi
    
    info "Git版本: $(git --version)"
    
    # 检查当前是否是OwnTV目录
    if [ ! -f "app/build.gradle.kts" ] && [ ! -f "settings.gradle.kts" ]; then
        error "未在OwnTV项目目录中，请在OwnTV目录运行此脚本"
        exit 1
    fi
    
    success "Git环境检查通过"
}

# 检查汉化文件
check_localization_files() {
    step "检查汉化文件"
    
    local missing_files=()
    
    # 检查关键汉化文件
    local important_files=(
        "app/src/main/java/tv/own/owntv/features/shell/ShellViewModel.kt"
        "app/src/main/java/tv/own/owntv/features/shell/components/SettingsScreen.kt"
        ".github/workflows/zh-build.yml"
        "build-zh.sh"
        "i18n-zh-summary.md"
        "zh-build-guide.md"
    )
    
    for file in "${important_files[@]}"; do
        if [ ! -f "$file" ]; then
            missing_files+=("$file")
        fi
    done
    
    if [ ${#missing_files[@]} -gt 0 ]; then
        error "缺少以下重要文件:"
        for file in "${missing_files[@]}"; do
            echo "  - $file"
        done
        exit 1
    fi
    
    # 检查是否有未提交的更改
    if ! git diff --quiet; then
        warning "有未提交的更改，建议先提交"
        info "当前更改:"
        git status --short
    fi
    
    success "汉化文件检查通过"
}

# 获取GitHub仓库信息
get_github_info() {
    step "获取GitHub信息"
    
    # 尝试从环境变量获取
    if [ -z "$GITHUB_REPO" ] && [ -n "$1" ]; then
        GITHUB_REPO="$1"
    fi
    
    if [ -z "$GITHUB_USER" ] && [ -n "$2" ]; then
        GITHUB_USER="$2"
    fi
    
    if [ -z "$GITHUB_TOKEN" ] && [ -n "$3" ]; then
        GITHUB_TOKEN="$3"
    fi
    
    # 提示输入缺少的信息
    if [ -z "$GITHUB_USER" ]; then
        read -p "请输入GitHub用户名: " GITHUB_USER
        export GITHUB_USER
    fi
    
    if [ -z "$GITHUB_REPO" ]; then
        read -p "请输入新仓库名称 (如 OwnTV-ZH): " GITHUB_REPO
        export GITHUB_REPO
    fi
    
    if [ -z "$GITHUB_TOKEN" ]; then
        echo "请创建GitHub Personal Access Token:"
        echo "1. 访问 https://github.com/settings/tokens"
        echo "2. 点击 'Generate new token'"
        echo "3. 选择 'repo' 权限"
        echo "4. 复制生成的token"
        read -s -p "请输入GitHub Token: " GITHUB_TOKEN
        echo
        export GITHUB_TOKEN
    fi
    
    info "GitHub用户: $GITHUB_USER"
    info "仓库名称: $GITHUB_REPO"
    info "Token: $(echo "$GITHUB_TOKEN" | cut -c1-4)..."
    
    success "GitHub信息获取完成"
}

# 验证GitHub Token
validate_github_token() {
    step "验证GitHub Token"
    
    if [ -z "$GITHUB_TOKEN" ]; then
        error "未提供GitHub Token"
        exit 1
    fi
    
    # 测试Token有效性
    local response
    response=$(curl -s -H "Authorization: token $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github.v3+json" \
        "https://api.github.com/user" 2>/dev/null || true)
    
    if echo "$response" | grep -q "Bad credentials"; then
        error "GitHub Token无效或已过期"
        exit 1
    fi
    
    if ! echo "$response" | grep -q '"login"'; then
        warning "无法验证Token，继续但可能失败"
    else
        success "GitHub Token验证通过"
    fi
}

# 创建GitHub仓库
create_github_repo() {
    step "创建GitHub仓库: $GITHUB_REPO"
    
    local repo_name="$GITHUB_REPO"
    if [ -z "$repo_name" ]; then
        read -p "请输入仓库名称: " repo_name
    fi
    
    local description="OwnTV 汉化版本 - 完整中文界面本地化"
    
    info "正在创建仓库: $repo_name"
    
    # 创建仓库的JSON数据
    local repo_data=$(cat << EOF
{
  "name": "$repo_name",
  "description": "$description",
  "private": false,
  "has_issues": true,
  "has_projects": true,
  "has_wiki": true,
  "auto_init": false,
  "gitignore_template": "Android"
}
EOF
    )
    
    # 发送创建请求
    local response
    response=$(curl -s -X POST \
        -H "Authorization: token $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github.v3+json" \
        "https://api.github.com/user/repos" \
        -d "$repo_data" 2>/dev/null || true)
    
    # 检查响应
    if echo "$response" | grep -q '"name"'; then
        success "GitHub仓库创建成功: https://github.com/$GITHUB_USER/$repo_name"
        echo "https://github.com/$GITHUB_USER/$repo_name.git"
    elif echo "$response" | grep -q '"message": "name already exists"'; then
        warning "仓库已存在: $repo_name"
        echo "https://github.com/$GITHUB_USER/$repo_name.git"
    else
        error "创建仓库失败"
        echo "响应: $response"
        exit 1
    fi
}

# 重新初始化Git仓库
reinit_git_repo() {
    step "重新初始化Git仓库"
    
    local repo_url="$1"
    
    # 备份原始.git目录
    if [ -d ".git" ]; then
        info "备份原始Git配置"
        mv .git .git.backup.$(date +%s)
    fi
    
    # 初始化新仓库
    git init
    git branch -M main
    
    # 设置用户信息
    git config user.name "$GITHUB_USER"
    git config user.email "$GITHUB_USER@users.noreply.github.com"
    
    # 添加远程仓库（如果提供了URL）
    if [ -n "$repo_url" ]; then
        git remote add origin "$repo_url"
        info "设置远程仓库: $repo_url"
    fi
    
    success "Git仓库重新初始化完成"
}

# 提交汉化代码
commit_localization() {
    step "提交汉化代码"
    
    # 添加所有文件
    git add .
    
    # 显示将要提交的文件
    info "将要提交的文件:"
    git status --short
    
    # 提交
    if git diff --cached --quiet; then
        warning "没有更改需要提交"
    else
        local commit_msg="完成OwnTV完整汉化

汉化内容:
- 导航菜单: 搜索、首页、直播、电影、剧集、下载、节目指南、设置
- 设置界面: 用户档案、播放列表、节目指南源、主题、备份、视频播放器、个性化等12个类别
- 所有按钮、开关、对话框汉化
- 添加GitHub Actions云编译工作流
- 添加中文编译脚本和文档

技术说明:
- 保留所有技术术语 (HDR, PIN, M3U, Xtream, XMLTV)
- 代码结构完全不变
- 仅修改硬编码的界面字符串"
        
        git commit -m "$commit_msg"
        success "汉化代码提交完成"
    fi
}

# 推送到GitHub
push_to_github() {
    step "推送到GitHub仓库"
    
    local repo_url="$1"
    
    if [ -z "$repo_url" ]; then
        if git remote get-url origin &> /dev/null; then
            repo_url=$(git remote get-url origin)
        else
            error "未设置远程仓库URL"
            exit 1
        fi
    else
        # 设置或更新远程仓库
        if git remote get-url origin &> /dev/null; then
            git remote set-url origin "$repo_url"
        else
            git remote add origin "$repo_url"
        fi
    fi
    
    info "远程仓库: $repo_url"
    
    # 推送代码
    if ! git push -u origin main; then
        error "推送失败，尝试强制推送"
        
        read -p "是否强制推送？(y/N): " force_push
        if [[ "$force_push" =~ ^[Yy]$ ]]; then
            git push -u origin main --force
        else
            error "推送取消"
            exit 1
        fi
    fi
    
    success "代码推送完成"
}

# 设置新仓库分支保护
setup_branch_protection() {
    step "设置分支保护规则"
    
    local repo_name="$GITHUB_REPO"
    local api_url="https://api.github.com/repos/$GITHUB_USER/$repo_name/branches/main/protection"
    
    local protection_data=$(cat << EOF
{
  "required_status_checks": null,
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF
    )
    
    info "正在设置main分支保护规则..."
    
    local response
    response=$(curl -s -X PUT \
        -H "Authorization: token $GITHUB_TOKEN" \
        -H "Accept: application/vnd.github.v3+json" \
        "https://api.github.com/repos/$GITHUB_USER/$repo_name/branches/main/protection" \
        -d "$protection_data" 2>/dev/null || true)
    
    if echo "$response" | grep -q '"url"'; then
        success "分支保护规则设置完成"
    else
        warning "分支保护规则设置失败 (可能需要仓库权限)"
    fi
}

# 创建初始README
create_readme() {
    step "创建README.md"
    
    cat > README.md << 'EOF'
# OwnTV 汉化版本

[![汉化版本编译](https://github.com/OWNER/REPO_NAME/workflows/汉化版本编译/badge.svg)](https://github.com/OWNER/REPO_NAME/actions)

完整的OwnTV中文界面汉化版本，基于官方OwnTV项目。

## 📱 功能特性

- ✅ **完整中文界面** - 所有用户界面元素均已汉化
- ✅ **保持原功能** - 所有功能与官方版本一致
- ✅ **技术术语保留** - HDR、PIN、M3U等技术术语保持英文
- ✅ **自动化编译** - GitHub Actions自动编译APK
- ✅ **持续更新** - 同步官方更新并保持汉化

## 🔧 汉化内容

### 主要界面汉化
- **导航菜单**: 搜索、首页、直播、电影、剧集、下载、节目指南、设置
- **设置界面**: 用户档案、播放列表、节目指南源、主题、备份、视频播放器、个性化等12个类别
- **全部按钮开关**: 直播预览、预览声音、自动播放、启动检查更新
- **所有对话框提示**: 退出确认、缩放警告、回看时间设置等

### 技术保持
## 🚀 快速开始

### 云编译APK
1. 访问 [Actions页面](https://github.com/OWNER/REPO_NAME/actions)
2. 选择"汉化版本编译"工作流
3. 点击"Run workflow"
4. 等待编译完成，下载APK文件

### 本地编译
```bash
# 克隆仓库
git clone https://github.com/OWNER/REPO_NAME.git
cd REPO_NAME

# 构建发布版本
./build-zh.sh --type release

# 构建调试版本
./build-zh.sh --type debug
```

## 📥 安装使用

### Android TV安装
1. 在电视上安装"Downloader"应用
2. 输入APK下载链接
3. 下载完成后点击安装
4. 确保开启"未知来源"权限

### 手机/平板安装
1. 下载APK文件到设备
2. 使用文件管理器找到APK文件
3. 点击安装，按提示操作

## 🔄 更新说明

汉化版本会跟随官方版本更新，每次更新后：
1. 同步官方代码更改
2. 更新汉化字符串
3. 自动化测试和编译
4. 发布新版本APK

## 🤝 贡献指南

欢迎贡献汉化改进：

1. Fork本仓库
2. 编辑需要改进的Kotlin文件中的字符串
3. 提交Pull Request
4. GitHub Actions会自动编译测试版本

## 📄 许可证

基于 [GNU GPL v3](LICENSE) 许可证开源。

## 📞 联系反馈

- 汉化问题: 提交GitHub Issue
- 功能建议: 参考官方OwnTV项目
- 编译问题: 检查Actions日志

---

**注意**: 此版本为社区汉化版，非官方发布。所有功能与官方版本一致，仅界面语言不同。
EOF

    # 替换占位符
    local current_dir=$(basename "$PWD")
    sed -i "s/REPO_NAME/$current_dir/g" README.md
    sed -i "s/OWNER/$GITHUB_USER/g" README.md
    
    git add README.md
    git commit -m "添加README说明文档" || true
    
    success "README.md创建完成"
}

# 完成设置
finalize_setup() {
    step "完成仓库设置"
    
    local repo_url="https://github.com/$GITHUB_USER/$GITHUB_REPO"
    
    echo ""
    echo "========================================"
    success "OwnTV汉化版本GitHub仓库设置完成！"
    echo "========================================"
    echo ""
    echo "仓库信息:"
    echo "  • 仓库URL: $repo_url"
    echo "  • 克隆命令: git clone $repo_url.git"
    echo "  • 网页访问: $repo_url"
    echo ""
    echo "下一步建议:"
    echo "  1. 访问 $repo_url 查看代码"
    echo "  2. 在Actions页面手动运行第一次编译"
    echo "  3. 在Releases页面下载APK文件"
    echo "  4. 安装到设备测试汉化效果"
    echo ""
    echo "自动化编译:"
    echo "  • 每次推送代码到main分支会自动编译"
    echo "  • 创建Pull Request会自动运行测试"
    echo "  • 可在Actions页面手动触发编译"
    echo ""
    echo "维护提示:"
    echo "  • 定期同步官方OwnTV的更新"
    echo "  • 检查汉化字符串是否需要更新"
    echo "  • 使用build-zh.sh脚本本地测试"
    echo ""
}

# 主函数
main() {
    echo ""
    echo "========================================"
    echo "  OwnTV汉化版本GitHub推送脚本"
    echo "========================================"
    echo ""
    
    # 解析参数
    local repo_url=""
    local repo_name=""
    local github_user=""
    local github_token=""
    local init_only=false
    local push_only=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -r|--repo)
                repo_url="$2"
                shift 2
                ;;
            -n|--name)
                repo_name="$2"
                shift 2
                ;;
            -u|--user)
                github_user="$2"
                shift 2
                ;;
            -t|--token)
                github_token="$2"
                shift 2
                ;;
            --init-only)
                init_only=true
                shift
                ;;
            --push-only)
                push_only=true
                shift
                ;;
            *)
                warning "未知参数: $1"
                shift
                ;;
        esac
    done
    
    # 执行流程
    check_git
    check_localization_files
    
    if [ "$push_only" = false ]; then
        get_github_info "$repo_name" "$github_user" "$github_token"
        validate_github_token
        
        if [ -z "$repo_url" ]; then
            repo_url=$(create_github_repo)
        fi
        
        reinit_git_repo "$repo_url"
        create_readme
    fi
    
    if [ "$init_only" = false ]; then
        commit_localization
        push_to_github "$repo_url"
        
        # 等待一下让GitHub API更新
        sleep 2
        
        setup_branch_protection
        finalize_setup
    fi
}

# 执行主函数
main "$@"