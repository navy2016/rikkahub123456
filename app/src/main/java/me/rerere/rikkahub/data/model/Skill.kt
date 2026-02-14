package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import kotlin.uuid.Uuid

/**
 * 技能配置
 * 可导入的技能包，包含 skill.md 和相关配置文件
 *
 * 技能 ZIP 包结构：
 * ```
 * skill.zip/
 *   skill.md           # 必需：技能说明（YAML Front Matter + 描述文档）
 *   icon.png           # 可选：技能图标
 *   tools.json         # 可选：工具定义（工具列表）
 *   tools/             # 可选：工具脚本目录
 *     tool1.js         # JavaScript 工具脚本
 *     tool2.py         # Python 工具脚本
 *   prompts/           # 可选：提示词模板
 *     system.md        # 系统提示词
 *     user.md          # 用户提示词模板
 *   injections/        # 可选：提示词注入
 *     injection1.json  # 注入配置
 * ```
 *
 * skill.md 格式示例：
 * ```markdown
 * ---
 * name: 天气查询
 * version: 1.0.0
 * author: developer
 * description: 让AI能够查询实时天气信息
 * tags: [weather, api, utility]
 * tools:
 *   - name: get_weather
 *     description: 获取指定城市的天气信息
 *     parameters:
 *       city: 城市名称
 * ---
 *
 * # 天气查询技能
 *
 * 本技能让 AI 能够查询实时天气信息...
 * ```
 */
@Serializable
data class Skill(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val icon: String? = null,  // 图标路径或 Base64
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // 工具定义
    val tools: List<SkillTool> = emptyList(),

    // 提示词配置
    val systemPrompt: String? = null,
    val userPromptTemplate: String? = null,

    // 提示词注入
    val injections: List<SkillInjection> = emptyList(),

    // MCP 服务器依赖
    val mcpServerIds: Set<Uuid> = emptySet(),

    // 原始 skill.md 内容
    val rawMarkdown: String? = null,

    // 导入来源
    val sourcePath: String? = null,  // ZIP 文件路径
) {
    /**
     * 获取技能显示名称
     */
    fun displayName(): String = name.ifBlank { "未命名技能" }

    /**
     * 将技能工具转换为 AI 工具
     */
    fun toTools(): List<Tool> = tools.map { it.toTool() }

    /**
     * 验证技能配置是否有效
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) {
            errors.add("技能名称不能为空")
        }
        tools.forEachIndexed { index, tool ->
            val toolErrors = tool.validate()
            if (toolErrors.isNotEmpty()) {
                errors.add("工具 #${index + 1} (${tool.name}): ${toolErrors.joinToString(", ")}")
            }
        }
        return errors
    }
}

/**
 * 技能工具定义
 */
@Serializable
data class SkillTool(
    val name: String = "",
    val description: String = "",
    val parameters: List<SkillToolParameter> = emptyList(),
    val required: List<String> = emptyList(),

    // 执行配置
    val executor: SkillToolExecutor = SkillToolExecutor.Builtin(),
) {
    /**
     * 转换为 AI 工具定义
     */
    fun toTool(): Tool = Tool(
        name = name,
        description = description,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    parameters.forEach { param ->
                        put(param.name, buildJsonObject {
                            put("type", JsonPrimitive(param.type.jsonValue))
                            put("description", JsonPrimitive(param.description))
                            param.enum?.let { enumValues ->
                                put("enum", buildJsonArray {
                                    enumValues.forEach { add(JsonPrimitive(it)) }
                                })
                            }
                        })
                    }
                },
                required = required
            )
        },
        execute = { args ->
            // 工具执行逻辑将在 SkillManager 中处理
            listOf(UIMessagePart.Text("Tool '$name' execution placeholder"))
        }
    )

    /**
     * 验证工具定义
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) {
            errors.add("工具名称不能为空")
        }
        if (!name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) {
            errors.add("工具名称格式无效，只能包含字母、数字和下划线，且不能以数字开头")
        }
        if (description.isBlank()) {
            errors.add("工具描述不能为空")
        }
        return errors
    }
}

/**
 * 技能工具参数
 */
@Serializable
data class SkillToolParameter(
    val name: String = "",
    val description: String = "",
    val type: SkillParameterType = SkillParameterType.String,
    val enum: List<String>? = null,
    val default: String? = null,
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) {
            errors.add("参数名称不能为空")
        }
        return errors
    }
}

/**
 * 参数类型
 */
@Serializable
enum class SkillParameterType(val jsonValue: String) {
    @SerialName("string")
    String("string"),

    @SerialName("number")
    Number("number"),

    @SerialName("integer")
    Integer("integer"),

    @SerialName("boolean")
    Boolean("boolean"),

    @SerialName("array")
    Array("array"),

    @SerialName("object")
    Object("object");
}

/**
 * 工具执行器配置
 */
@Serializable
sealed class SkillToolExecutor {
    /**
     * 内置执行器
     */
    @Serializable
    @SerialName("builtin")
    data class Builtin(
        val type: String = "",  // time_info, clipboard, web_search 等
        val config: Map<String, String> = emptyMap()
    ) : SkillToolExecutor()

    /**
     * JavaScript 执行器
     */
    @Serializable
    @SerialName("javascript")
    data class JavaScript(
        val code: String = "",
        val timeoutMs: Long = 5000
    ) : SkillToolExecutor()

    /**
     * Python 执行器
     */
    @Serializable
    @SerialName("python")
    data class Python(
        val code: String = "",
        val timeoutMs: Long = 30000
    ) : SkillToolExecutor()

    /**
     * HTTP 请求执行器
     */
    @Serializable
    @SerialName("http")
    data class Http(
        val url: String = "",
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val bodyTemplate: String? = null,  // 支持模板语法 {{param}}
        val responsePath: String? = null   // JSONPath 提取响应
    ) : SkillToolExecutor()
}

/**
 * 技能提示词注入
 */
@Serializable
data class SkillInjection(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val content: String = "",
    val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val injectDepth: Int = 4,
    val role: MessageRole = MessageRole.USER,
) {
    /**
     * 转换为 PromptInjection
     */
    fun toPromptInjection(): PromptInjection.RegexInjection = PromptInjection.RegexInjection(
        id = id,
        name = name,
        enabled = enabled,
        priority = priority,
        position = position,
        content = content,
        injectDepth = injectDepth,
        role = role,
        constantActive = true,  // 技能注入默认常驻激活
    )
}

/**
 * skill.md 的 YAML Front Matter 元数据
 */
@Serializable
data class SkillMetadata(
    val name: String = "",
    val version: String = "1.0.0",
    val author: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val tools: List<SkillTool> = emptyList(),
    val injections: List<SkillInjection> = emptyList(),
    val systemPrompt: String? = null,
    val userPromptTemplate: String? = null,
)

/**
 * 工具定义文件格式 (tools.json)
 */
@Serializable
data class SkillToolsFile(
    val version: String = "1.0.0",
    val tools: List<SkillTool> = emptyList(),
)

/**
 * 注入配置文件格式 (injections/*.json)
 */
@Serializable
data class SkillInjectionFile(
    val version: String = "1.0.0",
    val injections: List<SkillInjection> = emptyList(),
)
