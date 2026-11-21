# tcx-to-csv

Small CLI that converts a Garmin/Polar `.tcx` activity file into a compact CSV of trackpoints.

## Build

```sh
./gradlew installDist
```

The binary lands at `build/install/tcx-to-csv/bin/tcx-to-csv`.

## Usage

```sh
tcx-to-csv activity.tcx                  # CSV to stdout
tcx-to-csv activity.tcx -o out.csv       # CSV to file
cat activity.tcx | tcx-to-csv -          # stdin
tcx-to-csv activity.tcx --downsample 5   # one row per 5 s, keeping the first point of each bucket
tcx-to-csv activity.tcx --latlon --cadence --speed --lap
```

| Flag | Effect |
| --- | --- |
| `-o, --output FILE` | Write to `FILE` instead of stdout |
| `--downsample N` | Keep the first trackpoint of each N-second window (measured from the first trackpoint). Default `1` = every trackpoint. Use `5` or `10` for roughly 80–90% smaller files with negligible loss for split/HR work |
| `--latlon` | Add `lat,lon` columns (decimal degrees, 6 decimals) |
| `--cadence` | Add `cadence` (steps/min; top-level `<Cadence>`, falling back to TPX `<RunCadence>`/`<Cadence>`) |
| `--speed` | Add `speed_mps` (m/s, from TPX `<Speed>` extension) |
| `--lap` | Add `lap` (1-based index of the enclosing `<Lap>`) |

### Default columns

`time` (ISO-8601, UTC), `elapsed_s` (seconds since the first trackpoint), `distance_m` (1 decimal),
`hr_bpm` (integer), `altitude_m` (1 decimal). Missing values render as empty cells.

Notes:

- Only trackpoint data is emitted; `<Lap>` totals (calories etc.) are not included.
- `elapsed_s` and `--downsample` buckets are relative to the first trackpoint in the file.
- Distance/altitude are rounded to 1 decimal; lat/lon to 6 decimals; times are normalized to UTC.
- The parser uses local XML element names, so namespaced TPX extensions (e.g. `ns3:Speed`) are matched.

## Tests

```sh
./gradlew test jacocoTestReport jacocoTestCoverageVerification
```

Coverage is enforced at ≥ 80% line coverage (currently ~98%). Report: `build/reports/jacoco/test/html/index.html`.
