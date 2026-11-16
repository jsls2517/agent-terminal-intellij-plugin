# Agent Terminal

在 IntelliJ IDEA 右侧工具窗内，以交互式终端运行本地 `agy`（或 `claude`、`opencode` 等其他 AI Agent CLI）。打开工具窗即可进入，直接在里面操作即可。

### 📋 要求

- IntelliJ IDEA 2024.3+（或任何 JetBrains IDE：PyCharm、WebStorm、PhpStorm 等）
- 终端中可执行 `agy`（或配置的 CLI 工具，需自行安装并加入 PATH）

### 📦 安装

**从源码安装：**
1. 克隆本仓库
2. 运行 `./gradlew buildPlugin`
3. 在 Settings → Plugins → Install from Disk 中选择 `build/distributions/` 下生成的 ZIP
4. 或者在此仓库的Actions中下载最新 release 版本的插件

### 🚀 用法

1. 打开右侧 "Agent" 工具窗
2. 若已安装 `agy` 将自动启动
3. 也可以在设置页面自定义启动命令（Settings → Tools → Agent Terminal，默认 `agy`）

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

**提示未检测到 agy？**
请确保 `agy` 在 PATH 中；重开工具窗或重启 IDE 后重试。

### 🔒 隐私

不联网，不收集任何数据；所有命令都在 IDE 内置终端中可见。

### 📄 许可

MIT License - 详见 [LICENSE](LICENSE) 文件

### 鸣谢
此插件基础版本克隆自：https://github.com/hamdiwanis/claude-terminal-intellij-plugin
感谢 hamdiwanis 提供的基础版本