#!/usr/bin/env bash
# OwnTV 汉化版本编译脚本
# 适用于本地编译和CI环境

set -e  # 出错时退出

# 彩色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

# 显示帮助
show_help() {
    cat << EOF
OwnTV 汉化版本编译脚本

用法: ./build-zh.sh [选项]

选项:
  -h, --help          显示此帮助信息
  -v, --version       显示版本信息
  -t, --type TYPE     构建类型 (debug|release, 默认: release)
  -c, --clean         清理构建缓存
  -s, --setup         设置构建环境
  --no-test           跳过测试
  --no-lint           跳过代码检查

环境变量:
  VERSION_NAME        版本号 (默认: 自动生成)
  VERSION_CODE        版本代码 (默认: 自动生成)

示例:
  ./build-zh.sh                    # 构建发布版本
  ./build-zh.sh -t debug           # 构建调试版本
  ./build-zh.sh -c -t release      # 清理并构建发布版本
  ./build-zh.sh --setup            # 设置构建环境

GitHub Actions 使用:
  此脚本已在 .github/workflows/zh-build.yml 中配置
  提交到main分支会自动触发构建并创建预发布版本
EOF
}

# 检查环境
check_environment() {
    info "检查构建环境..."
    
    # 检查Java
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
        info "Java 版本: $JAVA_VERSION"
    else
        error "未找到Java，请安装Java JDK 21"
        exit 1
    fi
    
    # 检查Android SDK
    if [ -n "$ANDROID_HOME" ]; then
        info "Android SDK 路径: $ANDROID_HOME"
    else
        warning "未设置ANDROID_HOME环境变量"
    fi
    
    # 检查Gradle
    if [ -f "./gradlew" ]; then
        chmod +x ./gradlew
        info "Gradle wrapper 可用"
    else
        error "未找到gradlew文件"
        exit 1
    fi
}

# 设置构建环境
setup_environment() {
    info "设置构建环境..."
    
    # 创建必要的目录
    mkdir -p app/src/main/assets
    
    # 检查依赖
    info "检查依赖..."
    ./gradlew dependencies --configuration compileClasspath || {
        warning "依赖检查失败，尝试修复..."
    }
    
    success "环境设置完成"
}

# 清理构建
clean_build() {
    info "清理构建缓存..."
    ./gradlew clean
    success "清理完成"
}

# 生成版本号
generate_version() {
    local timestamp=$(date +'%Y%m%d.%H%M')
    local commit_hash=$(git rev-parse --short=7 HEAD 2>/dev/null || echo "unknown")
    
    if [ -z "$VERSION_NAME" ]; then
        VERSION_NAME="zh-$timestamp-$commit_hash"
        info "自动生成版本名: $VERSION_NAME"
    fi
    
    if [ -z "$VERSION_CODE" ]; then
        VERSION_CODE=$(date +'%Y%m%d')
        info "自动生成版本代码: $VERSION_CODE"
    fi
    
    export VERSION_NAME
    export VERSION_CODE
}

# 运行测试
run_tests() {
    if [ "$SKIP_TESTS" = "true" ]; then
        warning "跳过测试"
        return
    fi
    
    info "运行测试..."
    ./gradlew test --stacktrace
    
    if [ $? -eq 0 ]; then
        success "测试通过"
    else
        error "测试失败"
        exit 1
    fi
}

# 运行代码检查
run_lint() {
    if [ "$SKIP_LINT" = "true" ]; then
        warning "跳过代码检查"
        return
    fi
    
    info "运行代码检查..."
    ./gradlew ktlintCheck --stacktrace
    
    if [ $? -eq 0 ]; then
        success "代码检查通过"
    else
        warning "代码检查未通过，尝试修复..."
        ./gradlew ktlintFormat
        info "重新检查..."
        ./gradlew ktlintCheck
    fi
}

# 构建APK
build_apk() {
    local build_type=$1
    info "构建 $build_type APK..."
    info "版本: $VERSION_NAME (代码: $VERSION_CODE)"
    
    # 设置环境变量
    export VERSION_NAME
    export VERSION_CODE
    
    if [ "$build_type" = "debug" ]; then
        ./gradlew assembleDebug --stacktrace --no-daemon
        local apk_file=$(find app/build/outputs/apk/debug -name "*.apk" | head -n1)
    else
        ./gradlew assembleRelease --stacktrace --no-daemon
        local apk_file=$(find app/build/outputs/apk/release -name "*.apk" | head -n1)
    fi
    
    if [ -f "$apk_file" ]; then
        local output_file="OwnTV-zh-$build_type-$VERSION_NAME.apk"
        cp "$apk_file" "$output_file"
        success "构建完成: $output_file"
        echo "APK_PATH=$(pwd)/$output_file" >> $GITHUB_ENV 2>/dev/null || true
        echo "$(pwd)/$output_file"
    else
        error "构建失败，未找到APK文件"
        exit 1
    fi
}

