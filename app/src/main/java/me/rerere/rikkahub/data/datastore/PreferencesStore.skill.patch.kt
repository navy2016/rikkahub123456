// ============================================================
// PreferencesStore.kt 技能支持补丁
// 
// 将以下更改应用到 PreferencesStore.kt 文件中
// ============================================================

// 1. 在文件顶部的 import 部分添加:
import me.rerere.rikkahub.data.model.Skill

// 2. 在 SettingsStore companion object 中添加新的 preference key:
// 在 companion object { ... } 块中添加:
        // 技能
        val SKILLS = stringPreferencesKey("skills")

// 3. 在 settingsFlowRaw 的 map 块中添加技能加载:
// 在 settingsFlowRaw = dataStore.data...map { preferences ->
//     Settings(
//         ...
//         // 在 lorebooks 之后添加:
//         skills = preferences[SKILLS]?.let {
//             JsonInstant.decodeFromString<List<Skill>>(it)
//         } ?: emptyList(),
//         ...
//     )
// }

// 4. 在 update(settings: Settings) 方法中添加技能保存:
// suspend fun update(settings: Settings) {
//     settingsFlow.value = settings
//     dataStore.edit { preferences ->
//         ...
//         // 在 lorebooks 之后添加:
//         preferences[SKILLS] = JsonInstant.encodeToString(settings.skills)
//         ...
//     }
// }

// 5. 在 Settings 数据类中添加 skills 字段:
@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    // ... 其他字段 ...
    val lorebooks: List<Lorebook> = emptyList(),
    val enableContainerRuntime: Boolean = true,
    
    // 子代理设置
    val enabledSubAgentIds: Set<Uuid> = emptySet(),
    
    // ===== 新增: 技能设置 =====
    val skills: List<Skill> = emptyList(),              // 所有技能列表
) {
    // ...
}

// ============================================================
// 完整示例 (Settings 类):
// ============================================================
/*
@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val enableWebSearch: Boolean = false,
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val titleModelId: Uuid = Uuid.random(),
    val imageGenerationModelId: Uuid = Uuid.random(),
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val translateModeId: Uuid = Uuid.random(),
    val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
    val suggestionModelId: Uuid = Uuid.random(),
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val ocrModelId: Uuid = Uuid.random(),
    val ocrPrompt: String = DEFAULT_OCR_PROMPT,
    val compressModelId: Uuid = Uuid.random(),
    val compressPrompt: String = DEFAULT_COMPRESS_PROMPT,
    val codeCompressModelId: Uuid = Uuid.random(),
    val codeCompressPrompt: String = DEFAULT_CODE_COMPRESS_PROMPT,
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val enableContainerRuntime: Boolean = true,
    
    // 子代理设置
    val enabledSubAgentIds: Set<Uuid> = emptySet(),
    
    // 技能设置
    val skills: List<Skill> = emptyList(),
) {
    companion object {
        fun dummy() = Settings(init = true)
    }
}
*/
