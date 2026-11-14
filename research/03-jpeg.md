# JPEG

## 1. Summary

JPEG is the usual format for photographs. It keeps file sizes small by throwing away fine detail that the eye barely notices. The trade-off is that this loss can never be undone.

JPEG itself is the compression method (ITU-T T.81). Most `.jpg` files also follow JFIF, a file convention that adds a small APP0 header at the start.

We look at four binary details of a real file: the markers, the offsets and lengths of each segment, the image properties, and the quantization tables.

## 2. File layout

A JPEG file is a list of segments. Each segment starts with a marker, which is the byte `FF` followed by a code byte.

| Marker | Bytes | Name | What it holds |
|--------|-------|------|---------------|
| SOI | `FF D8` | Start of image | Nothing, it is the file signature |
| APP0 | `FF E0` | Application 0 | JFIF identification and version |
| DQT | `FF DB` | Define quantization table | 64 divisors used to throw away detail |
| SOF0 | `FF C0` | Start of frame | Width, height, precision, colour components |
| DHT | `FF C4` | Define Huffman table | Codes used to pack the data into bits |
| SOS | `FF DA` | Start of scan | The compressed image data follows it |
| EOI | `FF D9` | End of image | Nothing, it ends the file |

Length rule. After every marker except SOI and EOI comes a 2-byte length. It is big-endian, and it counts its own 2 bytes but not the 2 marker bytes. So a segment takes up length + 2 bytes on disk.

## 3. A tiny file, byte by byte

We wrote the 16x16 test image (R = x*17, G = y*17, B = 128) with Java ImageIO at its default settings. The file `samples/jpeg/t16.jpg` is 659 bytes.

### Markers, offsets and lengths

| Offset | Bytes on disk | Segment | First bytes | Length field |
|--------|---------------|---------|-------------|--------------|
| 0x000 (0) | 2 | SOI | `FF D8` | none |
| 0x002 (2) | 18 | APP0 | `FF E0 00 10` | 16 |
| 0x014 (20) | 69 | DQT | `FF DB 00 43 00` | 67 |
| 0x059 (89) | 69 | DQT | `FF DB 00 43 01` | 67 |
| 0x09E (158) | 19 | SOF0 | `FF C0 00 11` | 17 |
| 0x0B1 to 0x1AA | 33, 183, 33, 183 | DHT x 4 | `FF C4 ...` | 31, 181, 31, 181 |
| 0x261 (609) | 14 | SOS | `FF DA 00 0C` | 12 |
| 0x26F (623) | 34 | Compressed data | `E0 AC 34 1E ...` | none |
| 0x291 (657) | 2 | EOI | `FF D9` | none |

Every length field plus 2 matches the bytes on disk. For example DQT has length `00 43` = 67 and takes 69 bytes. Each segment starts exactly where the one before it ends, and the last one ends at byte 659.

The compressed data has no length field. A reader finds its end by looking for the next `FF` that is not followed by `00`. That is the EOI marker here.

### Image properties

The SOF0 segment is `FF C0 00 11 08 00 10 00 10 03` followed by 3 bytes per component.

| Bytes | Meaning |
|-------|---------|
| `08` | Precision, 8 bits per sample |
| `00 10` | Height 16 (big-endian) |
| `00 10` | Width 16 (big-endian) |
| `03` | 3 components: Y (brightness), Cb and Cr (colour) |
| `01 22 00` | Component 1 (Y), sampling 2x2, quantization table 0 |
| `02 11 01` | Component 2 (Cb), sampling 1x1, quantization table 1 |
| `03 11 01` | Component 3 (Cr), sampling 1x1, quantization table 1 |

The sampling byte packs two numbers into 4 bits each. Y has 2 and 2, colour has 1 and 1. So colour is stored at half the width and half the height of brightness. This is called 4:2:0 subsampling. We read it from the file instead of assuming it.

### Quantization tables

Each DQT segment holds one id byte and then 64 values. The file stores them in zigzag order, and our program puts them back into an 8x8 grid. Table 0 is for brightness:

