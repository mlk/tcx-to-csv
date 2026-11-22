package tcxtocsv

import kotlin.time.Instant
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming

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
    throw TcxParseException("unparseable <Time> value: '$raw'")
}

private fun String.toDoubleLenient(): Double? = trim().toDoubleOrNull()
private fun String.toIntLenient(): Int? = trim().toDoubleOrNull()?.toInt()

/**
 * Parses <Trackpoint> elements from a TCX document, invoking [onRow] in document order.
 * Only a single trackpoint of state is held at a time.
 *
 * Namespace prefixes are ignored (comparisons use local element names), so both
 * `<Speed>` and `<ns3:Speed>` style TPX extensions are picked up.
 */
fun parseTrackpoints(xml: String, onRow: (Trackpoint) -> Unit) {
    val reader = xmlStreaming.newReader(xml)

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

    val currentText = StringBuilder()

    fun resetTrackpoint() {
        time = null; distance = null; hr = null; altitude = null
        lat = null; lon = null; cadence = null
        tpxRunCadence = null; tpxCadence = null; speed = null
    }

    fun dispatchLeaf(name: String, text: String) {
        val parent = if (stack.size >= 2) stack[stack.size - 2] else null
        when {
            name == "Time" && parent == "Trackpoint" -> time = parseInstantFlexible(text)
            name == "AltitudeMeters" && parent == "Trackpoint" -> altitude = text.toDoubleLenient()
            name == "DistanceMeters" && parent == "Trackpoint" -> distance = text.toDoubleLenient()
            name == "LatitudeDegrees" && parent == "Position" -> lat = text.toDoubleLenient()
            name == "LongitudeDegrees" && parent == "Position" -> lon = text.toDoubleLenient()
            name == "Value" && parent == "HeartRateBpm" -> hr = text.toIntLenient()
            name == "Cadence" && parent == "Trackpoint" -> cadence = text.toIntLenient()
            name == "Cadence" && parent == "TPX" -> tpxCadence = text.toIntLenient()
            name == "RunCadence" && parent == "TPX" -> tpxRunCadence = text.toIntLenient()
            name == "Speed" -> speed = text.toDoubleLenient()
        }
    }

    try {
        while (reader.hasNext()) {
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    val name = reader.localName
                    when (name) {
                        "Lap" -> lap += 1
                        "Trackpoint" -> {
                            inTrackpoint = true
                            resetTrackpoint()
                        }
                    }
                    stack.addLast(name)
                    currentText.clear()
                }

                EventType.TEXT, EventType.CDSECT, EventType.IGNORABLE_WHITESPACE ->
                    currentText.append(reader.text)

                EventType.END_ELEMENT -> {
                    val name = reader.localName
                    val text = currentText.toString().trim()
                    if (inTrackpoint && text.isNotEmpty()) {
                        dispatchLeaf(name, text)
                    }
                    if (name == "Trackpoint") {
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
                    currentText.clear()
                }

                else -> Unit
            }
        }
    } catch (e: TcxParseException) {
        throw e
    } catch (e: Exception) {
        throw TcxParseException("failed to parse TCX: ${e.message}")
    } finally {
        try {
            reader.close()
        } catch (_: Exception) {
            // ignore close failures
        }
    }
}
