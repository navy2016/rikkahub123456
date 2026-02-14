package me.rerere.rikkahub.data.skill

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.model.SkillInjection
import me.rerere.rikkahub.data.model.SkillMetadata
import me.rerere.rikkahub.data.model.SkillTool
import me.rerere.rikkahub.data.model.SkillToolExecutor
import me.rerere.rikkahub.data.model.SkillToolParameter
import me.rerere.rikkahub.data.model.SkillParameterType
import me.rerere.rikkahub.utils.JsonInstant
import java.io.BufferedReader
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.uuid.Uuid

private const val TAG = "SkillImporter"

/**
 * 技能导入结果
 */
data class SkillImportResult(
    val success: Boolean,
    val skill: Skill? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    companion object {
        fun success(skill: Skill, warnings: List<String> = emptyList()) = SkillImportResult(
            success = true,
            skill = skill,
            warnings = warnings
        )

        fun failure(errors: List<String>, warnings: List<String> = emptyList()) = SkillImportResult(
            success = false,
            errors = errors,
            warnings = warnings
        )
    }
}

/**
 * 技能导入器
 * 支持从 ZIP 文件导入技能包
 *
 * ZIP 包结构：
 * - skill.md（必需）: 技能定义文件，包含 YAML Front Matter
 * - icon.png（可选）: 技能图标
 * - tools.json（可选）: 工具定义
 * - prompts/system.md（可选）: 系统提示词
 * - prompts/user.md（可选）: 用户提示词模板
 * - injections/*.json（可选）: 提示词注入配置
 */
