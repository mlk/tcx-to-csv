package tcxtocsv

object TestTcx {

    /** Builds one <Trackpoint>. Null arguments omit the element. */
    fun trackpoint(
        time: String? = "2026-11-21T09:00:00Z",
        distance: Double? = 100.0,
        hr: Int? = 150,
        altitude: Double? = 123.4,
        lat: Double? = 59.9139,
        lon: Double? = 10.7522,
        cadence: Int? = 88,
        speed: Double? = 3.4,
        runCadence: Int? = null,
        tpxCadence: Int? = null,
        rawDistance: String? = null,
    ): String = buildString {
        append("<Trackpoint>")
        time?.let { append("<Time>$it</Time>") }
        if (lat != null || lon != null) {
            append("<Position>")
            lat?.let { append("<LatitudeDegrees>$it</LatitudeDegrees>") }
            lon?.let { append("<LongitudeDegrees>$it</LongitudeDegrees>") }
            append("</Position>")
        }
        altitude?.let { append("<AltitudeMeters>$it</AltitudeMeters>") }
        rawDistance?.let { append("<DistanceMeters>$it</DistanceMeters>") }
            ?: distance?.let { append("<DistanceMeters>$it</DistanceMeters>") }
        hr?.let { append("<HeartRateBpm><Value>$it</Value></HeartRateBpm>") }
        cadence?.let { append("<Cadence>$it</Cadence>") }
        if (speed != null || runCadence != null || tpxCadence != null) {
            append("<Extensions><ns3:TPX xmlns:ns3=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2\">")
            speed?.let { append("<Speed>$it</Speed>") }
            runCadence?.let { append("<RunCadence>$it</RunCadence>") }
            tpxCadence?.let { append("<Cadence>$it</Cadence>") }
            append("</ns3:TPX></Extensions>")
        }
        append("</Trackpoint>")
    }

    /** Wraps laps (each a list of trackpoint XML strings) in a TCX document. */
    fun document(vararg laps: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\">").append('\n')
        append("<Activities><Activity Sport=\"Running\"><Id>2026-11-21T09:00:00Z</Id>").append('\n')
        for (lap in laps) {
            append("<Lap StartTime=\"2026-11-21T09:00:00Z\"><TotalTimeSeconds>1</TotalTimeSeconds><Track>")
            for (tp in lap) append(tp).append('\n')
            append("</Track></Lap>").append('\n')
        }
        append("</Activity></Activities></TrainingCenterDatabase>")
    }

    /** N trackpoints, 1 Hz apart starting at 2026-11-21T09:00:00Z. */
    fun sequence(n: Int, hr: (Int) -> Int? = { 150 + it }, distance: (Int) -> Double? = { it * 3.0 }): List<String> =
        (0 until n).map { i ->
            trackpoint(
                time = "2026-11-21T09:%02d:%02dZ".format((i / 60) % 60, i % 60),
                distance = distance(i),
                hr = hr(i),
            )
        }
}
