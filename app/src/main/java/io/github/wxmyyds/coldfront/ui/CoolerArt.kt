package io.github.wxmyyds.coldfront.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
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
 * 散热器产品图:有图的型号显示官方产品照,其余回退雪花图标。
 * 可指定暗色背景下的图标颜色(如首页仪表盘深色卡)。
 *
 * 无障碍:刻意不暴露 contentDescription 参数——四个调用点旁边都已有文字报出设备名/型号
 * (首页英雄卡、设备行、扇描行、RGB 预览卡),产品图属装饰性重复信息,
 * 按规范应置 null 以免 TalkBack 重报。若将来出现“只有图、没有文字”的用法,
 * 再加参数并在该处传入型号名。
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
        CoolerDeviceType.JACKET_6, CoolerDeviceType.JACKET_6_PRO -> R.drawable.img_cooler_6pro
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
            Icons.Filled.AcUnit,
            contentDescription = null,
            tint = iconTint,
            modifier = modifier,
        )
    }
}
