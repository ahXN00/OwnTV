#!/usr/bin/env bash
# OwnTV汉化版本分支创建脚本
# 在原始仓库中创建 zh-localization 分支

set -e

# 彩色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

show_help() {
    cat << EOF
OwnTV汉化版本分支创建脚本
在当前仓库创建 zh-localization 分支并推送汉化版本

用法: $0 [选项]

选项:
  -h, --help      显示帮助信息
  -f, --force     强制创建分支（覆盖同名分支）
  -c, --clean     清理并重新开始
  --dry-run       只显示将要执行的命令，不实际执行
  --branch NAME   使用自定义分支名（默认: zh-localization）

环境变量:
  GIT_USER        可选的Git用户名
  GIT_EMAIL       可选的Git邮箱

示例:
  $0              创建 zh-localization 分支
  $0 --branch own-zh  创建 own-zh 分支
  $0 --dry-run    预览将要执行的命令

步骤概览:
1. 检查当前Git状态
2. 创建新分支
3. 提交所有汉化更改
4. 推送到远程仓库
5. 提示创建Pull Request
EOF
}

# 检查Git状态
check_git_status() {
    info "检查Git状态..."
    
    if ! command -v git &> /dev/null; then
        error "未找到Git，请先安装Git"
        exit 1
    fi
    
    if [ ! -d ".git" ]; then
        error "当前目录不是Git仓库"
        exit 1
    fi
    
    # 获取当前分支
    CURRENT_BRANCH=$(git branch --show-current 2>/dev/null)
    if [ -z "$CURRENT_BRANCH" ]; then
        warning "未在任何分支上，可能处于detached HEAD状态"
        read -p "是否继续？(y/N): " -n 1 -r
        echo
        [[ ! $REPLY =~ ^[Yy]$ ]] && exit 1
    else
        info "当前分支: $CURRENT_BRANCH"
    fi
    
    # 检查是否有未提交的更改
    if [ -n "$(git status --porcelain)" ]; then
        info "发现未提交的更改："
        git status --short
        
        if [[ "$FORCE" != "true" ]]; then
            read -p "是否继续？未提交的更改将被添加到新分支中。(y/N): " -n 1 -r
            echo
            [[ ! $REPLY =~ ^[Yy]$ ]] && exit 1
        fi
    fi
}

# 创建新分支
create_branch() {
    local branch_name=${BRANCH_NAME:-"zh-localization"}
    
    info "正在创建分支: $branch_name"
    
    # 检查分支是否已存在
    if git show-ref --verify --quiet "refs/heads/$branch_name"; then
        if [[ "$FORCE" == "true" ]]; then
            warning "分支 $branch_name 已存在，将强制覆盖"
            git branch -D "$branch_name" 2>/dev/null || true
        else
            read -p "分支 $branch_name 已存在，是否切换到该分支？(y/N): " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                git checkout "$branch_name"
                success "已切换到现有分支: $branch_name"
                return
            else
                read -p "请输入新分支名: " -r new_branch
                branch_name="${new_branch:-$branch_name}"
            fi
        fi
    fi
    
    # 创建新分支
    if [[ "$DRY_RUN" == "true" ]]; then
        info "[模拟] git checkout -b $branch_name"
    else
        git checkout -b "$branch_name"
        success "已创建并切换到新分支: $branch_name"
    fi
}

# 设置Git用户信息
setup_git_user() {
    if [ -n "$GIT_USER" ] && [ -n "$GIT_EMAIL" ]; then
        info "使用提供的Git用户信息: $GIT_USER <$GIT_EMAIL>"
        if [[ "$DRY_RUN" != "true" ]]; then
            git config user.name "$GIT_USER"
            git config user.email "$GIT_EMAIL"
        fi
    else
        # 检查当前Git配置
        local current_user=$(git config user.name)
        local current_email=$(git config user.email)
        
        if [ -n "$current_user" ] && [ -n "$current_email" ]; then
            info "使用当前Git配置: $current_user <$current_email>"
        else
            warning "未设置Git用户信息"
            
            if [[ "$DRY_RUN" != "true" ]]; then
                read -p "请输入Git用户名: " -r git_user
                read -p "请输入Git邮箱: " -r git_email
                
                git config user.name "${git_user:-$current_user}"
                git config user.email "${git_email:-$current_email}"
                
                success "已设置Git用户信息"
            fi
        fi
    fi
}

