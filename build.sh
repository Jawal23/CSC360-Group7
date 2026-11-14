#!/bin/sh
# Compile everything into out/ and run the demos from the repo root.
set -e
cd "$(dirname "$0")"

rm -rf out
javac -d out src/imgformats/*.java

# ComparisonDemo goes last so it sees the sample files the other demos wrote.
# JpegInspector is the JPEG entry point: it writes the sample, then prints the table.
for demo in FoundationsDemo PngDemo PngFilterDemo JpegInspector JpegDctDemo GifDemo GifLzwDemo ComparisonDemo; do
    if [ -f "out/imgformats/$demo.class" ]; then
        echo "== $demo =="
        java -Djava.awt.headless=true -cp out "imgformats.$demo"
        echo
    else
        echo "== $demo: class not found, skipped =="
    fi
done
