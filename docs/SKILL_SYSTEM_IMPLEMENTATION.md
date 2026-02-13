# RikkaHub 技能系统实现文档

## 概述

本次实现为 RikkaHub 添加了技能导入功能，允许用户导入 `.zip` 格式的技能包，其中必须包含 `skill.md` 文件。

## 功能特性

1. **技能包导入**：支持从 ZIP 文件导入技能包
2. **YAML Front Matter 解析**：解析 skill.md 中的 YAML 元数据
3. **技能管理**：启用/禁用、编辑、删除技能
4. **Assistant 关联**：将技能与 Assistant 关联，激活特定的工具和提示词

## 新增文件清单

### 数据模型

```
app/src/main/java/me/rerere/rikkahub/data/model/Skill.kt
```
- `Skill` - 技能配置数据类
- `SkillTool` - 工具定义
- `SkillToolParameter` - 工具参数
- `SkillParameterType` - 参数类型枚举
- `SkillToolExecutor` - 工具执行器（内置/JS/Python/HTTP）
- `SkillInjection` - 提示词注入配置
- `SkillMetadata` - YAML 元数据结构

### 技能导入与管理

```
app/src/main/java/me/rerere/rikkahub/data/skill/
├── SkillImporter.kt    # ZIP 文件导入解析
└── SkillManager.kt     # 技能生命周期管理
```

### 数据存储扩展

```
app/src/main/java/me/rerere/rikkahub/data/datastore/
├── SkillPreferences.kt           # 技能存储扩展函数
└── PreferencesStore.skill.patch.kt # Settings 修改补丁
```

### Assistant 扩展

```
app/src/main/java/me/rerere/rikkahub/data/model/AssistantSkills.kt
```
- `Assistant.getSkills()` - 获取关联的技能
- `Assistant.getEnabledSkills()` - 获取启用的技能
- `Assistant.getSkillTools()` - 获取所有工具定义
- `Assistant.getSkillInjections()` - 获取所有提示词注入
- `Assistant.getMergedSystemPrompt()` - 合并系统提示词

### UI 组件

```
app/src/main/java/me/rerere/rikkahub/ui/pages/skill/
├── SkillPage.kt           # 技能管理主页面
├── SkillVM.kt             # 技能管理 ViewModel
└── SkillImportDialog.kt   # 导入对话框

app/src/main/java/me/rerere/rikkahub/ui/components/skill/
└── SkillSelector.kt       # Assistant 技能选择器组件
```

### 资源文件

```
app/src/main/res/values-zh/strings_skill.xml  # 中文字符串资源
` ``

### 示例技能包

```
docs/skills/example_weather_skill/skill.md  # 天气查询技能示例
```

## 技能包格式规范

### ZIP 包结构

```
skill.zip/
├── skill.md           # 必需：技能定义
├── icon.png           # 可选：技能图标 (png/jpg/webp/svg)
├── tools.json         # 可选：详细工具定义
├── prompts/           # 可选：提示词模板
│   ├── system.md      # 系统提示词
│   └── user.md        # 用户提示词模板
└── injections/        # 可选：提示词注入
    └── injection1.json
```

### skill.md 格式

技能包必须包含 `skill.md` 文件，使用 YAML Front Matter 格式：

```markdown
---
name: 技能名称
version: 1.0.0
author: 作者
description: 技能描述
tags: [tag1, tag2]
tools:
  - name: tool_name
    description: 工具描述
    parameters:
      param1: 参数说明
system_prompt: |
  系统提示词内容
  支持多行
injections:
  - name: 注入名称
    content: 注入内容
    position: after_system_prompt
    priority: 10
---

# 技能文档

这里可以写更详细的说明，比如：
- 功能介绍
- 使用方法
- 注意事项
```

### 可选字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| name | String | ✓ | 技能名称 |
| version | String | ✗ | 版本号，默认 "1.0.0" |
| author | String | ✗ | 作者信息 |
| description | String | ✗ | 技能描述 |
| tags | List<String> | ✗ | 标签列表 |
| tools | List | ✗ | 工具定义 |
| system_prompt | String | ✗ | 系统提示词 |
| injections | List | ✗ | 提示词注入 |

