# PNG

## 1. Summary

PNG (Portable Network Graphics) stores an image without losing any detail. It was created to replace GIF after the licensing problem with GIF's compression method [BB §1.5.4, p.14]. A PNG file is an 8-byte signature followed by a list of chunks. The pixel rows are filtered, compressed with zlib (which uses DEFLATE) and stored in IDAT chunks. The main trade-off is that the picture comes back exactly as it was saved and can have transparency, but the file for a photo is usually bigger than the same photo as a JPEG.

The book covers PNG in about half a page. The byte-level details in this write-up come from RFC 2083 and from our own programs.

## 2. File layout

### Signature

Every PNG file starts with these 8 bytes: `89 50 4E 47 0D 0A 1A 0A`. Each part has a job (RFC 2083 section 3.1).

| Bytes | Meaning |
|-------|---------|
| `89` | Not a text character. Catches transfers that strip the top bit or treat the file as text |
| `50 4E 47` | The letters P, N, G |
| `0D 0A` | Carriage return and line feed. Catches a conversion of Windows line endings |
| `1A` | Stops the display on old DOS systems if the file is printed as text |
| `0A` | Line feed. Catches a conversion of Unix line endings |

The book shows only the first 4 bytes [BB Table 1.2, p.20]. The spec gives all 8, and our file has all 8.

### Chunks

After the signature the file is a list of chunks. Every chunk has the same four parts (RFC 2083 section 3.2).

| Part | Size | Meaning |
|------|------|---------|
| Length | 4 bytes | Number of bytes in the data part only |
| Type | 4 bytes | Four letters, for example IHDR |
| Data | Length bytes | The content of the chunk |
| CRC | 4 bytes | Check value over the type and the data |

So a chunk takes 12 bytes plus the length of its data. CRC stands for cyclic redundancy check.

The critical chunks are IHDR, IDAT and IEND. IHDR (image header) comes first and describes the image. IDAT (image data) holds the compressed pixels. There can be several IDAT chunks, one after the other. IEND (image end) comes last and has no data. A PLTE chunk holds the colour palette and is needed only for palette images. Other optional chunks, such as text comments, can sit between IHDR and IEND. A chunk type that starts with an uppercase letter is critical, and one that starts with a lowercase letter is optional.

### Byte order

Every number longer than one byte in a PNG file is big endian, which means the biggest byte comes first (RFC 2083 section 2.1, [BB §1.5.9, pp.19 to 20]). This is the same byte order idea we covered in Session 23. The number 13 is stored as `00 00 00 0D`. A program on a little-endian machine would have to reverse the four bytes before using the number. In Java, `ByteBuffer` reads big endian by default, so `getInt` gives the right value without any swapping.

## 3. A tiny file, byte by byte

We made `samples/png/t16.png` with Java's ImageIO from the test image T16. T16 is 16 by 16 pixels with R = x * 17, G = y * 17, B = 128 and full opacity. The file is 352 bytes. The table comes from the output of `PngDemo`.

| Offset | Size | Part | Bytes | Meaning |
|--------|------|------|-------|---------|
| 0x00 (0) | 8 | Signature | `89 50 4E 47 0D 0A 1A 0A` | PNG signature |
| 0x08 (8) | 4 | IHDR length | `00 00 00 0D` | 13 data bytes |
| 0x0C (12) | 4 | IHDR type | `49 48 44 52` | The letters IHDR |
| 0x10 (16) | 4 | Width | `00 00 00 10` | 16 pixels |
| 0x14 (20) | 4 | Height | `00 00 00 10` | 16 pixels |
| 0x18 (24) | 1 | Bit depth | `08` | 8 bits per sample |
| 0x19 (25) | 1 | Colour type | `06` | RGB with alpha |
| 0x1A (26) | 1 | Compression | `00` | zlib with DEFLATE |
| 0x1B (27) | 1 | Filter method | `00` | The five standard row filters |
| 0x1C (28) | 1 | Interlace | `00` | No interlacing |
| 0x1D (29) | 4 | IHDR CRC | `1F F3 FF 61` | Matches our CRC32 calculation |
| 0x21 (33) | 4 | IDAT length | `00 00 01 27` | 295 data bytes |
| 0x25 (37) | 4 | IDAT type | `49 44 41 54` | The letters IDAT |
| 0x29 (41) | 295 | IDAT data | `78 5E 15 CC 41 81 45 11 00 00 C0 57 E1 57 50 41 ...` | zlib stream |
| 0x150 (336) | 4 | IDAT CRC | `50 03 2F 84` | Matches our CRC32 calculation |
| 0x154 (340) | 4 | IEND length | `00 00 00 00` | No data |
| 0x158 (344) | 4 | IEND type | `49 45 4E 44` | The letters IEND |
| 0x15C (348) | 4 | IEND CRC | `AE 42 60 82` | Matches our CRC32 calculation |

