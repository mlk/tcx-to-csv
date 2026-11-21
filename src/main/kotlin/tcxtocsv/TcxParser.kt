package tcxtocsv

import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/** One parsed <Trackpoint>. Missing optional values stay null (rendered as empty cells). */
data class Trackpoint(
    val time: Instant,
    val distanceM: Double?,
    val hrBpm: Int?,
    val altitudeM: Double?,
    val lat: Double?,
    val lon: Double?,
    /** Resolved cadence: top-level <Cadence>, else TPX <RunCadence>, else TPX <Cadence>. */
    val cadence: Int?,
    /** TPX <Speed>, metres per second. */
    val speed: Double?,
    /** 1-based index of the enclosing <Lap>, counting laps across the whole file. */
    val lap: Int,
)

class TcxParseException(message: String) : Exception(message)

private fun parseInstantFlexible(raw: String): Instant = try {
    Instant.parse(raw)
} catch (_: Exception) {
    try {
        OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
    } catch (_: Exception) {
        throw TcxParseException("unparseable <Time> value: '$raw'")
    }
}

private fun String.toDoubleLenient(): Double? = trim().toDoubleOrNull()
private fun String.toIntLenient(): Int? = trim().toDoubleOrNull()?.toInt()

/**
 * Streams <Trackpoint> elements from a TCX document, invoking [onRow] in document order.
 * Only one trackpoint of state is held at a time, so multi-hour files parse in O(1) memory.
 *
 * Namespace prefixes are ignored (comparisons use local names), so both
 * `<Speed>` and `<ns3:Speed>` style TPX extensions are picked up.
 */
fun parseTrackpoints(input: InputStream, onRow: (Trackpoint) -> Unit) {
    val factory = XMLInputFactory.newFactory().apply {
        // TCX is trusted local data, but keep entity expansion locked down anyway.
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty("javax.xml.stream.isSupportingExternalEntities", false)
    }
    val reader = factory.createXMLStreamReader(input, Charsets.UTF_8.name())

    val stack = ArrayDeque<String>()
    var lap = 0
    var inTrackpoint = false

    var time: Instant? = null
    var distance: Double? = null
    var hr: Int? = null
    var altitude: Double? = null
    var lat: Double? = null
    var lon: Double? = null
    var cadence: Int? = null
    var tpxRunCadence: Int? = null
    var tpxCadence: Int? = null
    var speed: Double? = null

    fun resetTrackpoint() {
        time = null; distance = null; hr = null; altitude = null
        lat = null; lon = null; cadence = null
        tpxRunCadence = null; tpxCadence = null; speed = null
    }

    try {
        while (reader.hasNext()) {
            when (reader.eventType) {
                XMLStreamConstants.START_ELEMENT -> {
                    val name = reader.localName
                    when (name) {
                        "Lap" -> lap += 1
                        "Trackpoint" -> {
                            inTrackpoint = true
                            resetTrackpoint()
                        }
                    }
                    stack.addLast(name)
                }

                XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    val text = reader.text.trim()
                    if (inTrackpoint && text.isNotEmpty() && stack.isNotEmpty()) {
                        val leaf = stack.last()
                        val parent = if (stack.size >= 2) stack[stack.size - 2] else null
                        when {
                            leaf == "Time" && parent == "Trackpoint" ->
                                time = parseInstantFlexible(text)
                            leaf == "AltitudeMeters" && parent == "Trackpoint" ->
                                altitude = text.toDoubleLenient()
                            leaf == "DistanceMeters" && parent == "Trackpoint" ->
                                distance = text.toDoubleLenient()
                            leaf == "LatitudeDegrees" && parent == "Position" ->
                                lat = text.toDoubleLenient()
                            leaf == "LongitudeDegrees" && parent == "Position" ->
                                lon = text.toDoubleLenient()
                            leaf == "Value" && parent == "HeartRateBpm" ->
                                hr = text.toIntLenient()
                            leaf == "Cadence" && parent == "Trackpoint" ->
                                cadence = text.toIntLenient()
                            leaf == "Cadence" && parent == "TPX" ->
                                tpxCadence = text.toIntLenient()
                            leaf == "RunCadence" && parent == "TPX" ->
                                tpxRunCadence = text.toIntLenient()
                            leaf == "Speed" ->
                                speed = text.toDoubleLenient()
                        }
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    if (reader.localName == "Trackpoint") {
                        val t = time
                            ?: throw TcxParseException("<Trackpoint> is missing required <Time> (lap $lap)")
                        onRow(
                            Trackpoint(
                                time = t,
                                distanceM = distance,
                                hrBpm = hr,
                                altitudeM = altitude,
                                lat = lat,
                                lon = lon,
                                cadence = cadence ?: tpxRunCadence ?: tpxCadence,
                                speed = speed,
                                lap = lap,
                            )
                        )
                        inTrackpoint = false
                        resetTrackpoint()
                    }
                    stack.removeLast()
                }
            }
            reader.next()
        }
    } finally {
        reader.close()
    }
}