# 创建构建报告
create_build_report() {
    local build_type=$1
    local apk_path=$2
    
    info "创建构建报告..."
    
    cat > 构建报告.md << EOF
# OwnTV 汉化版本构建报告

## 构建信息
- **构建类型**: $build_type
- **构建时间**: $(date +'%Y-%m-%d %H:%M:%S')
- **版本名称**: $VERSION_NAME
- **版本代码**: $VERSION_CODE
- **Git提交**: $(git rev-parse --short HEAD 2>/dev/null || echo "未知")
- **分支**: $(git branch --show-current 2>/dev/null || echo "未知")

## 构建环境
- **操作系统**: $(uname -s)
- **Java版本**: $(java -version 2>&1 | head -n 1)
- **Gradle版本**: $(./gradlew --version 2>/dev/null | grep "Gradle" | head -n 1)

## 汉化内容摘要
- 导航菜单: 搜索、首页、直播、电影、剧集、下载、节目指南、设置
- 设置界面: 用户档案、播放列表、节目指南源、主题、备份、视频播放器、个性化等
- 所有对话框和提示信息
- 侧边栏和主界面文本

## 构建产物
- **APK文件**: $(basename "$apk_path")
- **文件大小**: $(du -h "$apk_path" | cut -f1)
- **MD5校验**: $(md5sum "$apk_path" | cut -d' ' -f1)

## 安装说明
1. 将APK文件传输到Android TV或设备
2. 确保已开启"未知来源"安装权限
3. 使用文件管理器找到APK并安装
4. 首次打开可能需要授予必要权限

## 注意事项
- 此为汉化编译版，功能与原版相同
- 技术术语保持英文原样
- 如遇兼容性问题，请检查设备Android版本
EOF
    
    success "构建报告已生成: 构建报告.md"
}

# 主函数
main() {
    # 解析参数
    BUILD_TYPE="release"
    DO_CLEAN=false
    DO_SETUP=false
    SKIP_TESTS=false
    SKIP_LINT=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -v|--version)
                echo "OwnTV 汉化编译脚本 v1.0"
                exit 0
                ;;
            -t|--type)
                BUILD_TYPE="$2"
                shift 2
                ;;
            -c|--clean)
                DO_CLEAN=true
                shift
                ;;
            -s|--setup)
                DO_SETUP=true
                shift
                ;;
            --no-test)
                SKIP_TESTS=true
                shift
                ;;
            --no-lint)
                SKIP_LINT=true
                shift
                ;;
            *)
                warning "未知参数: $1"
                shift
                ;;
        esac
    done
    
    # 验证构建类型
    if [[ "$BUILD_TYPE" != "debug" && "$BUILD_TYPE" != "release" ]]; then
        error "无效的构建类型: $BUILD_TYPE，请使用debug或release"
        exit 1
    fi
    
    info "开始构建OwnTV汉化版本 ($BUILD_TYPE)..."
    
    # 检查环境
    check_environment
    
    # 设置环境
    if [ "$DO_SETUP" = true ]; then
        setup_environment
    fi
    
    # 清理
    if [ "$DO_CLEAN" = true ]; then
        clean_build
    fi
    
    # 生成版本号
    generate_version
    
    # 运行代码检查
    run_lint
    
    # 运行测试
    run_tests
    
    # 构建APK
    APK_PATH=$(build_apk "$BUILD_TYPE")
    
    # 创建构建报告
    create_build_report "$BUILD_TYPE" "$APK_PATH"
    
    success "OwnTV汉化版本构建完成！"
    info "APK文件: $APK_PATH"
    info "构建报告: 构建报告.md"
    
    if [ "$BUILD_TYPE" = "release" ]; then
        info "发布版本已准备就绪，可部署到设备测试"
    else
        info "调试版本已构建完成，可用于开发测试"
    fi
}

# 执行主函数
main "$@"