The file has only the three critical chunks. ImageIO added no other chunks. The sizes add up. IHDR takes 12 + 13 = 25 bytes, IDAT takes 12 + 295 = 307 bytes and IEND takes 12 bytes, so 8 + 25 + 307 + 12 = 352.

The IHDR data is 13 bytes: width (4), height (4), bit depth (1), colour type (1), compression method (1), filter method (1) and interlace method (1). The spec allows a width or height up to 2^31 - 1 (RFC 2083 section 4.1.1). The book says 2^30 [BB §1.5.4, p.14], so we follow the spec.

The CRC covers the type and the data of a chunk, and not the length. We checked this by calculating the CRC both ways for every chunk.

| Chunk | Stored CRC | CRC of type and data | CRC of length, type and data |
|-------|-----------|----------------------|------------------------------|
| IHDR | `1F F3 FF 61` | match | no match |
| IDAT | `50 03 2F 84` | match | no match |
| IEND | `AE 42 60 82` | match | no match |

The CRC can detect damage but cannot repair it. A damaged length field is not covered directly. It would still almost always be noticed, because the decoder would read the wrong number of bytes and then compare the wrong four bytes with a CRC it calculated over the wrong range. IEND has no data, so its CRC covers only the four letters of its type.

## 4. How the data is encoded

1. Turn each pixel into bytes. In T16 every pixel is 4 bytes: red, green, blue, alpha.
2. Put a filter byte at the start of each row and rewrite the row with that filter.
3. Join all the rows into one stream of bytes.
4. Compress the stream with zlib, which uses DEFLATE.
5. Cut the compressed data into IDAT chunks and add the signature, IHDR and IEND.

### Step 1: pixels as bytes

The colour type in IHDR says how many values each pixel has (RFC 2083 section 4.1.1). The table is for 8 bits per sample. With 16 bits per sample every value takes 2 bytes. Section 4.1.1 of the spec lists which bit depths each colour type allows.

| Colour type | Meaning | Values per pixel | Bytes per pixel |
|-------------|---------|------------------|-----------------|
| 0 | Grayscale | 1 | 1 |
| 2 | RGB | 3 | 3 |
| 3 | Palette (index into PLTE) | 1 | 1 |
| 4 | Grayscale with alpha | 2 | 2 |
| 6 | RGB with alpha | 4 | 4 |

T16 uses colour type 6. One row is 16 pixels of 4 bytes, so 64 bytes, plus 1 filter byte, so 65 bytes. Sixteen rows give 1040 bytes before compression. `PngDemo` confirms this by inflating the IDAT data and getting 1040 bytes back, in 16 rows of 65 bytes.

### Step 2: filters

Each row starts with one byte that names the filter used for that row. A filter replaces every byte of the row with its difference from a guess based on neighbouring bytes. The arithmetic wraps around at 256. A byte left of the first pixel and the row above the first row count as 0.

| Filter byte | Name | Guess that is subtracted |
|-------------|------|--------------------------|
| 0 | None | Nothing |
| 1 | Sub | The byte one pixel to the left |
| 2 | Up | The byte above |
| 3 | Average | The average of left and above, rounded down |
| 4 | Paeth | Whichever of left, above and upper left is closest to left + above - upper left |

A worked example from `PngFilterDemo`, with one byte per pixel:

```
Row above    98 100 103 107 112 118
Current row  100 102 105 109 114 120
0 None       100 102 105 109 114 120
1 Sub        100 2 3 4 5 6
2 Up         2 2 2 2 2 2
3 Average    51 2 3 3 4 4
4 Paeth      2 2 2 2 2 2
```

The filter does not make the data smaller. It has the same number of bytes, plus the filter byte. What it does is turn smooth areas into small numbers and repeats, which the next step compresses well. For the first 4 pixels of row 1 of T16, the Up filter gives `00 11 00 00` repeated four times, instead of the raw bytes `00 11 80 FF 11 11 80 FF 22 11 80 FF 33 11 80 FF`.

