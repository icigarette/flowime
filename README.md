# FlowIME

![Version](https://img.shields.io/badge/version-0.1.0-1F6FEB)
![Platform](https://img.shields.io/badge/IDEA-2024.1+-111827)
![OS](https://img.shields.io/badge/macOS%20%7C%20Windows-supported-34D399)
![License](https://img.shields.io/badge/license-MIT-FFD166)

FlowIME 是一个面向 IntelliJ IDEA 的上下文感知输入法切换插件。

它会根据光标所在位置，在代码、注释、字符串等场景之间自动切换中英文输入法，减少中文开发者在写代码时频繁手动切换输入法的打断感。

## 产品介绍

FlowIME 的核心目标只有一个：让输入法跟随编辑语义，而不是靠你自己记住什么时候该切英文、什么时候该切中文。

当前版本重点支持这些常见场景：
- Java / Kotlin 代码区域自动切换为英文
- Java / Kotlin 注释与字符串自动切换为中文
- Kotlin 字符串插值表达式保持英文
- XML 注释自动切换为中文
- 提供设置页、诊断窗口、结构化报告导出
- 支持 `im-select`、自定义命令适配器，以及可选的实验性原生适配器

适合这类用户：
- 日常在 IDEA 中大量写中文注释和文档的开发者
- 经常在代码、注释、字符串之间来回切换输入法的中文用户
- 希望把输入法切换从“手动操作”降到“自动跟随上下文”的团队

## 主要特性

- 上下文识别：识别代码、行注释、块注释、文档注释、字符串、XML 注释等常见编辑区域。
- 稳定切换：带有防抖、去重、当前模式缓存、手动切换尊重期，尽量避免抖动和误切换。
- 安全降级：`im-select` 不可用时，可使用自定义命令适配器；实验性 native 适配器默认关闭。
- 可诊断：内置诊断窗口，可手动刷新、复制报告、导出结构化报告，便于排查兼容性问题。

## 安装流程

1. 打开 IDEA。
2. 进入 `Settings / Preferences` → `Plugins`。
3. 点击右上角齿轮图标。
4. 选择 `Install Plugin from Disk...`。
5. 选择下载好的插件安装包 `FlowIme-0.1.0.zip`。
6. 安装后重启 IDEA。

建议优先从 GitHub Releases 下载正式发布的 ZIP 安装包。

## 首次使用建议

### macOS 用户

推荐先安装 `im-select`，这是当前最稳定的执行链路。

如果你的机器已经安装 `im-select`，FlowIME 会优先使用它。

如果没有 `im-select`，你也可以在设置页中配置：
- `英文切换命令`
- `中文切换命令`
- 可选的 `当前模式探测命令`

### 设置入口

安装完成后，在 IDEA 中打开：
- `Settings / Preferences` → `FlowIME 输入切换`

你可以在这里配置：
- 切换规则
- 手动切换尊重期
- 调试日志
- 命令适配器
- 输入源 ID

## 诊断与排查

诊断窗口位置：
- `View` → `Tool Windows` → `FlowIME 诊断`

你可以在诊断窗口中：
- 立即刷新当前状态
- 复制诊断信息
- 导出结构化报告
- 查看当前上下文、决策、适配器、最近事件、会话统计

当你需要反馈问题时，优先提供：
- 结构化报告
- 是否安装了 `im-select`
- 当前系统输入法
- 触发问题的具体操作路径
