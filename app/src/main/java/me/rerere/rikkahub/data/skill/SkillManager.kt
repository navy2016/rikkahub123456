package me.rerere.rikkahub.data.skill

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.utils.JsonInstant
import java.io.File
import kotlin.uuid.Uuid

private const val TAG = "SkillManager"

/**
 * 技能管理器
 * 负责技能的加载、保存、导入、导出和执行
 */
class SkillManager(private val context: Context) {

    private val importer = SkillImporter(context)

    // 技能列表
    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills: StateFlow<List<Skill>> = _skills.asStateFlow()

    // 技能缓存目录
    private val skillsDir: File by lazy {
        File(context.filesDir, "skills").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * 加载技能列表
     */
    fun loadSkills(skillsJson: String?) {
        try {
            if (skillsJson.isNullOrBlank()) {
                _skills.value = emptyList()
                return
            }
            val skillsList: List<Skill> = JsonInstant.decodeFromString(skillsJson)
            _skills.value = skillsList
            Log.d(TAG, "Loaded ${skillsList.size} skills")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load skills", e)
            _skills.value = emptyList()
        }
    }

    /**
     * 导出技能列表为 JSON
     */
    fun exportSkillsToJson(): String {
        return JsonInstant.encodeToString(_skills.value)
    }

    /**
     * 导入技能
     * @param uri ZIP 文件 URI
     * @return 导入结果
     */
    suspend fun importSkill(uri: Uri): SkillImportResult {
        val result = importer.importFromUri(uri)
        
        if (result.success && result.skill != null) {
            // 检查是否已存在同名技能
            val existingIndex = _skills.value.indexOfFirst { 
                it.name.equals(result.skill.name, ignoreCase = true) 
            }
            
            if (existingIndex >= 0) {
                // 更新已存在的技能
                val updatedSkill = result.skill.copy(
                    id = _skills.value[existingIndex].id,
                    updatedAt = System.currentTimeMillis()
                )
                val newSkills = _skills.value.toMutableList()
                newSkills[existingIndex] = updatedSkill
                _skills.value = newSkills
                Log.d(TAG, "Updated existing skill: ${updatedSkill.name}")
            } else {
                // 添加新技能
                _skills.value = _skills.value + result.skill
                Log.d(TAG, "Added new skill: ${result.skill.name}")
            }
        }
        
        return result
    }

    /**
     * 添加技能
     */
    fun addSkill(skill: Skill): Skill {
        val newSkill = skill.copy(
            id = Uuid.random(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        _skills.value = _skills.value + newSkill
        return newSkill
    }

    /**
     * 更新技能
     */
    fun updateSkill(skill: Skill): Boolean {
        val index = _skills.value.indexOfFirst { it.id == skill.id }
        if (index < 0) return false
        
        val updatedSkill = skill.copy(updatedAt = System.currentTimeMillis())
        val newSkills = _skills.value.toMutableList()
        newSkills[index] = updatedSkill
        _skills.value = newSkills
        return true
    }

    /**
     * 删除技能
     */
    fun deleteSkill(skillId: Uuid): Boolean {
        val skill = _skills.value.find { it.id == skillId } ?: return false
        
        // 删除技能缓存文件
        skill.sourcePath?.let { path ->
            try {
                if (path.startsWith("file://")) {
                    File(Uri.parse(path).path ?: "").delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete skill source file", e)
            }
        }
        
        // 从列表中移除
        _skills.value = _skills.value.filter { it.id != skillId }
        Log.d(TAG, "Deleted skill: ${skill.name}")
        return true
    }

    /**
     * 获取技能
     */
    fun getSkill(skillId: Uuid): Skill? {
        return _skills.value.find { it.id == skillId }
    }

    /**
     * 启用/禁用技能
     */
    fun setSkillEnabled(skillId: Uuid, enabled: Boolean): Boolean {
        val skill = getSkill(skillId) ?: return false
        return updateSkill(skill.copy(enabled = enabled))
    }

    /**
     * 按标签筛选技能
     */
    fun getSkillsByTag(tag: String): List<Skill> {
        return _skills.value.filter { it.tags.contains(tag) }
    }

    /**
     * 搜索技能
     */
    fun searchSkills(query: String): List<Skill> {
        val lowerQuery = query.lowercase()
        return _skills.value.filter { skill ->
            skill.name.lowercase().contains(lowerQuery) ||
            skill.description.lowercase().contains(lowerQuery) ||
            skill.tags.any { it.lowercase().contains(lowerQuery) } ||
            skill.author.lowercase().contains(lowerQuery)
        }
    }

    /**
     * 获取启用的技能
     */
    fun getEnabledSkills(): List<Skill> {
        return _skills.value.filter { it.enabled }
    }

    /**
     * 获取所有标签
     */
    fun getAllTags(): Set<String> {
        return _skills.value.flatMap { it.tags }.toSet()
    }

    /**
     * 根据ID列表获取技能
     */
    fun getSkillsByIds(ids: Collection<Uuid>): List<Skill> {
        return _skills.value.filter { it.id in ids }
    }

    /**
     * 导出技能为 ZIP 文件
     * @param skill 要导出的技能
     * @param uri 目标 URI
     * @return 是否成功
     */
    suspend fun exportSkill(skill: Skill, uri: Uri): Boolean {
        // TODO: 实现技能导出为 ZIP
        return false
    }

    companion object {
        /**
         * 创建示例技能
         */
        fun createSampleSkills(): List<Skill> {
            return listOf(
                Skill(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000101"),
                    name = "网络搜索",
                    version = "1.0.0",
                    author = "RikkaHub",
                    description = "让 AI 能够搜索网络获取实时信息",
                    tags = listOf("search", "web", "utility"),
                    enabled = false,
                    tools = emptyList(),
                    systemPrompt = "你有能力搜索网络获取最新信息。当用户询问实时信息时，请使用搜索工具获取准确的答案。"
                ),
                Skill(
                    id = Uuid.parse("00000000-0000-0000-0000-000000000102"),
                    name = "代码执行",
                    version = "1.0.0",
                    author = "RikkaHub",
                    description = "让 AI 能够执行 Python 和 JavaScript 代码",
                    tags = listOf("code", "python", "javascript", "utility"),
                    enabled = false,
                    systemPrompt = "你可以执行 Python 和 JavaScript 代码来解决问题。使用适当的编程语言和工具。"
                )
            )
        }
    }
}
