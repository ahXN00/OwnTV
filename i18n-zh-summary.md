## 汉化总结

### 已完成汉化的文件：

1. **ShellViewModel.kt** - 主标签汉化
   - Search → 搜索
   - Home → 首页  
   - Live TV → 直播
   - Movies → 电影
   - Series → 剧集
   - Downloads → 下载
   - Guide → 节目指南
   - Settings → 设置

2. **SettingsScreen.kt** - 主要设置界面汉化
   - Settings → 设置
   - Search settings… → 搜索设置…
   - Live preview → 直播预览
   - Preview sound → 预览声音
   - Auto-play → 自动播放
   - Check for update → 启动检查更新
   - Profile → 用户档案
   - Content → 内容
   - Playlists → 播放列表
   - TV guide sources → 节目指南源
   - Theme → 主题
   - Backups → 备份
   - Video Player → 视频播放器
   - Customize → 个性化
   - Home → 首页
   - Network → 网络
   - Metadata → 元数据
   - Weather → 天气

   - 对话框文本：
     - Done → 完成
     - Reset → 重置
     - Device → 设备
     - Manual → 手动
     - Catch-up time → 回看时间
     - Low zoom warning → 低缩放警告
     - "I understand and accept the risk" → "我理解并接受风险"
     - "Zoom below X% shows many more items on screen at once..." → "缩放低于 X% 会在屏幕上显示更多项目..."
     - "Press OK to continue, or Back to stay at X%." → "按确定继续，或按返回保持在 X%。"
     - "How catch-up timestamps are sent. Use your device timezone, or set the offset your provider's server expects." → "回看时间戳的发送方式。使用您设备的时区，或设置您供应商服务器期望的偏移量。"

3. **OwnTVShell.kt** - 离线提示汉化
   - "You're offline — playback and updates won't work until you reconnect." → "您已离线 — 重新连接前播放和更新将无法使用。"

4. **Sidebar.kt** - 侧边栏汉化
   - "OwnTV User" → "OwnTV 用户"
   - "Switch Profile" → "切换用户"
   - "Browse" → "浏览"

5. **ExitDialog.kt** - 退出对话框汉化
   - "Exit OwnTV?" → "退出 OwnTV？"
   - "Are you sure you want to close the app?" → "确定要关闭应用吗？"
   - "Cancel" → "取消"
   - "Exit" → "退出"

6. **QuickToggleChip文本** - 切换按钮汉化
   - "On" → "开"
   - "Off" → "关"
   - "SOON" → "即将推出"

### 汉化原则：
1. 保持技术术语原样（如HDR、PIN、M3U、Xtream、XMLTV）
2. 界面文本全部汉化为简体中文
3. 保持代码结构不变，仅替换字符串内容
4. 按钮和重要操作文本优先汉化
5. 长描述文本保持语义准确