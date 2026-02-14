package me.rerere.rikkahub.ui.pages.skill

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.skill.SkillImporter

/**
 * 技能导入对话框
 */
@Composable
fun SkillImportDialog(
    onDismiss: () -> Unit,
    onImport: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUri = it
            isValidating = true
            validationResult = null
            
            // 验证文件格式
            val isValid = SkillImporter.isValidSkillZip(context, it)
            validationResult = Pair(
                isValid,
                if (isValid) "有效的技能包" else "无效的技能包：缺少 skill.md 文件"
            )
            isValidating = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_skill)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 说明文字
                Text(
                    text = stringResource(R.string.import_skill_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 格式说明
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.skill_zip_format),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "skill.zip/\n" +
                                    "  ├── skill.md     (必需)\n" +
                                    "  ├── icon.png     (可选)\n" +
                                    "  ├── tools.json   (可选)\n" +
                                    "  └── prompts/     (可选)",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }

                // 文件选择按钮
                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("application/zip")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.select_zip_file))
                }

                // 显示选中的文件
                selectedUri?.let { uri ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                validationResult?.first == true -> MaterialTheme.colorScheme.primaryContainer
                                validationResult?.first == false -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                isValidating -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(stringResource(R.string.validating))
                                }
                                validationResult != null -> {
                                    Icon(
                                        imageVector = if (validationResult!!.first) 
                                            Icons.Default.CheckCircle 
                                        else 
                                            Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (validationResult!!.first)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = uri.lastPathSegment ?: uri.toString(),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        validationResult!!.second?.let { message ->
                                            Text(
                                                text = message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (validationResult!!.first)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedUri?.let { uri ->
                        if (validationResult?.first == true) {
                            onImport(uri)
                            onDismiss()
                        }
                    }
                },
                enabled = validationResult?.first == true
            ) {
                Text(stringResource(R.string.import_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 导入进度对话框
 */
@Composable
fun SkillImportProgressDialog(
    progress: Float,
    status: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { }, // 不允许点击外部关闭
        title = { Text(stringResource(R.string.importing_skill)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = { }
    )
}
