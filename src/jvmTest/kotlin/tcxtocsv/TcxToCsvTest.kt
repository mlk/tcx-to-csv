package tcxtocsv

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.parse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class TcxToCsvTest {

    @TempDir
    lateinit var tempDir: File

    private fun tcxFile(content: String, name: String = "in.tcx"): File =
        File(tempDir, name).apply { writeText(content) }

    /** Runs the command, capturing stdout/stderr and optionally stubbing stdin. */
    private fun runCli(args: List<String>, stdin: ByteArray? = null): Pair<String, String> {
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val oldOut = System.out
        val oldErr = System.err
        val oldIn = System.`in`
        System.setOut(PrintStream(outBytes, true, "UTF-8"))
        System.setErr(PrintStream(errBytes, true, "UTF-8"))
        stdin?.let { System.setIn(ByteArrayInputStream(it)) }
        try {
            TcxToCsv().parse(args)
        } finally {
            System.setOut(oldOut)
            System.setErr(oldErr)
            System.setIn(oldIn)
        }
        return outBytes.toString(Charsets.UTF_8) to errBytes.toString(Charsets.UTF_8)
    }

    @Test
    fun `writes core columns to stdout by default`() {
        val file = tcxFile(TestTcx.document(TestTcx.sequence(3)))
        val (out, _) = runCli(listOf(file.absolutePath))

        val lines = out.trim().lines()
        assertEquals("time,elapsed_s,distance_m,hr_bpm,altitude_m", lines[0])
        assertEquals(4, lines.size) // header + 3 rows
        assertEquals("2026-11-21T09:00:00Z,0,0.0,150,123.4", lines[1])
        assertEquals("2026-11-21T09:00:02Z,2,6.0,152,123.4", lines[3])
    }

    @Test
    fun `elapsed_s is seconds since the first trackpoint`() {
        val file = tcxFile(TestTcx.document(listOf(
            TestTcx.trackpoint(time = "2026-11-21T09:00:00Z", distance = 0.0),
            TestTcx.trackpoint(time = "2026-11-21T09:00:05Z", distance = 15.0),
        )))
        val (out, _) = runCli(listOf(file.absolutePath))
        val lines = out.trim().lines()
        assertTrue(lines[1].startsWith("2026-11-21T09:00:00Z,0,"))
        assertTrue(lines[2].startsWith("2026-11-21T09:00:05Z,5,"))
    }

    @Test
    fun `downsample keeps first trackpoint per N-second bucket`() {
        val file = tcxFile(TestTcx.document(TestTcx.sequence(25)))
        val (out, _) = runCli(listOf(file.absolutePath, "--downsample", "10"))

        val lines = out.trim().lines()
        assertEquals(4, lines.size) // header + buckets 0,10,20
        assertTrue(lines[1].startsWith("2026-11-21T09:00:00Z,0,"))
        assertTrue(lines[2].startsWith("2026-11-21T09:00:10Z,10,"))
        assertTrue(lines[3].startsWith("2026-11-21T09:00:20Z,20,"))
    }

    @Test
    fun `downsampled-away rows keep the first value of the bucket`() {
        val file = tcxFile(TestTcx.document(listOf(
            TestTcx.trackpoint(time = "2026-11-21T09:00:00Z", hr = 111),
            TestTcx.trackpoint(time = "2026-11-21T09:00:02Z", hr = 199),
        )))
        val (out, _) = runCli(listOf(file.absolutePath, "--downsample", "5"))
        val lines = out.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains(",111,"))
    }

    @Test
    fun `optional columns are appended in a stable order`() {
        val file = tcxFile(TestTcx.document(listOf(TestTcx.trackpoint())))
        val (out, _) = runCli(listOf(file.absolutePath, "--latlon", "--cadence", "--speed", "--lap"))

        val lines = out.trim().lines()
        assertEquals("time,elapsed_s,distance_m,hr_bpm,altitude_m,lat,lon,cadence,speed_mps,lap", lines[0])
        assertEquals("2026-11-21T09:00:00Z,0,100.0,150,123.4,59.913900,10.752200,88,3.40,1", lines[1])
    }

    @Test
    fun `writing to a file via -o produces identical content and reports on stderr`() {
        val input = tcxFile(TestTcx.document(TestTcx.sequence(30)))
        val target = File(tempDir, "out.csv")
        val (out, err) = runCli(listOf(input.absolutePath, "-o", target.absolutePath, "--downsample", "10"))

        assertEquals("", out)
        assertTrue(err.contains("wrote 3 rows"))
        assertTrue(err.contains("downsampled"))
        val lines = target.readText().trim().lines()
        assertEquals(4, lines.size)
    }

    @Test
    fun `reading from stdin with dash works`() {
        val doc = TestTcx.document(TestTcx.sequence(2))
        val (out, _) = runCli(listOf("-"), stdin = doc.toByteArray())
        assertEquals(3, out.trim().lines().size)
    }

    @Test
    fun `invalid downsample value is rejected`() {
        val file = tcxFile(TestTcx.document(TestTcx.sequence(1)))
        assertFailsWith<CliktError> {
            TcxToCsv().parse(listOf(file.absolutePath, "--downsample", "0"))
        }
    }

    @Test
    fun `missing input file is rejected`() {
        assertFailsWith<CliktError> {
            TcxToCsv().parse(listOf(File(tempDir, "nope.tcx").absolutePath))
        }
    }

    @Test
    fun `malformed timestamp surfaces as a CLI error`() {
        val file = tcxFile(TestTcx.document(listOf(TestTcx.trackpoint(time = "not-a-time"))))
        assertFailsWith<CliktError> {
            TcxToCsv().parse(listOf(file.absolutePath))
        }
    }

    @Test
    fun `negative zero altitude prints as 0dot0`() {
        val file = tcxFile(TestTcx.document(listOf(TestTcx.trackpoint(altitude = -0.04))))
        val (out, _) = runCli(listOf(file.absolutePath))
        assertTrue(out.contains(",0.0"))
        assertFalse(out.contains("-0.0"))
    }

    @Test
    fun `missing values render as empty cells`() {
        val file = tcxFile(TestTcx.document(listOf(
            TestTcx.trackpoint(distance = null, hr = null, altitude = null),
        )))
        val (out, _) = runCli(listOf(file.absolutePath))
        val line = out.trim().lines()[1]
        assertEquals("2026-11-21T09:00:00Z,0,,,", line)
    }

    @Test
    fun `lap column increments across laps`() {
        val file = tcxFile(TestTcx.document(
            listOf(TestTcx.trackpoint(time = "2026-11-21T09:00:00Z")),
            listOf(TestTcx.trackpoint(time = "2026-11-21T09:05:00Z")),
        ))
        val (out, _) = runCli(listOf(file.absolutePath, "--lap"))
        val lines = out.trim().lines()
        assertTrue(lines[1].endsWith(",1"))
        assertTrue(lines[2].endsWith(",2"))
    }
}
