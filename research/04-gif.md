# GIF

## 1. Summary

GIF (Graphics Interchange Format) is a raster format for pictures with at most 256 colours per frame. Each frame picks its colours from a small palette, and the stream of palette indices is packed with LZW compression. The trade-off is simple: GIF is lossless over the indices it stores, but reducing a photo to 256 colours before encoding is itself lossy, so GIF suits flat graphics, icons and short animations more than photographs.

## 2. File layout

A GIF file is a short header followed by a stream of blocks, read in order until a one-byte trailer ends the file.

| Offset | Size | Meaning |
|-------:|-----:|---------|
| 0 | 6 | Signature: `GIF87a` or `GIF89a` |
| 6 | 2 | Logical screen width (little-endian) |
| 8 | 2 | Logical screen height (little-endian) |
| 10 | 1 | Packed byte: global colour table flag, colour resolution, sort flag, GCT size |
| 11 | 1 | Background colour index |
| 12 | 1 | Pixel aspect ratio |
| 13 | 3 * 2^(size+1) | Global colour table (if the flag is set) |
| after GCT | variable | Blocks: extensions (`21`), image descriptors (`2C`), etc. |
| last byte | 1 | Trailer `3B` |

One point that matters for a cross-format slide: GIF stores 16-bit integers **little-endian**, whereas PNG and JPEG use big-endian.

## 3. A tiny file, byte by byte

We generated `samples/gif/t16.gif` from the shared 16x16 test image using Java's `ImageIO`. The file is **1087 bytes**. The first 32 bytes are:

```
47 49 46 38 39 61 10 00 10 00 F7 00 00 00 00 80 00 11 80 11 00 80 11 11 80 00 22 80 00 33 80 11
```

Running `GifInspector` on the file gives the segment table below. Every byte is accounted for.

| Offset | Len | Name | First bytes | Meaning |
|-------:|----:|------|-------------|---------|
| 0x0000 | 6 | Signature | `47 49 46 38 39 61` | Magic bytes `GIF89a` |
| 0x0006 | 7 | LogicalScreenDescriptor | `10 00 10 00 F7 00 00` | w=16, h=16, packed=0xF7 (GCT present, size=7), bg=0, aspect=0 |
| 0x000D | 768 | GlobalColorTable | `00 00 80 00 11 80 ...` | 256 entries, 3 bytes per RGB entry |
| 0x030D | 10 | ImageDescriptor | `2C 00 00 00 00 10 00 10 00 47` | left=0, top=0, w=16, h=16, packed=0x47 (no local table) |
| 0x0317 | 1 | LzwMinCodeSize | `08` | Starting code size = 8 bits |
| 0x0318 | 294 | ImageDataSubBlocks | `FF 00 01 08 40 A0 ...` | 2 sub-blocks, 291 compressed bytes, terminated by `00` |
| 0x043E | 1 | Trailer | `3B` | End of GIF stream |

A few details worth reading off the bytes.

- **Little-endian sizes.** The width bytes are `10 00`. Read little-endian this is `0x0010 = 16`. Read big-endian it would be `0x1000 = 4096`.
- **Packed byte of the LSD is `0xF7`.** Bit 7 set means a global colour table is present. The low three bits are `111`, so the table has `2^(7+1) = 256` entries, which costs 768 bytes.
- **LZW minimum code size is 8.** That is one byte before the image data. The alphabet of palette indices is 0..255, so eight bits are needed for a raw index, and the encoder's starting code size is one more than this.
- **The data ends with a zero-length sub-block.** The last data sub-block is followed by the single byte `00`, and only then comes the trailer `3B`.
- **No Graphic Control Extension here.** Java's `ImageIO` wrote a plain single-frame GIF, so there is no `21 F9` block. Animated GIFs would add a Graphic Control Extension before the image descriptor and a NETSCAPE application extension near the top.

## 4. How the data is encoded

