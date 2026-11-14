# Foundations: files, pixels and byte order

This page covers the ideas the three format write-ups rely on. Everything here is checked by `FoundationsDemo`.

## What an image file format is

An image file is a list of bytes that follows a fixed plan. Most formats have three parts:

1. A signature, a few bytes at the very start that name the format.
2. A header, with the facts a decoder needs before it can read pixels, such as width, height and colour type.
3. The pixel data, almost always compressed.

Many formats also end with a marker or a checksum so a decoder knows the file is complete. A program finds the format from the signature, so the file extension is only a hint. `ComparisonDemo` works this way. It reads the first bytes of each sample and names the format from those bytes alone.

## The pixel in memory

Our test image, T16, is 16 x 16, which gives 256 pixels. Each pixel has R = x * 17, G = y * 17 and B = 128, and it is fully opaque. The program counted 256 distinct colours, so every pixel has its own colour.

With 8 bits per channel, one pixel takes 3 bytes in RGB and 4 bytes in RGBA. A decoder holds the image as one long byte array and finds a pixel with

```
index = (row * width + column) * bytesPerPixel
```

For the pixel at x = 5, y = 3 the index is (3 * 16 + 5) * 3 = 159. The three bytes stored there are `55 33 80`, which is R = 85, G = 51, B = 128.

| Layout | Bytes per pixel | Raw size of T16 |
|--------|-----------------|-----------------|
| RGB | 3 | 768 bytes |
| RGBA | 4 | 1,024 bytes |

These raw sizes are the baseline. Every file size in `05-comparison.md` is easiest to read next to 768 bytes.

## Byte order

A number that needs more than one byte can be stored with the biggest byte first (big-endian) or the smallest byte first (little-endian). The 32 bit number `0x12345678` shows the difference.

| Byte order | Bytes in the file |
|------------|-------------------|
| Big-endian | `12 34 56 78` |
| Little-endian | `78 56 34 12` |

The same thing happens to our width of 16.

| How it is written | Bytes |
|-------------------|-------|
| 32 bit big-endian | `00 00 00 10` |
| 16 bit big-endian | `00 10` |
| 16 bit little-endian | `10 00` |

PNG stores its integers as 4 byte big-endian numbers, which is also called network byte order. JPEG stores its segment lengths as 2 byte big-endian numbers. GIF stores width and height as 2 byte little-endian numbers. The three format write-ups check this on the real files.

## Lossless and lossy

A lossless format gives back exactly the pixels that went in. A lossy format throws some information away to make the file smaller. The loss happens in a specific step, so it is worth knowing where.

| Format | Where information is lost |
|--------|---------------------------|
| PNG | Nowhere. Filtering and DEFLATE are both reversible. |
| JPEG | Quantization and chroma subsampling. |
| GIF | LZW itself is lossless. Reducing a photo to 256 colours is the lossy step. |

T16 has exactly 256 distinct colours, so it fits a GIF palette without any reduction.

## How we checked it

`FoundationsDemo` builds T16, packs it into plain RGB and RGBA byte arrays, counts the distinct colours and prints the numbers above. It also writes `0x12345678` and the width 16 with `ByteBuffer` in both byte orders.

```
javac -d out src/imgformats/*.java
java -cp out imgformats.FoundationsDemo
```

`build.sh` and `build.ps1` run this demo together with the others.

## References

- Burger and Burge, Digital Image Processing: An Algorithmic Introduction Using Java, 2nd ed., 2016, section 1.5, image file formats and byte order (pages 19 to 20).
- RFC 2083, PNG Specification version 1.0, section 2.1, for the PNG byte order.
- Graphics Interchange Format, Version 89a, for the GIF byte order.
- ITU-T T.81 and the JFIF 1.02 specification, for the JPEG segment lengths.