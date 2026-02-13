package me.rerere.rikkahub.ui.pages.skill

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.skill.SkillImportResult
import me.rerere.rikkahub.data.skill.SkillManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.uuid.Uuid

/**
 * 技能管理页面状态
 */
data class SkillState(
    val isLoading: Boolean = false,
    val skills: List<Skill> = emptyList(),
    val message: String? = null,
    val isImporting: Boolean = false,
)

/**
 * 技能管理 ViewModel
 */
class SkillVM(
    application: Application,
) : AndroidViewModel(application), KoinComponent {

    private val settingsStore: SettingsStore = get()
    private val settings: Settings get() = settingsStore.settingsFlow.value
    private val skillManager = SkillManager(application)

    private val _state = MutableStateFlow(SkillState())
    val state: StateFlow<SkillState> = _state.asStateFlow()

    init {
        // 从设置中加载技能
        loadSkills()
        
        // 监听设置变化
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { newSettings ->
                _state.update { it.copy(skills = newSettings.skills) }
            }
        }
    }

    /**
     * 从设置加载技能
     */
    private fun loadSkills() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                _state.update { 
                    it.copy(
                        skills = settings.skills,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        isLoading = false,
                        message = "加载技能失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 导入技能
     */
    fun importSkill(uri: Uri, onResult: (SkillImportResult) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true) }
            
            try {
                val result = skillManager.importFromUri(uri)
                
                if (result.success && result.skill != null) {
                    // 添加到设置中
                    val newSkills = settings.skills + result.skill
                    settingsStore.update { it.copy(skills = newSkills) }
                    
                    _state.update { 
                        it.copy(
                            isImporting = false,
                            message = if (result.warnings.isNotEmpty()) {
                                "导入成功，但有警告:\n${result.warnings.joinToString("\n")}"
                            } else null
                        )
                    }
                } else {
                    _state.update { 
                        it.copy(
                            isImporting = false,
                            message = "导入失败: ${result.errors.firstOrNull()}"
                        )
                    }
                }
                
                onResult(result)
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        isImporting = false,
                        message = "导入失败: ${e.message}"
                    )
                }
                onResult(SkillImportResult.failure(listOf(e.message ?: "未知错误")))
            }
        }
    }

    /**
     * 更新技能
     */
    fun updateSkill(skill: Skill) {
        viewModelScope.launch {
            val newSkills = settings.skills.map { 
                if (it.id == skill.id) skill else it 
            }
            settingsStore.update { it.copy(skills = newSkills) }
            _state.update { it.copy(message = "已更新: ${skill.name}") }
        }
    }

    /**
     * 删除技能
     */
    fun deleteSkill(skillId: Uuid) {
        viewModelScope.launch {
            val skill = settings.skills.find { it.id == skillId }
            val newSkills = settings.skills.filter { it.id != skillId }
            settingsStore.update { it.copy(skills = newSkills) }
            _state.update { it.copy(message = "已删除: ${skill?.name}") }
        }
    }

    /**
     * 切换技能启用状态
     */
    fun toggleSkillEnabled(skillId: Uuid) {
        viewModelScope.launch {
            val newSkills = settings.skills.map { skill ->
                if (skill.id == skillId) {
                    skill.copy(
                        enabled = !skill.enabled,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    skill
                }
            }
            settingsStore.update { it.copy(skills = newSkills) }
        }
    }

    /**
     * 清除消息
     */
    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /**
     * 按标签筛选技能
     */
    fun filterByTag(tag: String) {
        viewModelScope.launch {
            val filtered = settings.skills.filter { skill ->
                skill.tags.any { it.equals(tag, ignoreCase = true) }
            }
            _state.update { it.copy(skills = filtered) }
        }
    }

    /**
     * 搜索技能
     */
    fun searchSkills(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _state.update { it.copy(skills = settings.skills) }
            } else {
                val lowerQuery = query.lowercase()
                val filtered = settings.skills.filter { skill ->
                    skill.name.lowercase().contains(lowerQuery) ||
                    skill.description.lowercase().contains(lowerQuery) ||
                    skill.tags.any { it.lowercase().contains(lowerQuery) } ||
                    skill.author.lowercase().contains(lowerQuery)
                }
                _state.update { it.copy(skills = filtered) }
            }
        }
    }
}
