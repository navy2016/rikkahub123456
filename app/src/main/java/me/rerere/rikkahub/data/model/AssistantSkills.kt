package me.rerere.rikkahub.data.model

// ============================================================
// Assistant.kt 技能关联补丁
// 
// 在 Assistant 数据类中添加 skillIds 字段
// ============================================================

/**
 * Assistant 技能关联扩展
 * 
// 在 Assistant 数据类中添加以下字段:
// 
// @Serializable
// data class Assistant(
//     ...
//     val modeInjectionIds: Set<Uuid> = emptySet(),      // 关联的模式注入 ID
//     val lorebookIds: Set<Uuid> = emptySet(),            // 关联的 Lorebook ID
//     
//     // ===== 新增: 技能关联 =====
//     val skillIds: Set<Uuid> = emptySet(),               // 关联的技能 ID
// )
 */

/**
 * 获取 Assistant 关联的技能
 */
fun Assistant.getSkills(settings: me.rerere.rikkahub.data.datastore.Settings): List<Skill> {
    return settings.skills.filter { it.id in skillIds }
}

/**
 * 获取 Assistant 关联的启用的技能
 */
fun Assistant.getEnabledSkills(settings: me.rerere.rikkahub.data.datastore.Settings): List<Skill> {
    return settings.skills.filter { it.id in skillIds && it.enabled }
}

/**
 * 获取 Assistant 所有技能的工具定义
 */
fun Assistant.getSkillTools(settings: me.rerere.rikkahub.data.datastore.Settings): List<me.rerere.ai.core.Tool> {
    return getEnabledSkills(settings).flatMap { it.toTools() }
}

/**
 * 获取 Assistant 所有技能的提示词注入
 */
fun Assistant.getSkillInjections(settings: me.rerere.rikkahub.data.datastore.Settings): List<PromptInjection.RegexInjection> {
    return getEnabledSkills(settings).flatMap { skill -> 
        skill.injections.map { it.toPromptInjection() }
    }
}

/**
 * 获取合并后的系统提示词（包含技能提示词）
 */
fun Assistant.getMergedSystemPrompt(settings: me.rerere.rikkahub.data.datastore.Settings): String {
    val basePrompt = systemPrompt
    val skillPrompts = getEnabledSkills(settings)
        .mapNotNull { it.systemPrompt }
        .filter { it.isNotBlank() }
    
    return if (skillPrompts.isEmpty()) {
        basePrompt
    } else {
        buildString {
            append(basePrompt)
            if (basePrompt.isNotBlank()) {
                append("\n\n")
            }
            skillPrompts.forEachIndexed { index, prompt ->
                if (index > 0) append("\n\n")
                append(prompt)
            }
        }
    }
}