# 添加并提交文件
commit_changes() {
    info "添加所有更改文件..."
    
    # 列出将要添加的文件
    local changed_files=$(git status --porcelain | grep -E "^\s*[MA]" | sed 's/^...//')
    local new_files=$(git status --porcelain | grep -E "^\?\?" | sed 's/^...//')
    
    if [ -n "$changed_files" ]; then
        info "已修改的文件:"
        echo "$changed_files" | sed 's/^/  /'
    fi
    
    if [ -n "$new_files" ]; then
        info "新文件:"
        echo "$new_files" | sed 's/^/  /'
    fi
    
    # 创建提交
    local commit_message="完整汉化版本提交：界面翻译、编译脚本、GitHub Actions工作流

本次提交包含:
✅ 主界面汉化: 导航菜单、设置界面、按钮文本
✅ 对话框汉化: 退出确认、缩放警告、回看时间
✅ 编译脚本: build-zh.sh 本地编译脚本
✅ GitHub Actions: 自动化云编译工作流
✅ 文档说明: 汉化总结和部署指南
✅ 侧边栏和主页文本汉化
✅ 技术术语保持原样（HDR、PIN、M3U等）
✅ 代码结构不变，仅替换硬编码字符串"

    if [[ "$DRY_RUN" == "true" ]]; then
        info "[模拟] git add ."
        info "[模拟] git commit -m \"完整汉化版本提交\""
        success "[模拟] 提交完成"
    else
        git add .
        git commit -m "$commit_message"
        
        # 显示提交信息
        success "提交完成"
        echo
        git log --oneline -1
        echo
    fi
}

# 推送到远程
push_to_remote() {
    local branch_name=${BRANCH_NAME:-"zh-localization"}
    
    # 检查远程仓库
    local remote_url=$(git remote get-url origin 2>/dev/null || echo "")
    if [ -z "$remote_url" ]; then
        error "未配置远程仓库"
        return 1
    fi
    
    info "远程仓库: $remote_url"
    info "推送分支: $branch_name"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        info "[模拟] git push origin $branch_name"
        info "[模拟] git push --set-upstream origin $branch_name"
    else
        read -p "是否推送到远程仓库？(y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            info "正在推送..."
            if git push origin "$branch_name" 2>/dev/null; then
                success "推送成功"
            else
                warning "推送失败，可能是首次推送，尝试设置上游..."
                git push --set-upstream origin "$branch_name"
                success "推送成功！分支 $branch_name 已建立上游跟踪"
            fi
        else
            warning "已取消推送操作"
            return 0
        fi
    fi
}

# 显示后续步骤
show_next_steps() {
    local branch_name=${BRANCH_NAME:-"zh-localization"}
    local remote_url=$(git remote get-url origin 2>/dev/null || echo "github.com")
    
    success "🎉 汉化版本分支创建完成！"
    echo
    echo "下一步操作："
    echo
    echo "1. 创建 Pull Request 申请合并到主分支："
    echo "   访问: https://$(echo "$remote_url" | sed 's/.*@//; s/\.git$//; s/:/\//')/compare/main...$branch_name"
    echo
    echo "2. 在 GitHub Actions 中编译 APK："
    echo "   访问: https://$(echo "$remote_url" | sed 's/.*@//; s/\.git$//; s/:/\//')/actions/workflows/zh-build.yml"
    echo
    echo "3. 查看汉化内容："
    echo "   - $branch_name 分支中的所有汉化文件"
    echo "   - i18n-zh-summary.md 汉化总结"
    echo "   - zh-build-guide.md 汉化编译指南"
    echo
    echo "4. 本地编译测试："
    echo "   ./build-zh.sh --type debug"
    echo
    echo "5. 保持同步："
    echo "   # 获取官方更新"
    echo "   git fetch upstream"
    echo "   git merge upstream/main"
    echo "   git push origin $branch_name"
    echo
    info "分支: $branch_name 已准备好用于汉化版本开发！"
}

# 主函数
main() {
    # 解析参数
    BRANCH_NAME="zh-localization"
    FORCE=false
    CLEAN=false
    DRY_RUN=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -f|--force)
                FORCE=true
                shift
                ;;
            -c|--clean)
                CLEAN=true
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --branch)
                BRANCH_NAME="$2"
                shift 2
                ;;
            *)
                warning "未知参数: $1"
                shift
                ;;
        esac
    done
    
    # 清理操作
    if [[ "$CLEAN" == "true" ]]; then
        info "清理构建缓存..."
        git clean -ffd
        git checkout .
    fi
    
    # 执行流程
    check_git_status
    create_branch
    setup_git_user
    commit_changes
    push_to_remote
    show_next_steps
    
    success "汉化版本分支创建流程完成！"
}

# 执行主函数
main "$@"