package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.wxmyyds.coldfront.R
import io.github.wxmyyds.coldfront.domain.CoolerDeviceType

/**
 * 散热器图片：有产品图的型号显示对应图片，其他型号显示雪花图标。
 * 暗色背景下也可指定图标颜色，例如首页仪表盘的深色卡片。
 *
 * 无障碍：这里不提供 contentDescription 参数。四个调用点旁都有设备名或型号文字（首页英雄卡、设备行、扫描结果行、RGB 预览卡），图片只是重复信息，因此设为 null 可避免 TalkBack 重复播报。若以后用于只有图片、没有文字的场景，再增加参数并传入型号名。
 */
@Composable
fun CoolerArt(
    type: CoolerDeviceType?,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val res = when (type) {
        CoolerDeviceType.JACKET_8_PRO -> R.drawable.img_cooler_8pro
        CoolerDeviceType.JACKET_4 -> R.drawable.img_cooler_4pro
        CoolerDeviceType.JACKET_6,
        CoolerDeviceType.JACKET_6_PRO,
        -> R.drawable.img_cooler_6pro
        CoolerDeviceType.JACKET_6_PRO_PLUS -> R.drawable.img_cooler_6proplus
        CoolerDeviceType.JACKET_1 -> R.drawable.img_cooler_dual
        CoolerDeviceType.JACKET_2 -> R.drawable.img_cooler_turbo
        CoolerDeviceType.JACKET_3 -> R.drawable.img_cooler_gen3
        CoolerDeviceType.JACKET_5 -> R.drawable.img_cooler_5pro
        else -> 0
    }
    if (res != 0) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        Icon(
            painterResource(R.drawable.materialsymbols_ic_ac_unit_rounded_filled),
            contentDescription = null,
            tint = iconTint,
            modifier = modifier,
        )
    }
}
