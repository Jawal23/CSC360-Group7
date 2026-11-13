# JPEG

## 1. Summary

JPEG is the usual format for photographs. It splits the image into 8x8 blocks, turns each block into frequencies with the discrete cosine transform (DCT), rounds away the fine detail the eye barely notices, and packs what is left with Huffman coding. The trade-off is small files in exchange for losing information that can never be recovered.

Strictly speaking JPEG is the compression method, defined in ITU-T T.81. The `.jpg` files we open are wrapped in a file convention on top of it. JFIF adds an APP0 segment with the version and pixel density. Exif, used by cameras, adds an APP1 segment with camera settings stored in a small TIFF structure.

## 2. File layout

A JPEG file is a list of segments. Each one starts with a marker, which is the byte `FF` followed by a code byte. Most markers are followed by a 2-byte length. The length is big-endian and counts its own 2 bytes, but not the 2 marker bytes. The compressed image data after SOS has no length at all.

| Marker | Bytes | Name | Length field? | What it holds |
|--------|-------|------|---------------|---------------|
| SOI | `FF D8` | Start of image | no | Nothing, it is the file signature |
| APP0 | `FF E0` | Application 0 | yes | JFIF identifier, version, density |
| APP1 | `FF E1` | Application 1 | yes | Exif data (not in our file) |
| DQT | `FF DB` | Define quantization table | yes | 64 divisors used to round the DCT values |
| SOF0 | `FF C0` | Start of frame, baseline | yes | Precision, height, width, components, sampling factors |
| DHT | `FF C4` | Define Huffman table | yes | Code lengths and symbols for one Huffman table |
| SOS | `FF DA` | Start of scan | yes | Which tables each component uses. The data follows. |
| EOI | `FF D9` | End of image | no | Nothing, it ends the file |

Byte stuffing. The compressed data can contain the value `FF` by chance, which would look like a marker. So the encoder writes every data `FF` as `FF 00`, and a reader that sees `FF 00` knows it is data. A reader finds the end of the data by looking for an `FF` that is followed by something other than `00` (or a restart marker `D0` to `D7`).

The Exif APP1 segment is where byte order shows up inside a JPEG. Its TIFF header starts with `49 49` ("II", little-endian, Intel) or `4D 4D` ("MM", big-endian, Motorola), and every number after it follows that order. So one JPEG file can hold big-endian marker lengths and little-endian Exif values side by side.

## 3. A tiny file, byte by byte

We wrote the 16x16 test image (R = x*17, G = y*17, B = 128) with Java ImageIO at its default settings. The file `samples/jpeg/t16.jpg` is 659 bytes. Our inspector splits it like this.

| Offset | Size | Segment | First bytes | Meaning |
|--------|------|---------|-------------|---------|
| 0x000 (0) | 2 | SOI | `FF D8` | Start of image |
| 0x002 (2) | 18 | APP0 | `FF E0 00 10 4A 46 49 46 00 01 02` | Length 16, "JFIF" plus a zero byte, version 1.02 |
| 0x014 (20) | 69 | DQT | `FF DB 00 43 00 08 06 06 07` | Length 67, table 0 (brightness), first values 8, 6, 6, 7 |
| 0x059 (89) | 69 | DQT | `FF DB 00 43 01 09 09 09 0C` | Length 67, table 1 (colour) |
| 0x09E (158) | 19 | SOF0 | `FF C0 00 11 08 00 10 00 10 03` | Length 17, 8 bits, height 16, width 16, 3 components |
| 0x0B1 (177) | 33 | DHT | `FF C4 00 1F 00` | Length 31, DC table 0 |
| 0x0D2 (210) | 183 | DHT | `FF C4 00 B5 10` | Length 181, AC table 0 |
| 0x189 (393) | 33 | DHT | `FF C4 00 1F 01` | Length 31, DC table 1 |
| 0x1AA (426) | 183 | DHT | `FF C4 00 B5 11` | Length 181, AC table 1 |
| 0x261 (609) | 14 | SOS | `FF DA 00 0C 03 01 00 02 11 03 11 00 3F 00` | Length 12, 3 components in the scan |
| 0x26F (623) | 34 | Compressed data | `E0 AC 34 1E 9F 25 75 3A ...` | All pixel data, Huffman coded |
| 0x291 (657) | 2 | EOI | `FF D9` | End of image |