class SkillImporter(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 从 URI 导入技能
     * @param uri ZIP 文件的 URI
     * @return 导入结果
     */
    suspend fun importFromUri(uri: Uri): SkillImportResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        try {
            // 读取 ZIP 文件内容
            val zipContent = mutableMapOf<String, ByteArray>()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry: ZipEntry? = zipInputStream.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val content = zipInputStream.readBytes()
                            zipContent[entry.name] = content
                        }
                        entry = zipInputStream.nextEntry
                    }
                }
            } ?: run {
                return@withContext SkillImportResult.failure(listOf("无法打开文件"))
            }

            // 查找 skill.md 文件（必需）
            val skillMdEntry = zipContent.keys.find { 
                it == "skill.md" || it.endsWith("/skill.md") 
            }
            
            if (skillMdEntry == null) {
                return@withContext SkillImportResult.failure(
                    listOf("ZIP 包中缺少 skill.md 文件。技能包必须包含 skill.md 文件。")
                )
            }

            val skillMdContent = zipContent[skillMdEntry]?.toString(Charsets.UTF_8)
                ?: return@withContext SkillImportResult.failure(listOf("无法读取 skill.md 文件"))

            // 解析 skill.md
            val metadata = parseSkillMetadata(skillMdContent)
            if (metadata == null) {
                return@withContext SkillImportResult.failure(
                    listOf("无法解析 skill.md 文件。请确保文件格式正确。")
                )
            }

            // 验证必需字段
            if (metadata.name.isBlank()) {
                errors.add("技能名称不能为空")
            }

            if (errors.isNotEmpty()) {
                return@withContext SkillImportResult.failure(errors, warnings)
            }

            // 读取可选内容
            var iconBase64: String? = null
            var tools: List<SkillTool> = metadata.tools
            var systemPrompt: String? = metadata.systemPrompt
            var userPromptTemplate: String? = metadata.userPromptTemplate
            var injections: List<SkillInjection> = metadata.injections

            // 读取图标
            zipContent.keys.find { 
                it == "icon.png" || it.endsWith("/icon.png") 
            }?.let { iconEntry ->
                zipContent[iconEntry]?.let { iconData ->
                    iconBase64 = "data:image/png;base64,${Base64.encodeToString(iconData, Base64.NO_WRAP)}"
                }
            }

            // 也支持其他图标格式
            if (iconBase64 == null) {
                zipContent.keys.find { 
                    it.matches(Regex(".*icon\\.(png|jpg|jpeg|webp|svg)$", RegexOption.IGNORE_CASE))
                }?.let { iconEntry ->
                    zipContent[iconEntry]?.let { iconData ->
                        val extension = iconEntry.substringAfterLast('.').lowercase()
                        val mimeType = when (extension) {
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "webp" -> "image/webp"
                            "svg" -> "image/svg+xml"
                            else -> "image/png"
                        }
                        iconBase64 = "data:$mimeType;base64,${Base64.encodeToString(iconData, Base64.NO_WRAP)}"
                    }
                }
            }

            // 读取 tools.json
            zipContent.keys.find { 
                it == "tools.json" || it.endsWith("/tools.json") 
            }?.let { toolsEntry ->
                try {
                    val toolsJson = zipContent[toolsEntry]?.toString(Charsets.UTF_8)
                    if (toolsJson != null) {
                        val toolsFile = json.decodeFromString<me.rerere.rikkahub.data.model.SkillToolsFile>(toolsJson)
                        // 合并 tools.json 和 metadata 中的工具
                        if (toolsFile.tools.isNotEmpty()) {
                            tools = tools + toolsFile.tools
                            warnings.add("从 tools.json 加载了 ${toolsFile.tools.size} 个工具定义")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse tools.json", e)
                    warnings.add("tools.json 解析失败: ${e.message}")
                }
            }

            // 读取系统提示词
            zipContent.keys.find { 
                it == "prompts/system.md" || it.endsWith("/prompts/system.md") 
            }?.let { promptEntry ->
                zipContent[promptEntry]?.toString(Charsets.UTF_8)?.let { content ->
                    if (content.isNotBlank()) {
                        systemPrompt = content.trim()
                        // 合并 metadata 中的 systemPrompt
                        if (!metadata.systemPrompt.isNullOrBlank()) {
                            systemPrompt = "${metadata.systemPrompt}\n\n$content"
                        }
                    }
                }
            }

            // 读取用户提示词模板
            zipContent.keys.find { 
                it == "prompts/user.md" || it.endsWith("/prompts/user.md") 
            }?.let { promptEntry ->
                zipContent[promptEntry]?.toString(Charsets.UTF_8)?.let { content ->
                    if (content.isNotBlank()) {
                        userPromptTemplate = content.trim()
                    }
                }
            }

            // 读取注入配置
            zipContent.keys.filter { 
                it.startsWith("injections/") && it.endsWith(".json") 
            }.forEach { injectionEntry ->
                try {
                    val injectionJson = zipContent[injectionEntry]?.toString(Charsets.UTF_8)
                    if (injectionJson != null) {
                        val injectionFile = json.decodeFromString<me.rerere.rikkahub.data.model.SkillInjectionFile>(injectionJson)
                        injections = injections + injectionFile.injections
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse injection file: $injectionEntry", e)
                    warnings.add("注入文件 $injectionEntry 解析失败: ${e.message}")
                }
            }

            // 去重工具
            tools = tools.distinctBy { it.name }

            // 创建技能对象
            val skill = Skill(
                id = Uuid.random(),
                name = metadata.name,
                version = metadata.version,
                author = metadata.author,
                description = metadata.description,
                tags = metadata.tags,
                icon = iconBase64,
                enabled = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                tools = tools,
                systemPrompt = systemPrompt,
                userPromptTemplate = userPromptTemplate,
                injections = injections,
                rawMarkdown = skillMdContent,
                sourcePath = uri.toString()
            )

            // 验证技能
            val validationErrors = skill.validate()
            if (validationErrors.isNotEmpty()) {
                return@withContext SkillImportResult.failure(validationErrors, warnings)
            }

            // 检查技能文档内容（skill.md 中 YAML Front Matter 之后的部分）
            val descriptionFromContent = extractDescriptionFromMarkdown(skillMdContent)
            if (skill.description.isBlank() && descriptionFromContent.isNotBlank()) {
                // 如果 YAML 中的 description 为空，使用文档内容作为描述
                warnings.add("使用 skill.md 文档内容作为技能描述")
            }

            SkillImportResult.success(skill, warnings)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import skill", e)
            SkillImportResult.failure(listOf("导入失败: ${e.message}"))
        }
    }

    /**
     * 解析 skill.md 文件
     * 提取 YAML Front Matter 元数据
     */
    private fun parseSkillMetadata(content: String): SkillMetadata? {
        try {
            // 检查是否有 YAML Front Matter
            val frontMatterRegex = Regex("^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n")
            val match = frontMatterRegex.find(content)
            
            if (match == null) {
                Log.w(TAG, "No YAML front matter found in skill.md")
                // 尝试提取名称（从第一个标题）
                val nameRegex = Regex("^#\\s+(.+)$", RegexOption.MULTILINE)
                val nameMatch = nameRegex.find(content)
                return if (nameMatch != null) {
                    SkillMetadata(name = nameMatch.groupValues[1].trim())
                } else {
                    null
                }
            }

            val yamlContent = match.groupValues[1]
            return parseYamlFrontMatter(yamlContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse skill metadata", e)
            return null
        }
    }

    /**
     * 解析 YAML Front Matter
     */
    private fun parseYamlFrontMatter(yaml: String): SkillMetadata {
        val lines = yaml.lines()
        var name = ""
        var version = "1.0.0"
        var author = ""
        var description = ""
        val tags = mutableListOf<String>()
        val tools = mutableListOf<SkillTool>()
        val injections = mutableListOf<SkillInjection>()
        var systemPrompt: String? = null
        var userPromptTemplate: String? = null

        var currentKey = ""
        var inList = false
        var listIndent = 0
        var currentTool: MutableMap<String, Any?>? = null

        for (line in lines) {
            val trimmedLine = line.trimStart()
            val indent = line.length - trimmedLine.length
            
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) continue

            // 处理列表项
            if (trimmedLine.startsWith("- ")) {
                inList = true
                listIndent = indent
                
                val listContent = trimmedLine.removePrefix("- ").trim()
                
                when (currentKey) {
                    "tags" -> {
                        // 简单标签列表
                        val tag = listContent.removeSurrounding("\"")
                        if (tag.isNotBlank()) tags.add(tag)
                    }
                    "tools" -> {
                        // 如果有之前的工具，保存它
                        if (currentTool != null) {
                            parseToolMap(currentTool)?.let { tools.add(it) }
                        }
                        currentTool = mutableMapOf()
                        
                        // 检查是否是内联工具定义
                        if (listContent.contains(":")) {
                            val parts = listContent.split(":", limit = 2)
                            if (parts.size == 2) {
                                val key = parts[0].trim()
                                val value = parts[1].trim().removeSurrounding("\"")
                                currentTool!![key] = value
                            }
                        } else {
                            // 可能是简单的工具名称
                            currentTool!!["name"] = listContent.removeSurrounding("\"")
                        }
                    }
                }
                continue
            }

            // 处理嵌套属性
            if (inList && indent > listIndent && currentTool != null) {
                if (trimmedLine.contains(":")) {
                    val parts = trimmedLine.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim().removeSurrounding("\"")
                        currentTool!![key] = value
                    }
                }
                continue
            }

            // 结束列表
            if (inList && indent <= listIndent) {
                if (currentTool != null) {
                    parseToolMap(currentTool)?.let { tools.add(it) }
                    currentTool = null
                }
                inList = false
            }

            // 处理键值对
            if (trimmedLine.contains(":")) {
                val parts = trimmedLine.split(":", limit = 2)
                if (parts.size == 2) {
                    currentKey = parts[0].trim()
                    val value = parts[1].trim().removeSurrounding("\"")
                    
                    when (currentKey) {
                        "name" -> name = value
                        "version" -> version = value
                        "author" -> author = value
                        "description" -> description = value
                        "system_prompt" -> systemPrompt = value
                        "user_prompt_template" -> userPromptTemplate = value
                    }
                }
            }
        }

        // 保存最后一个工具
        if (currentTool != null) {
            parseToolMap(currentTool)?.let { tools.add(it) }
        }

        // 解析标签数组格式（如 tags: [weather, api]）
        if (tags.isEmpty()) {
            val tagsMatch = Regex("tags:\\s*\\[(.*?)\\]").find(yaml)
            if (tagsMatch != null) {
                val tagsStr = tagsMatch.groupValues[1]
                tagsStr.split(",").forEach { tag ->
                    val cleanTag = tag.trim().removeSurrounding("\"").removeSurrounding("'")
                    if (cleanTag.isNotBlank()) tags.add(cleanTag)
                }
            }
        }

        return SkillMetadata(
            name = name,
            version = version,
            author = author,
            description = description,
            tags = tags,
            tools = tools,
            injections = injections,
            systemPrompt = systemPrompt,
            userPromptTemplate = userPromptTemplate
        )
    }

    /**
     * 解析工具 Map
     */
    private fun parseToolMap(map: Map<String, Any?>): SkillTool? {
        val name = (map["name"] as? String) ?: return null
        val description = (map["description"] as? String) ?: ""
        
        return SkillTool(
            name = name,
            description = description,
            parameters = emptyList(),
            required = emptyList(),
            executor = SkillToolExecutor.Builtin()
        )
    }

    /**
     * 从 Markdown 内容中提取描述（YAML Front Matter 之后的内容）
     */
    private fun extractDescriptionFromMarkdown(content: String): String {
        // 移除 YAML Front Matter
        val frontMatterRegex = Regex("^---\\s*\\n[\\s\\S]*?\\n---\\s*\\n")
        val mdContent = frontMatterRegex.replace(content, "")
        
        // 提取第一段作为描述
        val firstParagraph = mdContent.lines()
            .dropWhile { it.isBlank() }
            .takeWhile { !it.startsWith("#") && it.isNotBlank() }
            .joinToString(" ")
            .trim()
        
        return firstParagraph
    }

    companion object {
        /**
         * 验证 ZIP 文件格式
         */
        fun isValidSkillZip(context: Context, uri: Uri): Boolean {
            return try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipInputStream ->
                        var hasSkillMd = false
                        var entry = zipInputStream.nextEntry
                        while (entry != null) {
                            if (entry.name == "skill.md" || entry.name.endsWith("/skill.md")) {
                                hasSkillMd = true
                                break
                            }
                            entry = zipInputStream.nextEntry
                        }
                        hasSkillMd
                    }
                } ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to validate skill zip", e)
                false
            }
        }

        /**
         * 快速获取技能信息（不完整导入）
         */
        suspend fun quickPreview(context: Context, uri: Uri): SkillMetadata? = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zipInputStream ->
                        var entry = zipInputStream.nextEntry
                        while (entry != null) {
                            if (entry.name == "skill.md" || entry.name.endsWith("/skill.md")) {
                                val content = zipInputStream.readBytes().toString(Charsets.UTF_8)
                                return@withContext SkillImporter(context).parseSkillMetadata(content)
                            }
                            entry = zipInputStream.nextEntry
                        }
                    }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preview skill", e)
                null
            }
        }
    }
}