## 集成步骤

### 1. 更新 Settings 数据类

在 `PreferencesStore.kt` 的 `Settings` 数据类中添加 `skills` 字段：

```kotlin
@Serializable
data class Settings(
    // ... 现有字段 ...
    
    // 技能设置
    val skills: List<Skill> = emptyList(),
) {
    // ...
}
```

### 2. 更新 SettingsStore

在 `SettingsStore` 的 companion object 中添加：

```kotlin
companion object {
    // ... 现有常量 ...
    val SKILLS = stringPreferencesKey("skills")
}
```

在 `settingsFlowRaw` 中加载技能：

```kotlin
skills = preferences[SKILLS]?.let {
    JsonInstant.decodeFromString<List<Skill>>(it)
} ?: emptyList(),
```

在 `update` 方法中保存技能：

```kotlin
preferences[SKILLS] = JsonInstant.encodeToString(settings.skills)
```

### 3. 更新 Assistant 模型

在 `Assistant` 数据类中添加 `skillIds` 字段：

```kotlin
@Serializable
data class Assistant(
    // ... 现有字段 ...
    
    // 技能关联
    val skillIds: Set<Uuid> = emptySet(),
)
```

### 4. 添加导航路由

在应用的路由配置中添加技能管理页面：

```kotlin
// 在 NavHost 或路由配置中添加
composable("settings/skills") {
    SkillPage(
        onNavigateBack = { navController.popBackStack() }
    )
}
```

### 5. 添加依赖注入

确保在 Koin 模块中注册 `SkillVM`：

```kotlin
viewModel { SkillVM(get()) }
```

## API 使用示例

### 导入技能

```kotlin
val skillManager = SkillManager(context)

// 从 URI 导入
val result = skillManager.importFromUri(zipUri)

if (result.success) {
    val skill = result.skill
    println("导入成功: ${skill?.name}")
    
    // 检查警告
    result.warnings.forEach { println("警告: $it") }
} else {
    result.errors.forEach { println("错误: $it") }
}
```

### 获取启用的技能

```kotlin
val settings = settingsStore.settingsFlow.value
val enabledSkills = settings.getEnabledSkills()

enabledSkills.forEach { skill ->
    println("技能: ${skill.name}")
    println("工具数量: ${skill.tools.size}")
}
```

### Assistant 关联技能

```kotlin
// 获取 Assistant 关联的技能
val skills = assistant.getSkills(settings)

// 获取合并后的系统提示词
val systemPrompt = assistant.getMergedSystemPrompt(settings)

// 获取工具定义
val tools = assistant.getSkillTools(settings)
```

### 快速预览技能包

```kotlin
// 不完整导入，只读取元数据
val metadata = SkillImporter.quickPreview(context, zipUri)
metadata?.let {
    println("技能名称: ${it.name}")
    println("版本: ${it.version}")
    println("描述: ${it.description}")
}
```

## 注意事项

1. **文件大小限制**：建议 ZIP 包不超过 10MB
2. **图标支持**：支持 PNG、JPG、WEBP、SVG 格式，建议使用 PNG
3. **字符编码**：所有文本文件应使用 UTF-8 编码
4. **安全性**：导入时会验证 skill.md 是否存在，不会自动执行任何代码

## 后续扩展

1. **技能市场**：可以添加在线技能市场，一键安装公开技能
2. **技能导出**：支持将当前配置导出为技能包
3. **技能更新**：检测并更新已安装的技能
4. **技能分享**：生成分享链接或二维码
5. **工具执行**：实现 SkillToolExecutor 的实际执行逻辑

## 示例技能包

查看 `docs/skills/example_weather_skill/skill.md` 获取完整的技能包示例。

---

**作者**: AI Assistant
**日期**: 2026-02-13
**版本**: 1.0.0
