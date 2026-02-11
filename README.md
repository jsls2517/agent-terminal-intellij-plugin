# Claude Code Terminal

在 IntelliJ IDEA 右侧工具窗内，以交互式终端运行本地 `claude` (Claude Code CLI)。打开工具窗即可进入，直接在里面操作即可。

### ✨ 功能亮点

- **一键聚焦/重启**: 用快捷键快速切换终端；按住 Shift 即可重建会话
- **自动启动**: PATH 中检测到 `claude` 即自动运行，无需额外配置
- **界面简洁**: 独立右侧 ToolWindow，隐藏底部 Terminal，避免重复窗口
- **智能提示**: 未检测到 CLI 时自动气泡提醒

### 📋 要求

- IntelliJ IDEA 2024.2+（或任何 JetBrains IDE：PyCharm、WebStorm、PhpStorm 等）
- 终端中可执行 `claude`（需自行安装并加入 PATH）

### 📦 安装

**从源码安装：**
1. 克隆本仓库
2. 运行 `./gradlew buildPlugin`
3. 在 Settings → Plugins → Install from Disk 中选择 `build/distributions/` 下生成的 ZIP

### 🚀 用法

1. 打开右侧 "Claude Code Terminal" 工具窗
2. 若已安装 `claude` 将自动启动

### 🛠️ 开发

**构建插件：**
```bash
./gradlew buildPlugin
```

**在沙盒中运行：**
```bash
./gradlew runIde
```

### ❓ 常见问题

**提示未检测到 claude？**
请从 [Claude Code 文档](https://docs.claude.com/docs/claude-code) 安装 CLI，并确保 `claude` 在 PATH 中；重开工具窗或重启 IDE 后重试。

**首次弹出认证/登录界面？**
在该终端内按提示操作即可，使用 Anthropic 账户认证。

### 🔒 隐私

不联网，不收集任何数据；所有命令都在 IDE 内置终端中可见。

### 📄 许可

MIT License - 详见 [LICENSE](LICENSE) 文件

### ⚠️ 免责声明

本插件为独立项目，与 Anthropic 或任何第三方供应商无关；不打包/下载/安装 `claude` CLI，也不收集数据。

### 鸣谢
此插件克隆自：https://github.com/hamdiwanis/claude-terminal-intellij-plugin
感谢 hamdiwanis 提供的基础版本