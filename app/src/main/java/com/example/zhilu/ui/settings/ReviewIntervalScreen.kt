package com.example.zhilu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.zhilu.domain.reminder.ReviewIntervals
import com.example.zhilu.ui.component.AppTopBar
import com.example.zhilu.ui.navigation.AppTabScaffold
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import kotlinx.coroutines.launch

/**
 * 「复习间隔」独立页：预设 + 自定义阶梯 + 即时校验 + 预览。
 *
 * 编辑的是"每次复习之间隔多少天"（存成一串天数，见 [ReviewIntervals]）。
 * 改动**只影响之后的排期**：已有计划保留当前档位，从下一档开始按新间隔走 ——
 * 这句话必须写在页面上（"改间隔会不会把我已排的计划打乱"是第一个会担心的事）。
 */
@Composable
fun ReviewIntervalScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()

    // 草稿 + 是否被用户改过：没动过时跟随存储（避免异步到达的初始值被空串覆盖）
    var draft by rememberSaveable { mutableStateOf(ReviewIntervals.format(state.reviewIntervals)) }
    var touched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.reviewIntervals) {
        if (!touched) draft = ReviewIntervals.format(state.reviewIntervals)
    }

    val parsed = ReviewIntervals.parse(draft)
    val error = if (parsed == null) {
        "只能填正整数（天），用逗号或空格分隔"
    } else {
        ReviewIntervals.validate(parsed)
    }
    val canSave = parsed != null && error == null && parsed != state.reviewIntervals

    AppTabScaffold(
        topBar = {
            AppTopBar(
                title = "复习间隔",
                onBack = { navController.popBackStack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(bottom = Spacing.Xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
        ) {
            SettingsGroup(title = "阶梯") {
                Text(
                    text = "每一档的间隔天数，从第 1 档往后依次变长。" +
                        "评「重来」回到第 1 档，评「掌握」跳一档，越过最后一档即毕业。",
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 预设一键填入，之后仍可继续手改
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    ReviewIntervals.PRESETS.forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                touched = true
                                draft = ReviewIntervals.format(preset.days)
                            }
                        ) {
                            Text(preset.label, style = ZhiLuType.chip)
                        }
                    }
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        touched = true
                        draft = it
                    },
                    label = { Text("间隔（天）") },
                    placeholder = { Text("1, 3, 7, 15, 30") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(
                            text = error ?: "最多 ${ReviewIntervals.MAX_STEPS} 档，" +
                                "每档 ${ReviewIntervals.MIN_DAY}–${ReviewIntervals.MAX_DAY} 天",
                            style = ZhiLuType.meta
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SettingsGroup(title = "预览") {
                Text(
                    text = if (parsed != null && error == null) {
                        ReviewIntervals.preview(parsed)
                    } else {
                        "把间隔改成合法的一串天数后，这里会画出完整的复习节奏。"
                    },
                    style = ZhiLuType.bodySmall,
                    color = if (parsed != null && error == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = "已有计划不会重置：保留当前档位，从下一档开始按新间隔走；" +
                        "新开启的计划从第 1 档开始。",
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    val days = parsed ?: return@Button
                    viewModel.setReviewIntervals(days)
                    touched = false
                    scope.launch { snackbar.showSnackbar("复习间隔已保存") }
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.PageGutter)
            ) {
                Text("保存", style = ZhiLuType.chip)
            }
        }
    }
}
