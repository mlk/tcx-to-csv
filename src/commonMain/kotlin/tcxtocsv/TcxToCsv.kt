package tcxtocsv

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import kotlin.time.Instant

private val POW10 = doubleArrayOf(1.0, 10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0)
private val POW10_LONG = longArrayOf(1, 10, 100, 1000, 10000, 100000, 1000000)

/** Formats [value] with exactly [decimals] fraction digits; never emits "-0". */
internal fun formatFixed(value: Double, decimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    val scaled = kotlin.math.round(kotlin.math.abs(value) * POW10[decimals]).toLong()
    val factor = POW10_LONG[decimals]
    return if (scaled == 0L) {
        "0." + "0".repeat(decimals)
    } else {
        val sign = if (value < 0) "-" else ""
        "$sign${scaled / factor}.${(scaled % factor).toString().padStart(decimals, '0')}"
    }
}

private fun fmt1(v: Double?): String = v?.let { formatFixed(it, 1) } ?: ""
private fun fmt2(v: Double?): String = v?.let { formatFixed(it, 2) } ?: ""
private fun fmt6(v: Double?): String = v?.let { formatFixed(it, 6) } ?: ""

/** floor(a / b) for Longs (kotlin.math has no floorDiv). */
private fun floorDiv(a: Long, b: Long): Long {
    var q = a / b
    if ((a xor b) < 0 && a % b != 0L) q -= 1
    return q
}

class TcxToCsv : CliktCommand(name = "tcx-to-csv") {
    override fun help(context: com.github.ajalt.clikt.core.Context) =
        "Convert a Garmin/Polar TCX activity file to a compact CSV of trackpoints."

    private val input by argument("input")
        .help("Path to the .tcx file, or '-' to read from stdin")

    private val output by option("-o", "--output", metavar = "FILE")
        .help("Write CSV to FILE instead of stdout")

    private val downsample by option("--downsample", metavar = "SECONDS")
        .int()
        .default(1)
        .help("Keep the first trackpoint of each N-second window, measured from the first trackpoint. Default: 1 (every trackpoint). Use 5 or 10 for compact output.")

    private val withLatLon by option("--latlon").flag()
        .help("Add lat,lon columns (decimal degrees, 6 decimals)")
    private val withCadence by option("--cadence").flag()
        .help("Add a cadence column (spm; top-level <Cadence>, falling back to TPX <RunCadence>/<Cadence>)")
    private val withSpeed by option("--speed").flag()
        .help("Add a speed column (m/s, from TPX <Speed> extensions)")
    private val withLap by option("--lap").flag()
        .help("Add a lap column (1-based index of the enclosing <Lap>)")

    override fun run() {
        if (downsample < 1) throw CliktError("--downsample must be >= 1 (got $downsample)")

        val header = buildList {
            add("time"); add("elapsed_s"); add("distance_m"); add("hr_bpm"); add("altitude_m")
            if (withLatLon) { add("lat"); add("lon") }
            if (withCadence) add("cadence")
            if (withSpeed) add("speed_mps")
            if (withLap) add("lap")
        }

        val xml = try {
            if (input == "-") readStdinText() else readFileText(input)
        } catch (e: Exception) {
            throw CliktError(if (input == "-") "cannot read from stdin: ${e.message}" else "cannot read input file: $input")
        }

        val csv = StringBuilder()
        // All columns are generated values (ISO instants / numbers), so no CSV quoting is needed.
        csv.append(header.joinToString(",")).append('\n')

        var firstTime: Instant? = null
        var lastBucket = -1L
        var rowsWritten = 0L
        var rowsSkipped = 0L

        try {
            parseTrackpoints(xml) { tp ->
                val start = firstTime ?: tp.time.also { firstTime = it }
                val elapsed = (tp.time - start).inWholeSeconds
                val bucket = floorDiv(elapsed, downsample.toLong())
                if (bucket == lastBucket) {
                    rowsSkipped++
                    return@parseTrackpoints
                }
                lastBucket = bucket

                val cols = buildList {
                    add(tp.time.toString())
                    add(elapsed.toString())
                    add(fmt1(tp.distanceM))
                    add(tp.hrBpm?.toString() ?: "")
                    add(fmt1(tp.altitudeM))
                    if (withLatLon) { add(fmt6(tp.lat)); add(fmt6(tp.lon)) }
                    if (withCadence) add(tp.cadence?.toString() ?: "")
                    if (withSpeed) add(fmt2(tp.speed))
                    if (withLap) add(tp.lap.toString())
                }
                csv.append(cols.joinToString(",")).append('\n')
                rowsWritten++
            }
        } catch (e: TcxParseException) {
            throw CliktError(e.message ?: "failed to parse TCX")
        } catch (e: Exception) {
            throw CliktError("failed to parse TCX: ${e.message}")
        }

        val outputPath = output
        if (outputPath != null) {
            try {
                writeFileText(outputPath, csv.toString())
            } catch (e: Exception) {
                throw CliktError("cannot write output file: $outputPath")
            }
            echo("wrote $rowsWritten rows to $outputPath" + if (rowsSkipped > 0) " ($rowsSkipped downsampled away)" else "", err = true)
        } else {
            print(csv.toString())
        }
    }
}

fun main(args: Array<String>) = TcxToCsv().main(args)
