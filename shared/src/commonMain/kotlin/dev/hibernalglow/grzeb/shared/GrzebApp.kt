package dev.hibernalglow.grzeb.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.hibernalglow.grzeb.shared.platform.rememberPlatformServices
import dev.hibernalglow.grzeb.shared.ui.search.SearchScreen

/**
 * 应用根组件：Android / 桌面 / Web 共用这一份 UI，平台差异全在
 * [dev.hibernalglow.grzeb.shared.platform.PlatformServices] 里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrzebApp() {
    val services = rememberPlatformServices()

    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(topBar = { TopAppBar(title = { Text("全文搜索") }) }) { padding ->
                // Scaffold 已经吃掉状态栏 / 导航栏的 padding，再叠 ime 时要把它排除，
                // 否则键盘弹出时会多垫一份导航栏高度
                SearchScreen(
                    services = services,
                    modifier = Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .imePadding(),
                )
            }
        }
    }
}
