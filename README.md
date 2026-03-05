# SeedSearch

Searches through Slay the Spire seeds. For how to run the seed searcher, see the [upstream README](https://github.com/ForgottenArbiter/SeedSearch/blob/master/README.md).

## Project layout

| Path | Description |
|------|-------------|
| `configs/` | Search parameters. Edit `searchConfig.json` for your overrides. |
| `  └── defaultSearchConfig.json` | Full list of possible search params and defaults |
| `  └── searchConfig.json` | Your search overrides; created on first run, merges with defaults (gitignored) |
| `  └── regressionTestConfig.json` | Config for regression tests; seed range, player class, and non-filter settings |
| `preferences/` | Game save/prefs state used by the headless sim (gitignored; created at runtime) |
| `search_results/` | YAML output from seed searches (gitignored; created at runtime) |
| `sendToDevs/` | Corrupt prefs and logs preserved for debugging (gitignored; created at runtime) |
| `spire_src/` | Decompiled Slay the Spire source code (the simulator) |
| `src/main/java/seedsearch/` | Seed searcher Java packages |
| `  └── core/` | Simulation kernel, models, bootstrap; no deps on seed_search or web_app |
| `  └── seed_search/` | CLI batch search (SeedSearch, SeedRunner, RegressionTest) |
| `  └── seed_explorer/` | Step-by-step run engine; no web deps |
| `  └── seed_explorer/web_app/` | Javalin HTTP layer for web explorer |
| `src/main/resources/seed_explorer/web_app/` | Static web app files (index.html, app.js, style.css) |
| `target/` | Build output; runnable JARs go here |
| `test/` | Intentionally brittle regression tests; any sim change that alters deterministic output fails |

## Build and run

**Build everything:**
```bash
mvn package
```

**Seed search** (CLI):
```bash
java -jar target/SeedSearch.jar
```

**Web app** (seed explorer):
```bash
java -jar target/SeedSearch-Web.jar
```

**Regression tests** (ensure sim matches expected seed outcomes):
```bash
java -jar target/SeedSearch-Regression.jar
```
Config: `configs/regressionTestConfig.json`. Expected YAML files go in `test/regression/expected/` (e.g. `silent_a20_seed_1.yaml`).
