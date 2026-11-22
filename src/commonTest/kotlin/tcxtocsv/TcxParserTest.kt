package tcxtocsv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class TcxParserTest {

    private fun parse(xml: String): List<Trackpoint> {
        val rows = mutableListOf<Trackpoint>()
        parseTrackpoints(xml) { rows.add(it) }
        return rows
    }

    @Test
    fun `parses a fully populated trackpoint`() {
        val rows = parse(TestTcx.document(listOf(TestTcx.trackpoint())))
        assertEquals(1, rows.size)
        val tp = rows[0]
        assertEquals(Instant.parse("2026-11-21T09:00:00Z"), tp.time)
        assertEquals(100.0, tp.distanceM)
        assertEquals(150, tp.hrBpm)
        assertEquals(123.4, tp.altitudeM)
        assertEquals(59.9139, tp.lat)
        assertEquals(10.7522, tp.lon)
        assertEquals(88, tp.cadence)
        assertEquals(3.4, tp.speed)
        assertEquals(1, tp.lap)
    }

    @Test
    fun `missing optional elements stay null`() {
        val tp = parse(TestTcx.document(listOf(
            TestTcx.trackpoint(distance = null, hr = null, altitude = null, lat = null, lon = null, cadence = null, speed = null),
        )))[0]
        assertNull(tp.distanceM)
        assertNull(tp.hrBpm)
        assertNull(tp.altitudeM)
        assertNull(tp.lat)
        assertNull(tp.lon)
        assertNull(tp.cadence)
        assertNull(tp.speed)
    }

    @Test
    fun `top-level cadence wins over TPX cadences`() {
        val tp = parse(TestTcx.document(listOf(
            TestTcx.trackpoint(cadence = 90, runCadence = 180, tpxCadence = 88),
        )))[0]
        assertEquals(90, tp.cadence)
    }

    @Test
    fun `TPX RunCadence is used when top-level cadence is absent`() {
        val tp = parse(TestTcx.document(listOf(
            TestTcx.trackpoint(cadence = null, runCadence = 182, tpxCadence = 88),
        )))[0]
        assertEquals(182, tp.cadence)
    }

    @Test
    fun `TPX Cadence is used as last resort`() {
        val tp = parse(TestTcx.document(listOf(
            TestTcx.trackpoint(cadence = null, tpxCadence = 85),
        )))[0]
        assertEquals(85, tp.cadence)
    }

    @Test
    fun `lap index increments across Lap elements`() {
        val lap1 = listOf(TestTcx.trackpoint(), TestTcx.trackpoint())
        val lap2 = listOf(TestTcx.trackpoint())
        val rows = parse(TestTcx.document(lap1, lap2))
        assertEquals(listOf(1, 1, 2), rows.map { it.lap })
    }

    @Test
    fun `timestamps with offset are normalised to instants`() {
        val tp = parse(TestTcx.document(listOf(
            TestTcx.trackpoint(time = "2026-11-21T10:00:00+01:00"),
        )))[0]
        assertEquals(Instant.parse("2026-11-21T09:00:00Z"), tp.time)
    }

    @Test
    fun `unparseable time raises TcxParseException`() {
        assertFailsWith<TcxParseException> {
            parse(TestTcx.document(listOf(TestTcx.trackpoint(time = "not-a-time"))))
        }
    }

    @Test
    fun `missing time raises TcxParseException`() {
        assertFailsWith<TcxParseException> {
            parse(TestTcx.document(listOf(TestTcx.trackpoint(time = null))))
        }
    }

    @Test
    fun `non-numeric values are tolerated as missing`() {
        val tp = parse(TestTcx.document(listOf(
            TestTcx.trackpoint(rawDistance = "abc", hr = null),
        )))[0]
        assertNull(tp.distanceM)
    }

    @Test
    fun `namespaced Speed elements are picked up`() {
        val tp = parse(TestTcx.document(listOf(TestTcx.trackpoint(speed = 2.75))))[0]
        assertEquals(2.75, tp.speed)
    }

    @Test
    fun `empty document yields no rows`() {
        assertEquals(emptyList(), parse(TestTcx.document(emptyList())))
    }
}
