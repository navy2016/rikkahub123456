package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.stringPreferencesKey
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

/**
 * Skill 相关的 PreferencesStore 扩展
 * 用于管理技能的存储和加载
 */
object SkillPreferencesKeys {
    /**
     * 技能列表存储键
     */
    val SKILLS = stringPreferencesKey("skills")
    
    /**
     * 启用的技能 ID 列表存储键
     */
    val ENABLED_SKILL_IDS = stringPreferencesKey("enabled_skill_ids")
}

/**
 * Settings 扩展函数 - 技能相关
 */

/**
 * 获取所有技能
 */
fun Settings.getSkills(): List<Skill> {
    return skills
}

/**
 * 根据 ID 获取技能
 */
fun Settings.getSkillById(id: Uuid): Skill? {
    return skills.find { it.id == id }
}

/**
 * 获取启用的技能
 */
fun Settings.getEnabledSkills(): List<Skill> {
    return skills.filter { it.enabled }
}

/**
 * 检查技能是否启用
 */
fun Settings.isSkillEnabled(skillId: Uuid): Boolean {
    return skills.find { it.id == skillId }?.enabled == true
}

/**
 * 按标签获取技能
 */
fun Settings.getSkillsByTag(tag: String): List<Skill> {
    return skills.filter { skill -> skill.tags.any { it.equals(tag, ignoreCase = true) } }
}

/**
 * 序列化技能列表为 JSON 字符串
 */
fun serializeSkills(skills: List<Skill>): String {
    return JsonInstant.encodeToString(skills)
}

/**
 * 反序列化技能列表
 */
fun deserializeSkills(json: String?): List<Skill> {
    return if (json.isNullOrBlank()) {
        emptyList()
    } else {
        try {
            JsonInstant.decodeFromString(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
