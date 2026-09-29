package plus.rua.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MomentsLocationTest {

    @Test
    fun getDefaultLocations_containsNoneItemAndCityAndPois() {
        val locations = MomentsLocationProvider.getDefaultLocations("深圳市")
        assertTrue(locations.isNotEmpty())
        assertTrue(locations.first().isNone)
        assertEquals("深圳市", locations[1].name)
        assertTrue(locations.any { it.name == "创维半导体设计大厦" })
    }

    @Test
    fun filterLocations_emptyQuery_returnsAll() {
        val original = MomentsLocationProvider.getDefaultLocations()
        val filtered = MomentsLocationProvider.filterLocations(original, "")
        assertEquals(original.size, filtered.size)
    }

    @Test
    fun filterLocations_matchesExistingPoi() {
        val original = MomentsLocationProvider.getDefaultLocations()
        val filtered = MomentsLocationProvider.filterLocations(original, "创维")
        assertTrue(filtered.any { it.name == "创维半导体设计大厦" })
        assertTrue(filtered.any { it.name == "创维大厦" })
    }

    @Test
    fun filterLocations_unmatchedQuery_addsCustomLocationOption() {
        val original = MomentsLocationProvider.getDefaultLocations()
        val filtered = MomentsLocationProvider.filterLocations(original, "猫咪咖啡馆")
        assertEquals("猫咪咖啡馆", filtered.first().name)
        assertEquals("自定义位置", filtered.first().address)
    }
}
