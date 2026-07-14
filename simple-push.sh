#!/usr/bin/env bash
# OwnTV汉化版一键推送脚本
# 简化版 - 只需填写一次信息

set -e

echo "========================================"
echo "  OwnTV汉化版GitHub推送助手"
echo "========================================"
echo ""

echo "📝 请填写以下信息（信息将只保存在本脚本中）："
echo ""

# 读取用户信息
read -p "1. 您的GitHub用户名: " GITHUB_USER

read -p "2. 新仓库名称（如：OwnTV-ZH）: " REPO_NAME

echo ""
echo "3. 需要生成GitHub Personal Access Token"
echo "   访问: https://github.com/settings/tokens"
echo "   点击 'Generate new token' → 选择 'repo' 权限"
echo "   复制Token（只显示一次）"
echo ""
read -s -p "   请输入Token: " GITHUB_TOKEN
echo ""

echo "⏳ 正在准备推送到新仓库: $GITHUB_USER/$REPO_NAME"
echo ""

# 保存配置到临时文件
cat > github_config.sh << EOF
#!/usr/bin/env bash
# GitHub配置信息
export GITHUB_USER="$GITHUB_USER"
export GITHUB_REPO="$REPO_NAME"
export GITHUB_TOKEN="$GITHUB_TOKEN"
EOF

# 运行主推送脚本
echo "🚀 开始推送到GitHub..."
chmod +x push-to-github.sh
./push-to-github.sh -u "$GITHUB_USER" -t "$GITHUB_TOKEN" -n "$REPO_NAME"

# 清理（可选）
echo ""
read -p "是否保存配置供下次使用？(y/N): " save_config
if [[ "$save_config" =~ ^[Yy]$ ]]; then
    mv github_config.sh .github_config.secure
    chmod 600 .github_config.secure
    echo "✅ 配置已保存到 .github_config.secure"
    echo "   下次运行: source .github_config.secure && ./push-to-github.sh"
else
    rm -f github_config.sh
    echo "🗑️  配置已删除"
fi

echo ""
echo "✨ 推送完成！"
echo "   访问: https://github.com/$GITHUB_USER/$REPO_NAME"
echo ""