Some things worth pointing out.

- Length rule. APP0 has length `00 10` = 16 but takes 18 bytes on disk, because the 2 marker bytes are not counted. The same holds for every segment above.
- Sampling factors. In SOF0 each component has 3 bytes: id, sampling byte, table number. The bytes are `01 22 00`, `02 11 01`, `03 11 01`. The sampling byte packs horizontal and vertical factors into 4 bits each. Brightness (Y) has 2 and 2, colour (Cb and Cr) has 1 and 1. So colour is kept at half the width and half the height, which is called 4:2:0. One minimum coded unit (MCU) is 16x16 pixels, so the whole image is a single MCU: four Y blocks, one Cb block and one Cr block.
- Tables take most of the space. Everything except the data is 659 - 34 = 625 bytes. The 34 data bytes are the whole image. For a tiny picture JPEG is mostly overhead. The raw RGB pixels would be 16 x 16 x 3 = 768 bytes.
- No byte stuffing here. None of the 34 data bytes is `FF`, so this file has no `FF 00` pairs. In a larger image it is very likely to happen.
- ImageIO compared with the spec. T.81 allows several tables inside one DQT or DHT segment. ImageIO writes each table in its own segment, which is why there are 2 DQT and 4 DHT segments.

## 4. How the data is encoded

1. Convert RGB to YCbCr
2. Subsample the colour channels
3. Split each channel into 8x8 blocks and shift values by -128
4. Apply the DCT to each block
5. Quantize, which is the lossy step
6. Zigzag, run-length and Huffman code the result

Colour conversion. Y is brightness and Cb and Cr are colour differences. JFIF uses Y = 0.299 R + 0.587 G + 0.114 B. Our eyes see detail mainly in brightness, so separating it lets the next steps treat colour more roughly.

Subsampling. Colour is stored at lower resolution. Our file uses 4:2:0, so for every 2x2 pixels it keeps 4 Y values but only 1 Cb and 1 Cr value. This already throws information away.

Blocks. Each channel is cut into 8x8 blocks. 128 is subtracted from each value so the numbers sit around zero. The top-left block of our test image starts with the row -113, -108, -103, -98, -93, -88, -83, -78.

DCT. The DCT rewrites the 64 pixel values as 64 coefficients. The top-left one is the DC coefficient, which is the average level of the block. The others (AC) describe changes from slow to fast. For our block the DC value is -484 and the next largest are -182 and -92. Almost every other coefficient rounds to 0 or close to it, because the block is a smooth gradient. A completely flat block (every pixel 100) gives DC = -224 and exactly zero everywhere else. The DCT on its own loses nothing. Running it forward and back without quantization gave a biggest pixel error of 0 in our test.

Quantization. Each coefficient is divided by the matching entry of the quantization table and rounded to a whole number. The table has small divisors for slow changes and large ones for fast changes, so fine detail mostly becomes 0. For our block the results were as follows.

| Quality | Non-zero coefficients left | Zero coefficients | Biggest pixel error after decoding |
|---------|----------------------------|-------------------|------------------------------------|
| 75 | 5 (-61, -15, -1, -30, -3) | 59 of 64 | 2 |
| 25 | 4 (-15, -4, -8, -1) | 60 of 64 | 6 |

Entropy coding. The 64 values are read in a zigzag order from top-left to bottom-right, so the zeros bunch up at the end. Runs of zeros are stored as a count, and each DC value is stored as the difference from the previous block's DC. Finally Huffman coding gives short bit codes to common values and longer ones to rare values, using the tables in the DHT segments. This last stage is lossless.

