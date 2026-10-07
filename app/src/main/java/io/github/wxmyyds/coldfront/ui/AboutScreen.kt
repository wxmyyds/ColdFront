package io.github.wxmyyds.coldfront.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(112.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer),
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    strings.appName,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(88.dp))
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
