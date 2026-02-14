package me.rerere.rikkahub.ui.pages.skill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.data.skill.SkillImportResult
import me.rerere.rikkahub.data.skill.SkillManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.uuid.Uuid

/**
 * 技能管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillPage(
    onNavigateBack: () -> Unit,
    viewModel: SkillVM = koinViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 导入技能的文件选择器
    var showImportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Skill?>(null) }
    var showSkillDetail by remember { mutableStateOf<Skill?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.skill_management)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // 导入按钮
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_skill))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showImportDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.import_skill)) }
            )
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.skills.isEmpty()) {
            // 空状态
            EmptySkillsContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onImportClick = { showImportDialog = true }
            )
        } else {
            // 技能列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.skills, key = { it.id }) { skill ->
                    SkillCard(
                        skill = skill,
                        onToggleEnabled = { viewModel.toggleSkillEnabled(skill.id) },
                        onEdit = { showSkillDetail = skill },
                        onDelete = { showDeleteDialog = skill }
                    )
                }
            }
        }
    }

    // 导入对话框
    if (showImportDialog) {
        SkillImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { uri ->
                viewModel.importSkill(uri) { result ->
                    viewModel.viewModelScope.launch {
                        when {
                            result.success -> snackbarHostState.showSnackbar(
                                context.getString(R.string.skill_import_success, result.skill?.name)
                            )
                            else -> snackbarHostState.showSnackbar(
                                context.getString(R.string.skill_import_failed, result.errors.firstOrNull())
                            )
                        }
                    }
                }
            }
        )
    }

    // 删除确认对话框
    showDeleteDialog?.let { skill ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_skill)) },
            text = { Text(stringResource(R.string.delete_skill_confirm, skill.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSkill(skill.id)
                        showDeleteDialog = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 技能详情对话框
    showSkillDetail?.let { skill ->
        SkillDetailDialog(
            skill = skill,
            onDismiss = { showSkillDetail = null },
            onSave = { updatedSkill ->
                viewModel.updateSkill(updatedSkill)
                showSkillDetail = null
            }
        )
    }

    // 显示警告/错误消息
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
}

/**
 * 空状态内容
 */
@Composable
private fun EmptySkillsContent(
    modifier: Modifier = Modifier,
    onImportClick: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_skills),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.no_skills_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onImportClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.import_first_skill))
        }
    }
}

/**
 * 技能卡片
 */
@Composable
private fun SkillCard(
    skill: Skill,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            SkillIcon(skill = skill, size = 48.dp)
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 内容
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (skill.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 标签
                if (skill.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        skill.tags.take(3).forEach { tag ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (skill.tags.size > 3) {
                            Text(
                                text = "+${skill.tags.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // 开关
            Switch(
                checked = skill.enabled,
                onCheckedChange = { onToggleEnabled() }
            )
        }
        
        // 工具数量指示器
        if (skill.tools.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tools_count, skill.tools.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 技能图标
 */
@Composable
private fun SkillIcon(
    skill: Skill,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    // TODO: 支持加载 Base64 图标
    // 目前使用默认图标
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = skill.name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 技能详情对话框
 */
@Composable
private fun SkillDetailDialog(
    skill: Skill,
    onDismiss: () -> Unit,
    onSave: (Skill) -> Unit,
) {
    var editedSkill by remember(skill) { mutableStateOf(skill) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_skill)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = editedSkill.name,
                    onValueChange = { editedSkill = editedSkill.copy(name = it) },
                    label = { Text(stringResource(R.string.skill_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editedSkill.description,
                    onValueChange = { editedSkill = editedSkill.copy(description = it) },
                    label = { Text(stringResource(R.string.skill_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                OutlinedTextField(
                    value = editedSkill.version,
                    onValueChange = { editedSkill = editedSkill.copy(version = it) },
                    label = { Text(stringResource(R.string.skill_version)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editedSkill.author,
                    onValueChange = { editedSkill = editedSkill.copy(author = it) },
                    label = { Text(stringResource(R.string.skill_author)) },
                    modifier = Modifier.fillMaxWidth()
                )
                // 标签
                OutlinedTextField(
                    value = editedSkill.tags.joinToString(", "),
                    onValueChange = { 
                        editedSkill = editedSkill.copy(
                            tags = it.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        )
                    },
                    label = { Text(stringResource(R.string.skill_tags)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(R.string.skill_tags_hint)) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(editedSkill) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
