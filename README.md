# tcx-to-csv

Convert a Garmin/Polar `.tcx` activity file into a compact CSV of trackpoints.

[![CI](https://github.com/mlk/tcx-to-csv/actions/workflows/ci.yml/badge.svg)](https://github.com/mlk/tcx-to-csv/actions/workflows/ci.yml)

## Quick start

Prerequisites: **JDK 21+** (the Gradle wrapper handles everything else).

```sh
git clone https://github.com/mlk/tcx-to-csv.git
cd tcx-to-csv
./gradlew installDist
build/install/tcx-to-csv/bin/tcx-to-csv --help
```

Optionally put it on your `PATH`:

```sh
ln -s "$PWD/build/install/tcx-to-csv/bin/tcx-to-csv" ~/.local/bin/tcx-to-csv
```

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
src/main/kotlin/tcxtocsv/TcxParser.kt   streaming StAX parser for TCX <Trackpoint> elements
src/main/kotlin/tcxtocsv/TcxToCsv.kt    Clikt command: flags, downsample bucketing, CSV output
src/test/kotlin/tcxtocsv/               JUnit 5 tests (parser + end-to-end CLI)
gradle/libs.versions.toml               dependency version catalog
```

Common tasks:

```sh
./gradlew build                  # compile + test + coverage verification
./gradlew test                   # run tests only
./gradlew check                  # test + JaCoCo coverage gate
./gradlew installDist            # build the binary in build/install/tcx-to-csv/
./gradlew --refresh-dependencies # after changing dependency versions
```

Coverage is enforced at **≥ 80% line coverage** (currently ~98%). The HTML report is at
`build/reports/jacoco/test/html/index.html`.

### CI

GitHub Actions (`.github/workflows/ci.yml`) runs on every push/PR: tests, the 80% coverage gate, an
`installDist` + `--help` smoke check, and uploads the test/coverage reports as artifacts. PRs also get an
automated review from the Ollama review action, and Dependabot keeps GitHub Actions and the Gradle
version catalog up to date (minor/patch bumps grouped, Kotlin bumps in their own PRs).
