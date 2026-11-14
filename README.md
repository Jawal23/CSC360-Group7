# PNG, JPEG and GIF at byte level

This is our group project for CSC360 Computer Graphics and Digital Image Processing at Ahmedabad University, Monsoon 2026. The topic was to explain the popular image formats with binary-level details. For each of PNG, JPEG and GIF we wrote a short report and a few small Java programs that read the bytes of a real file and label them.

All three formats are built from the same 16 x 16 test image (T16), so their sizes can be compared fairly. Each pixel has R = x * 17, G = y * 17 and B = 128.

## What you need

Java 17 or newer. The code uses only the JDK, so there is nothing else to install.

## Build and run

From the repo root on Linux or macOS:

```
./build.sh
```

On Windows PowerShell:

```
.\build.ps1
```

Both scripts compile `src/imgformats/*.java` into `out/` and then run the demos in this order: `FoundationsDemo`, `PngDemo`, `PngFilterDemo`, `JpegInspector`, `JpegDctDemo`, `GifDemo`, `GifLzwDemo` and `ComparisonDemo`. The PNG, JPEG and GIF programs write their sample file into `samples/` and print a table of labelled byte ranges. They also check that the labelled ranges cover every byte of the file exactly once.

To run one demo by hand:

```
javac -d out src/imgformats/*.java
java -cp out imgformats.PngDemo
```

## Folder map

```
README.md           this file
build.sh            compile and run on Linux or macOS
build.ps1           compile and run on Windows
research/
  01-foundations.md   file formats, pixels, raw sizes, byte order
  02-png.md           PNG report
  03-jpeg.md          JPEG report
  04-gif.md           GIF report
  05-comparison.md    side by side table with measured sizes
  references.md       books and specifications we used
src/imgformats/     all Java code, one package
samples/            image files the demos generate
slides/             the final presentation as a PDF
```

## The code

Four small classes are shared by everything else.

| Class | What it does |
|-------|--------------|
| `Segment` | One labelled byte range: offset, length, name, category and a short description |
| `FormatInspector` | The interface each format inspector implements |
| `HexDump` | Prints bytes as hex, prints a table of segments and checks coverage |
| `TestImage` | Builds the T16 image |

Each format has an inspector that splits a real file into segments and a demo that writes the sample and prints the table (for JPEG the inspector does both). Each format also has one small program for its main idea: `PngFilterDemo`, `JpegDctDemo` and `GifLzwDemo`. `FoundationsDemo` checks the raw sizes and byte order, and `ComparisonDemo` lists the sample files with their sizes and the format their first bytes name.

## Authors

| Name | Part |
|------|------|
| Yug Bangoriya | PNG, `research/02-png.md` |
| Devraj Sangharajka | JPEG, `research/03-jpeg.md` |
| Dvij Desai | GIF, `research/04-gif.md` |
| Jawal Agrawal | Foundations, comparison, build scripts and slides |

## Sources

See `research/references.md`.