1. **Reduce colours.** Take the RGB image and quantise it to at most 256 colours. Store those colours in the global (or local) colour table. For our test image, the 256 distinct colours fit the palette exactly, so this step is lossless here.
2. **Index the pixels.** Replace each pixel with the index of its colour in the palette. For a 16x16 image this gives 256 index bytes, one per pixel, in row-major order.
3. **LZW encode the indices.** Feed the index stream into an LZW encoder with a starting code size of `min_code_size + 1`. Codes widen by one bit each time the next free dictionary entry needs it, up to 12 bits. Two codes are reserved at the start: a clear code and an end-of-information code.
4. **Pack codes LSB-first.** The output codes are a bit stream, packed with the least significant bit first. This is the opposite of PNG and JPEG, which pack bits MSB-first.
5. **Wrap in sub-blocks.** The packed bit stream is cut into sub-blocks of up to 255 bytes. Each sub-block starts with a one-byte length. A zero-length sub-block marks the end.
6. **Add the trailer.** Write `3B` as the last byte.

A short worked trace from `GifLzwDemo` on the 4-colour index sequence `1 0 1 0 1 0 2 1 0 2 1 0 2`, with `min_code_size = 2`:

| Step | Current | Emit | New table entry | Code size |
|-----:|---------|-----:|-----------------|----------:|
| init | - | 4 | - | 3 (clear code) |
| 1 | 1 | 1 | 1,0 = 6 | 3 |
| 2 | 0 | 0 | 0,1 = 7 | 3 |
| 3 | 1,0 | 6 | 1,0,1 = 8 | 4 |
| 4 | 1,0 | 6 | 1,0,2 = 9 | 4 |
| 5 | 2 | 2 | 2,1 = 10 | 4 |
| 6 | 1,0,2 | 9 | 1,0,2,1 = 11 | 4 |
| flush | 1,0,2 | 9 | - | 4 |
| end | - | 5 | - | 4 (end code) |

Emitted code stream: `4, 1, 0, 6, 6, 2, 9, 9, 5`. Decoding these back gives the original sequence, so the round trip is lossless. The key idea: the dictionary is rebuilt during decoding from the same rule, so it is never stored in the file.

## 5. Strengths, weaknesses, when to use it

Strengths: small, simple, well supported everywhere, supports transparency and animation in one file, LZW on index streams is a good fit for flat graphics with runs of the same colour.

Weaknesses: at most 256 colours per frame, no partial transparency (a pixel is either fully transparent or fully opaque), no built-in colour management, and the global colour table costs real bytes even for small files (768 bytes on a 256-entry table).

Use it for icons, logos, pixel art, line drawings and short animations. Prefer PNG for anything that needs more than 256 colours or alpha blending, and prefer JPEG for photographs.

## 6. Common mix-ups

- "GIF is lossy." LZW itself is lossless. The loss happens when a photograph is quantised to 256 colours before encoding.
- "GIF supports alpha." It supports a single transparent colour index per frame, not a per-pixel alpha channel.
- "The sizes in a GIF are 32-bit." They are 16-bit, which is why the maximum logical screen size is 65535 by 65535.

## 7. How we checked it

- `GifInspector` parses the whole file into labelled segments. `HexDump.checkCoverage` returns `null`, so the segments tile every byte of the file with no gap or overlap.
- `GifDemo` writes T16 via `ImageIO`, then prints the segment table above. The hex and offsets in this write-up come from that run.
- `GifLzwDemo` encodes the short index sequence above and decodes the resulting codes, printing both. The decoded array equals the input array, which checks the LZW idea end to end.

To build and run:

```
javac -d out src/imgformats/*.java
java -cp out imgformats.GifDemo
java -cp out imgformats.GifLzwDemo
```

## 8. References

- Burger and Burge, *Digital Image Processing*, section 1.5 on image file formats and byte order.
- The GIF89a specification, sections on the Logical Screen Descriptor, blocks, and the LZW compression appendix.
