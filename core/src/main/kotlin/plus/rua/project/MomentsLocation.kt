package plus.rua.project

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 朋友圈地理位置项。
 *
 * @param name 位置名称（如“创维半导体设计大厦”、“深圳市”或“不显示位置”）
 * @param address 详细地址与距离描述（如“603m | 广东省深圳市南山区高新南四道18号”）
 * @param isCityOnly 是否仅展示城市级别
 * @param isNone 是否代表“不显示位置”
 */
data class MomentLocationItem(
    val name: String,
    val address: String? = null,
    val isCityOnly: Boolean = false,
    val isNone: Boolean = false,
)

/**
 * 朋友圈位置信息提供器，包含默认真实 POI 列表与系统定位/地理逆编码。
 */
object MomentsLocationProvider {

    const val NONE_LOCATION_NAME = "不显示位置"

    val NONE_ITEM = MomentLocationItem(
        name = NONE_LOCATION_NAME,
        isNone = true,
    )

    /**
     * 获取预置的真实 POI 列表（基于微信经典高新园附近位置，与用户截屏一致）。
     */
    fun getDefaultLocations(currentCity: String = "深圳市"): List<MomentLocationItem> = listOf(
        NONE_ITEM,
        MomentLocationItem(name = currentCity, isCityOnly = true),
        MomentLocationItem(
            name = "创维半导体设计大厦",
            address = "603m | 广东省深圳市南山区高新南四道18号",
        ),
        MomentLocationItem(
            name = "光后未来中心",
            address = "487m | 广东省深圳市南山区高新南四道88号",
        ),
        MomentLocationItem(
            name = "高新园",
            address = "687m | 广东省深圳市南山区地铁1号线(罗宝线), 地铁20号线",
        ),
        MomentLocationItem(
            name = "创维半导体设计大厦西座",
            address = "601m | 广东省深圳市南山区创维半导体设计大厦裙楼",
        ),
        MomentLocationItem(
            name = "泰邦科技大厦",
            address = "691m | 广东省深圳市南山区高新南六道16号",
        ),
        MomentLocationItem(
            name = "创维大厦",
            address = "381m | 广东省深圳市南山区高新南一道8号",
        ),
        MomentLocationItem(
            name = "华润城万象天地",
            address = "924m | 广东省深圳市南山区粤海街道深南大道9668号",
        ),
        MomentLocationItem(
            name = "中国科技开发院",
            address = "665m | 广东省深圳市南山区高新南一道与科技南十路交叉口",
        ),
        MomentLocationItem(
            name = "深圳湾科技生态园",
            address = "1.2km | 广东省深圳市南山区沙河西路辅路与高新南环路交叉口",
        ),
        MomentLocationItem(
            name = "中科大厦",
            address = "553m | 广东省深圳市南山区高新南一道9号",
        ),
        MomentLocationItem(
            name = "工勘大厦",
            address = "779m | 广东省深圳市南山区粤海科技南八路8号",
        ),
        MomentLocationItem(
            name = "金蝶软件园",
            address = "1.0km | 广东省深圳市南山区高新南十二路2号",
        ),
        MomentLocationItem(
            name = "腾讯大厦",
            address = "820m | 广东省深圳市南山区深南大道10000号",
        ),
    )

    /**
     * 尝试通过系统 GPS/网络定位服务与 Geocoder 解析当前自动定位位置。
     */
    suspend fun resolveLocation(context: Context): List<MomentLocationItem> = withContext(Dispatchers.IO) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return@withContext getDefaultLocations()
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext getDefaultLocations()

        var bestLocation: Location? = null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                        bestLocation = loc
                    }
                }
            } catch (_: SecurityException) {
                // 忽略权限异常
            } catch (_: Exception) {}
        }

        if (bestLocation == null || !Geocoder.isPresent()) {
            return@withContext getDefaultLocations()
        }

        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(bestLocation.latitude, bestLocation.longitude, 5)
            if (!addresses.isNullOrEmpty()) {
                val primary = addresses[0]
                val city = primary.locality ?: primary.subAdminArea ?: primary.adminArea ?: "深圳市"
                val poiName = primary.featureName ?: primary.thoroughfare ?: city
                val detailedAddress = primary.getAddressLine(0) ?: "$city ${primary.thoroughfare ?: ""}"

                val detectedItems = mutableListOf<MomentLocationItem>()
                detectedItems.add(NONE_ITEM)
                detectedItems.add(MomentLocationItem(name = city, isCityOnly = true))
                if (poiName != city && poiName.isNotBlank()) {
                    detectedItems.add(
                        MomentLocationItem(
                            name = poiName,
                            address = detailedAddress,
                        ),
                    )
                }

                // 合并默认列表中的其它选项（避免重复）
                for (item in getDefaultLocations(city)) {
                    if (detectedItems.none { it.name == item.name }) {
                        detectedItems.add(item)
                    }
                }
                return@withContext detectedItems
            }
        } catch (_: Exception) {
            // Geocoder 网络或超时异常，优雅降级
        }

        return@withContext getDefaultLocations()
    }

    /**
     * 根据搜索关键词过滤位置列表。若输入自定义位置，则置顶提供“创建自定义位置”。
     */
    fun filterLocations(allLocations: List<MomentLocationItem>, query: String): List<MomentLocationItem> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return allLocations
        }

        val filtered = allLocations.filter { item ->
            !item.isNone && (
                item.name.contains(trimmed, ignoreCase = true) ||
                    (item.address?.contains(trimmed, ignoreCase = true) == true)
                )
        }.toMutableList()

        val exactMatch = filtered.any { it.name.equals(trimmed, ignoreCase = true) }
        if (!exactMatch) {
            filtered.add(
                0,
                MomentLocationItem(
                    name = trimmed,
                    address = "自定义位置",
                ),
            )
        }

        filtered.add(NONE_ITEM)
        return filtered
    }
}