The encoder may choose a different filter for each row. In our `t16.png` the first byte of all 16 rows is `00`, so ImageIO used None everywhere. The compression in this sample comes from DEFLATE alone.

### Steps 3 and 4: compression

The joined rows go through zlib. A zlib stream has 2 header bytes, the DEFLATE data and a 4-byte check value that is not the PNG CRC (RFC 2083 section 5, RFC 1950). In our file the header is `78 5E`. The `78` means DEFLATE with a 32 KB window.

DEFLATE combines two ideas and is from the same family as ZIP (RFC 1951). LZ77 replaces a repeated run of bytes with a short pointer to an earlier copy. Huffman coding gives short codes to common byte values and long codes to rare ones. Nothing is thrown away, so the decoder gets back the exact bytes. In T16 the 1040 bytes became 295 bytes.

### Step 5: IDAT chunks

The compressed bytes are placed in one or more IDAT chunks. A decoder joins the data of all IDAT chunks before inflating, so it does not matter where the cuts fall. Our file needs only one IDAT chunk.

### Transparency and interlacing

Alpha, the transparency value, exists only in colour types 4 and 6. It adds one more value to every pixel, so an RGBA image has one more byte per pixel than an RGB image, which is a third more raw data before compression.

The interlace method in IHDR is 0 for no interlacing or 1 for Adam7. Adam7 stores the image in seven passes over an 8 by 8 pattern, so a rough picture can appear early while the file loads. Our file uses 0.

## 5. Strengths, weaknesses, when to use it

Strengths: the image is stored exactly, it supports transparency, it can use 8 or 16 bits per sample, and a palette is available for images with few colours. Each chunk has its own CRC, so damage is easy to detect.

Weaknesses: a photo is usually much bigger than a JPEG of the same photo. A PNG file holds one image, so the basic format does not do animation [BB §1.5.4, p.14].

Use PNG for screenshots, logos, diagrams, and any image that needs sharp edges, transparency or exact pixels. Use JPEG for photos when a small file matters more than exact pixels. GIF is still used for simple animation, and `04-gif.md` covers it. Compared with GIF, PNG allows many more colours and smooth transparency.

## 6. Common mix-ups

Filtering is not compression. The filter byte and the filter change the values but not the number of bytes. DEFLATE does the compressing. Also, the filter is chosen per row, and in our sample file every row uses None.

The CRC does not cover the whole chunk. It covers the type and the data. The length field is left out, as the table in section 3 shows.

The signature is not just the letters PNG. The book lists 4 bytes, but the file starts with 8 bytes and the other bytes each catch a specific kind of damage.

## 7. How we checked it

`PngInspector` reads a PNG file and splits it into segments: the signature, then the length, type, data and CRC of every chunk. It splits the IHDR data into its seven fields. It also calculates each CRC with `java.util.zip.CRC32` and compares it with the stored value.

`PngDemo` writes `samples/png/t16.png` with ImageIO, runs the inspector and prints the table in section 3. It then checks that the segments cover every byte, compares the CRC calculated with and without the length field, and joins and inflates the IDAT data to show the size and the filter byte of each row.

`PngFilterDemo` applies the five filters to a short row of numbers and to the first 4 pixels of a row of T16, so the filter examples in section 4 are real output.

To compile and run from the repository root:

```
javac -d out src/imgformats/*.java
java -cp out imgformats.PngDemo
java -cp out imgformats.PngFilterDemo
```

What we checked:

- The segments cover all 352 bytes with no gap or overlap.
- Each chunk takes its data length plus 12 bytes on disk.
- The chunk order is IHDR, IDAT, IEND, with no optional chunks.
- The CRC of the type and the data matches the stored value for all three chunks. The CRC that includes the length matches for none.
- The IDAT data starts with `78 5E`.
- The IDAT data of 295 bytes inflates to 1040 bytes, which is 16 rows of 65 bytes. The first byte of every row is `00`.

## 8. References

- Burger and Burge, Digital Image Processing, 2016, section 1.5 (page 14 on PNG, pages 19 to 20 on byte order, Table 1.2 on page 20).
- RFC 2083, PNG (Portable Network Graphics) Specification, version 1.0: sections 2.1, 3.1, 3.2, 3.4, 4.1.1 to 4.1.4, 5 and 6.
- RFC 1950, ZLIB Compressed Data Format Specification.
- RFC 1951, DEFLATE Compressed Data Format Specification.
