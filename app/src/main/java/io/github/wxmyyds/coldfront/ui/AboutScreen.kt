package io.github.wxmyyds.coldfront.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import io.github.wxmyyds.coldfront.BuildConfig
import io.github.wxmyyds.coldfront.R
import io.github.wxmyyds.coldfront.ui.component.PageScaffold
import io.github.wxmyyds.coldfront.ui.component.SegmentedGroup
import io.github.wxmyyds.coldfront.ui.component.SegmentedRow
import io.github.wxmyyds.coldfront.ui.i18n.LocalStrings

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val contentScrollState = rememberScrollState()
    fun openLink(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, strings.aboutOpenLinkFailed, Toast.LENGTH_LONG).show()
        }
    }
    PageScaffold(
        title = strings.settingsAbout,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ms_arrow_back_fill1_24),
                    contentDescription = strings.back,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(contentScrollState),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 仿 KernelSU MD3E 版 About 头部：80dp 白底 16dp 圆角块 + 彩色前景式 logo
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                    )
                }
                Text(
                    modifier = Modifier.padding(top = 12.dp),
                    text = strings.appName,
                    fontWeight = FontWeight.Medium,
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                )
                Text(
                    text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                SegmentedGroup {
                    item(key = "report") {
                        SegmentedRow(
                            title = strings.settingsAboutReport,
                            leadingContent = { Icon(painterResource(R.drawable.materialsymbols_ic_bug_report_rounded_filled), contentDescription = null) },
                            trailingContent = {
                                Icon(painterResource(R.drawable.materialsymbols_ic_chevron_right_rounded_filled), contentDescription = null)
                            },
                            onClick = {
                                openLink("https://github.com/wxmyyds/ColdFront/issues/new")
                            },
                        )
                    }
                    item(key = "project") {
                        SegmentedRow(
                            title = strings.settingsAboutProject,
                            leadingContent = { Icon(painterResource(R.drawable.materialsymbols_ic_public_rounded_filled), contentDescription = null) },
                            trailingContent = {
                                Icon(painterResource(R.drawable.materialsymbols_ic_chevron_right_rounded_filled), contentDescription = null)
                            },
                            onClick = {
                                openLink("https://github.com/wxmyyds/ColdFront")
                            },
                        )
                    }
                }
            }
        }
    }
}
