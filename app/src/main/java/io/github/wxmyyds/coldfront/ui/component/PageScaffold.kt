package io.github.wxmyyds.coldfront.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** Shared page shell for scroll-driven expanded-to-collapsed Material 3 titles. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val blurTopBar = LocalInterfaceBlur.current && LocalGlassHazeState.current != null
    val background = MaterialTheme.colorScheme.background

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        title,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                modifier = Modifier.glassTopBarEffect(LocalGlassHazeState.current, blurTopBar),
                navigationIcon = navigationIcon,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background.copy(alpha = if (blurTopBar) 0f else 1f),
                    scrolledContainerColor = background.copy(alpha = if (blurTopBar) 0f else 1f),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = floatingActionButton,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassSource(LocalGlassHazeState.current),
        ) {
            content(innerPadding)
        }
    }
}