Where the loss happens. Only in subsampling and quantization. The colour conversion and the DCT can be undone apart from tiny rounding, and the entropy coding is exact.

The quality number. Quality is not stored in the file. The encoder uses it to scale a base table, and only the scaled table is written into DQT. The scaling rule is a library convention from the Independent JPEG Group, not part of T.81. We scaled the example table from T.81 Annex K with that rule at quality 75, and it matched table 0 in our ImageIO file exactly. So ImageIO's default quality is 75.

Artifacts and progressive. At low quality the 8x8 grid becomes visible (blocking), and sharp edges get faint ripples around them (ringing). Text and line drawings suffer most. A progressive JPEG stores the same coefficients in several scans, coarse first, so a blurry full image shows up early while loading. Our file is baseline (SOF0), with one scan.

## 5. Strengths, weaknesses, when to use it

Strengths. Very small files for photographs. The quality can be tuned. Every browser, camera and phone supports it.

Weaknesses. Lossy, and the loss adds up every time a file is opened, edited and saved again. No transparency. Sharp edges, text and flat colour areas get artifacts. For very small images the tables cost more than the data, as our 659-byte file shows.

When to use it. Photographs and other natural images. For screenshots, logos, diagrams or anything that needs transparency or exact pixels, use PNG. For simple animations, GIF.

## 6. Common mix-ups

"The DCT compresses the image." It does not. It turns 64 values into 64 other values and can be inverted. Our test gave zero error with no quantization. The DCT only makes the values easy to throw away. Quantization does the throwing.

"The quality setting is saved in the file." It is not. Only the resulting quantization tables are saved. Two programs can give different files for the same quality number, because the scaling is a library convention.

"JPEG and JFIF are the same thing." JPEG (T.81) is the compression method. JFIF and Exif are file conventions that add an APP0 or APP1 segment around it.

## 7. How we checked it

Three classes in `src/imgformats/`:

- `JpegInspector` walks the markers and returns one segment per marker, with the compressed data as one segment. It finds the end of the data by skipping `FF 00` and restart markers.
- `JpegDemo` writes the test image to `samples/jpeg/t16.jpg` with ImageIO, prints the segment table and the coverage check, and reads the JFIF version and sampling factors from the file.
- `JpegDctDemo` takes the top-left 8x8 brightness block, runs our own DCT, quantizes at quality 75 and 25, rebuilds the block and prints the biggest error. It also tests a flat block and compares our quality 75 table with the one in the file.

Compile and run from the repository root:

```
javac -d out src/imgformats/*.java
java -cp out imgformats.JpegDemo
java -cp out imgformats.JpegDctDemo
```

Checks we ran:

- The segments cover all 659 bytes exactly once (`checkCoverage` printed `null`).
- Every length field plus 2 equals the segment size on disk.
- The sampling factors were read from SOF0, not assumed.
- A flat block gives only a DC coefficient.
- DCT then inverse DCT with no quantization gives a biggest error of 0.
- Our quality 75 table equals DQT table 0 in the file (printed `true`).

We did not write a decoder. The Huffman step is explained but not decoded.

## 8. References

- W. Burger and M. J. Burge, Digital Image Processing: An Algorithmic Introduction Using Java, 2nd ed., Springer, 2016. Section 1.5, Image File Formats. The book covers JPEG at overview level. The byte-level details above come from the specifications below.
- ITU-T Recommendation T.81 (1992), Digital compression and coding of continuous-tone still images. Annex A (DCT and level shift), Annex B (marker and segment syntax, byte stuffing), Annex F (baseline Huffman coding, zigzag order), Annex K (example quantization tables).
- E. Hamilton, JPEG File Interchange Format, Version 1.02, 1992. APP0 layout and the RGB to YCbCr conversion.
- CIPA DC-008, Exchangeable image file format for digital still cameras (Exif). APP1 layout and the TIFF header byte order mark.
- Independent JPEG Group, libjpeg documentation. Quality scaling of the quantization tables.