```
   8   6   5   8  12  20  26  31
   6   6   7  10  13  29  30  28
   7   7   8  12  20  29  35  28
   7   9  11  15  26  44  40  31
   9  11  19  28  34  55  52  39
  12  18  28  32  41  52  57  46
  25  32  39  44  52  61  60  51
  36  46  48  49  56  50  52  50
```

Table 1 is for colour. It starts with 9, 9, 12, 24 and most of it is 50. JPEG divides the DCT values of each block (section 4) by these numbers and rounds, which lowers their precision and makes the file smaller. Small numbers at the top-left keep the smooth, large-scale parts of a block. Big numbers at the bottom-right throw away fine detail. The colour table is coarser than the brightness table because the eye is less sharp for colour.

ImageIO writes each table in its own DQT segment, even though the standard allows both in one segment.

## 4. How the data is encoded

1. Convert RGB to brightness (Y) and colour (Cb, Cr)
2. Store colour at lower resolution (4:2:0)
3. Split into 8x8 blocks
4. Apply the DCT to each block
5. Divide by the quantization table and round
6. Pack the result into bits (Huffman coding)

Loss happens only when colour is stored at lower resolution and when values are divided and rounded. The rest can be undone.

DCT and quantization on one block. Our program `JpegDctDemo` takes the top-left 8x8 brightness block of the test image and runs our own DCT. The DCT turns 64 pixel values into 64 numbers that describe the block from smooth to fine. The top-left number is the average level of the block. Our block is a smooth gradient, so almost everything sits in the first row and column:

```
  -484   -92     0   -10     0    -3     0    -1
  -182     1     1     0     0     0     0     0
     0     0     0     0     0     0     0     0
   -19     0     0     0     0     0     0     0
```
(the last 4 rows are almost all 0)

The DCT on its own can be undone. Dividing by the quantization table and rounding is what loses detail. We did this at two quality levels.

| Quality | Values left after rounding | Zero values out of 64 | Biggest pixel error after decoding |
|---------|----------------------------|-----------------------|------------------------------------|
| 75 | -61, -15, -1, -30, -3 | 59 | 2 |
| 25 | -15, -4, -8, -1 | 60 | 6 |

Lower quality means bigger divisors, so more values round to 0 and the decoded block is further from the original. The quality number itself is not stored in the file. It only scales the table that gets stored. This scaling rule is a library convention, not part of the standard.

## 5. Strengths, weaknesses, when to use it

Strengths. Small files for photographs. Adjustable quality. Supported everywhere.

Weaknesses. Lossy, and saving again loses more. No transparency. Text and sharp edges get blurry blocks around them. For tiny images the tables take most of the file. Here 625 of 659 bytes are headers and tables, and only 34 bytes are image data.

When to use it. Photographs. Use PNG for screenshots, logos or anything that needs exact pixels or transparency.

## 6. Common mix-ups

"The DCT compresses the image." It does not. It only rewrites the 64 values in another form, and it can be undone. The rounding in quantization is what throws data away.

"The quality setting is saved in the file." It is not. Only the quantization tables are saved.

"Width and height can be read in either byte order." JPEG is always big-endian. `00 10` means 16, not 4096.

## 7. How we checked it

- `JpegInspector` walks the file marker by marker. Running it writes `samples/jpeg/t16.jpg`, prints every segment with its offset and length, then the image properties and both quantization tables.
- `JpegDctDemo` runs our own DCT on one 8x8 block, then quantizes it at quality 75 and 25.

```
javac -d out src/imgformats/*.java
java -cp out imgformats.JpegInspector
java -cp out imgformats.JpegDctDemo
```

Checks we ran:

- The segments cover all 659 bytes exactly once. The program checks this and printed `Coverage: OK`.
- Every length field plus 2 equals the segment size on disk.
- The sampling factors were read from SOF0.

## 8. References

- W. Burger and M. J. Burge, Digital Image Processing: An Algorithmic Introduction Using Java, 2nd ed., Springer, 2016. Section 1.5, Image File Formats. The book covers JPEG at overview level. The byte-level details come from the specifications below.
- ITU-T Recommendation T.81 (1992). Annex B (markers and segments), Annex A (DCT), Annex K (example quantization tables).
- E. Hamilton, JPEG File Interchange Format, Version 1.02, 1992.
