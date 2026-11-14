# Comparison of PNG, JPEG and GIF

All three sample files were made from the same 16 x 16 test image (T16), so their sizes can be compared. Raw RGB for T16 is 768 bytes, as shown in `01-foundations.md`. The sizes below come from the files in `samples/` and were printed by `ComparisonDemo`.

## Side by side

| | PNG | JPEG | GIF |
|---|---|---|---|
| Main use | Screenshots, logos, exact pixels | Photographs | Icons, flat graphics, short animations |
| Compression | Row filters, then DEFLATE | DCT, quantization, Huffman coding | LZW on palette indices |
| Lossless or lossy | Lossless | Lossy | LZW is lossless, but the 256 colour limit can lose colour |
| Colour depth | 8 or 16 bits per sample, palette optional | 8 bits per sample, 3 components | Palette of up to 256 colours per frame |
| Transparency | Alpha channel in colour types 4 and 6 | None | One transparent colour, on or off |
| Animation | Not in the basic format | None | Yes, several frames in one file |
| Byte order | Big-endian | Big-endian lengths | Little-endian sizes |
| Magic bytes | `89 50 4E 47 0D 0A 1A 0A` | `FF D8 FF` | `47 49 46 38 39 61` (GIF89a) |
| T16 file | `samples/png/t16.png` | `samples/jpeg/t16.jpg` | `samples/gif/t16.gif` |
| Size of T16 | 352 bytes | 659 bytes | 1,087 bytes |
| Size compared with raw 768 bytes | 0.46 | 0.86 | 1.42 |

The PNG file stores an alpha channel, so it holds 1,024 bytes of raw data. The JPEG and GIF files were made from the RGB version of T16, which is 768 bytes. All three ratios in the last row use 768 bytes.

## Why the sizes came out this way

Only the PNG came out smaller than raw RGB. The tables printed by the three demos show where the other bytes go.

PNG, 352 bytes. The signature takes 8 bytes, the IHDR chunk 25, the IDAT chunk 307 and the IEND chunk 12. The IDAT data is 295 bytes and it inflates to 1,040 bytes, which is 16 rows of 65 bytes. Every row in our file uses filter type 0, so the saving comes from DEFLATE alone. In T16 the blue value is always 128 and alpha is always 255, and each row repeats the same pattern with only the green value changing.

JPEG, 659 bytes. Only 34 bytes are compressed image data. The other 625 bytes are markers, headers and tables. The two quantization tables take 69 bytes each and the four Huffman tables take 33, 183, 33 and 183 bytes. Every JPEG file carries its tables, so a tiny image pays for them in full.

GIF, 1,087 bytes. The global colour table has 256 entries of 3 bytes, which is 768 bytes and equals the raw RGB size on its own. The image data sub-blocks take 294 bytes. The header, screen descriptor, image descriptor, code size byte and trailer take the remaining 25 bytes.

T16 is a very small image, so these numbers do not show how the formats behave on a photograph. We did not measure a photograph. The format write-ups say that PNG is usually much bigger than JPEG for photos and that GIF needs a photo cut down to 256 colours first.

## When to use which

- PNG for screenshots, logos, diagrams and anything that needs sharp edges, exact pixels or smooth transparency.
- JPEG for photographs when a small file matters more than exact pixels. Saving a JPEG again loses more detail.
- GIF for icons, pixel art and short animations. Use PNG instead when an image needs more than 256 colours or partial transparency.

## How we checked it

`ComparisonDemo` reads every image under `samples/`, prints its size and its first 8 bytes, and names the format from those bytes alone, ignoring the file extension. Run it after compiling:

```
javac -d out src/imgformats/*.java
java -cp out imgformats.ComparisonDemo
```

The breakdown of each file comes from the tables printed by `PngDemo`, `JpegInspector` and `GifDemo`. The same numbers appear in `02-png.md`, `03-jpeg.md` and `04-gif.md`.

## References

See `references.md`.
