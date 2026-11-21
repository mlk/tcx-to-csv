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
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private fun fmt1(v: Double?): String = v?.let { roundTo(it, 1) } ?: ""
private fun fmt2(v: Double?): String = v?.let { roundTo(it, 2) } ?: ""
private fun fmt6(v: Double?): String = v?.let { roundTo(it, 6) } ?: ""
private fun roundTo(v: Double, decimals: Int): String {
    // Avoid "-0.0" in output.
    val normalized = if (abs(v) < 0.5 * Math.pow(10.0, -decimals.toDouble())) 0.0 else v
    return String.format(Locale.ROOT, "%.${decimals}f", normalized)
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

        val rawInput: InputStream = if (input == "-") {
            System.`in`
        } else {
            val f = File(input)
            if (!f.isFile) throw CliktError("cannot read input file: $input")
            f.inputStream()
        }

        val rawOutput = output?.let { File(it).outputStream() } ?: System.out
        val closeOutput = output != null
        val writer = PrintWriter(BufferedWriter(OutputStreamWriter(rawOutput, StandardCharsets.UTF_8)))

        var firstTime: Instant? = null
        var lastBucket = -1L
        var rowsWritten = 0L
        var rowsSkipped = 0L

        try {
            // All columns are generated values (ISO instants / numbers), so no CSV quoting is needed.
            writer.println(header.joinToString(","))
            rawInput.use { ins ->
                parseTrackpoints(ins) { tp ->
                    val start = firstTime ?: tp.time.also { firstTime = it }
                    val elapsed = Duration.between(start, tp.time).seconds
                    val bucket = Math.floorDiv(elapsed, downsample.toLong())
                    if (bucket == lastBucket) {
                        rowsSkipped++
                        return@parseTrackpoints
                    }
                    lastBucket = bucket

                    val cols = buildList {
                        add(DateTimeFormatter.ISO_INSTANT.format(tp.time))
                        add(elapsed.toString())
                        add(fmt1(tp.distanceM))
                        add(tp.hrBpm?.toString() ?: "")
                        add(fmt1(tp.altitudeM))
                        if (withLatLon) { add(fmt6(tp.lat)); add(fmt6(tp.lon)) }
                        if (withCadence) add(tp.cadence?.toString() ?: "")
                        if (withSpeed) add(fmt2(tp.speed))
                        if (withLap) add(tp.lap.toString())
                    }
                    writer.println(cols.joinToString(","))
                    rowsWritten++
                }
            }
            writer.flush()
        } catch (e: TcxParseException) {
            throw CliktError(e.message ?: "failed to parse TCX")
        } finally {
            if (closeOutput) writer.close() else writer.flush()
        }

        if (output != null) {
            echo("wrote $rowsWritten rows to $output" + if (rowsSkipped > 0) " ($rowsSkipped downsampled away)" else "", err = true)
        }
    }
}

fun main(argv: Array<String>) = TcxToCsv().main(argv)
