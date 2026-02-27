#!/bin/bash
# libGDX 1.12.1 has arm64 Mac natives; run natively.
exec java -jar target/SeedSearch.jar "$@"
