# tcx-to-csv

Convert a Garmin/Polar `.tcx` activity file into a compact CSV of trackpoints.

Kotlin Multiplatform: the CLI runs on the JVM and as a native macOS binary (Apple Silicon and Intel).

[![CI](https://github.com/mlk/tcx-to-csv/actions/workflows/ci.yml/badge.svg)](https://github.com/mlk/tcx-to-csv/actions/workflows/ci.yml)

## Quick start

Prerequisites: **JDK 21+** (the Gradle wrapper handles everything else).
To build the macOS native binaries you additionally need **macOS with Xcode** installed and its
license accepted (`sudo xcodebuild -license accept`).

JVM jar:

```sh
git clone https://github.com/mlk/tcx-to-csv.git
cd tcx-to-csv
./gradlew fatJar
java -jar build/libs/tcx-to-csv-all.jar --help
```

Native macOS binaries:

```sh
./gradlew macosArm64Binaries macosX64Binaries   # arm64 (Apple Silicon) and x86_64
ls build/bin/macosArm64/releaseExecutable/      # -> tcx-to-csv.kexe (and/or debugExecutable/)
build/bin/macosArm64/releaseExecutable/tcx-to-csv.kexe --help
```

> If your machine has the Xcode license unaccepted but you only have the Command Line Tools
> selected (`xcode-select -p` → `/Library/Developer/CommandLineTools`), Kotlin/Native's Xcode
> detection fails. Fix: `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`
> (and accept the license). Alternatively run Gradle with
> `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`.

## Usage

```sh
tcx-to-csv activity.tcx                  # CSV to stdout
tcx-to-csv activity.tcx -o out.csv       # CSV to file
cat activity.tcx | tcx-to-csv -          # stdin
tcx-to-csv activity.tcx --downsample 5   # one row per 5 s, keeping the first point of each bucket
tcx-to-csv activity.tcx --latlon --cadence --speed --lap
```

Example output (`--downsample 5` on a real export):

```csv
time,elapsed_s,distance_m,hr_bpm,altitude_m
2026-11-21T09:53:07Z,0,0.0,97,150.8
2026-11-21T09:53:12Z,5,9.5,98,150.5
```

### Options

| Flag | Effect |
| --- | --- |
| `-o`, `--output FILE` | Write to `FILE` instead of stdout |
| `--downsample N` | Keep the first trackpoint of each N-second window (measured from the first trackpoint). Default `1` = every trackpoint. Use `5` or `10` for roughly 80–90% smaller files with negligible loss for split/HR work |
| `--latlon` | Add `lat,lon` columns (decimal degrees, 6 decimals) |
| `--cadence` | Add `cadence` (steps/min; top-level `<Cadence>`, falling back to TPX `<RunCadence>`/`<Cadence>`) |
| `--speed` | Add `speed_mps` (m/s, from TPX `<Speed>` extension) |
| `--lap` | Add `lap` (1-based index of the enclosing `<Lap>`) |
| `-h`, `--help` | Show usage |

### Default columns

`time` (ISO-8601, UTC), `elapsed_s` (seconds since the first trackpoint), `distance_m` (1 decimal),
`hr_bpm` (integer), `altitude_m` (1 decimal). Missing values render as empty cells.

Notes:

- Only trackpoint data is emitted; `<Lap>` totals (calories etc.) are not included.
- `elapsed_s` and `--downsample` buckets are relative to the first trackpoint in the file.
- Distance/altitude are rounded to 1 decimal; lat/lon to 6 decimals; times are normalized to UTC.
- The parser uses local XML element names, so namespaced TPX extensions (e.g. `ns3:Speed`) are matched.

## Development

Layout:

```
src/commonMain/kotlin/tcxtocsv/   shared code (parser, CLI, format helpers, IO expect declarations)
src/jvmMain/kotlin/tcxtocsv/      JVM actuals (java.io)
src/macosMain/kotlin/tcxtocsv/    macOS actuals (POSIX posix I/O), shared by macosArm64 + macosX64
src/commonTest/kotlin/tcxtocsv/   parser tests + fixture builder (all targets)
src/jvmTest/kotlin/tcxtocsv/      end-to-end CLI tests (stdout/stderr capture)
gradle/libs.versions.toml         dependency version catalog
```

Common tasks:

```sh
./gradlew jvmTest                # JVM unit + end-to-end tests
./gradlew macosArm64Test         # native tests on Apple Silicon (requires Xcode)
./gradlew check                  # all checks incl. Kover coverage verification
./gradlew fatJar                 # runnable JVM jar -> build/libs/tcx-to-csv-all.jar
./gradlew macosArm64Binaries     # native binary -> build/bin/macosArm64/
```

Coverage is enforced at **≥ 80% line coverage** on the JVM target (currently ~97%) via Kover;
report at `build/reports/kover/html/index.html`.

### Dependencies

Managed in `gradle/libs.versions.toml` and kept current by Dependabot
(github-actions + gradle ecosystems; Kotlin bumps get their own PR). Versions:
Kotlin 2.4.21, Clikt 5.2.1, xmlutil 1.0.2, kotlinx-datetime.

### CI

GitHub Actions (`.github/workflows/ci.yml`):

- **Build & Quality Gates** (ubuntu): `./gradlew check` (tests + 80% coverage gate), `fatJar` build,
  CLI smoke test, test/coverage reports uploaded as artifacts.
- **Build macOS native binaries** (macos-latest): builds `arm64` and `x86_64` binaries, smoke-tests
  the arm64 binary on a sample TCX, uploads both as artifacts.

PRs also get an automated review via the Ollama review action (`.github/workflows/ollama-review.yml`).
