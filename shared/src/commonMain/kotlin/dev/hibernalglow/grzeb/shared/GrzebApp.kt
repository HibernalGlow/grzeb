package dev.hibernalglow.grzeb.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.hibernalglow.grzeb.shared.platform.rememberPlatformServices
import dev.hibernalglow.grzeb.shared.ui.GrzebIcons
import dev.hibernalglow.grzeb.shared.ui.search.SearchScreen
import dev.hibernalglow.grzeb.shared.ui.search.SearchUiState
import dev.hibernalglow.grzeb.shared.ui.theme.GrzebTheme

/**
 * 应用根组件：Android / 桌面 / Web 共用这一份 UI，平台差异全在
 * [dev.hibernalglow.grzeb.shared.platform.PlatformServices] 里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrzebApp() {
    val services = rememberPlatformServices()
    val state = remember { SearchUiState() }

    GrzebTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "grzeb",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "全文检索",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { state.showSettings = true },
                            ) {
                                Icon(
                                    imageVector = GrzebIcons.Settings,
                                    contentDescription = "设置",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                // Scaffold 已经吃掉状态栏 / 导航栏的 padding，再叠 ime 时要把它排除，
                // 否则键盘弹出时会多垫一份导航栏高度
                SearchScreen(
                    services = services,
                    state = state,
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .imePadding(),
                )
            }
        }
    }
}
