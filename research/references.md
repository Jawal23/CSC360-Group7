# References

## Books

- Wilhelm Burger and Mark J. Burge, Digital Image Processing: An Algorithmic Introduction Using Java, 2nd ed., Springer, 2016. Section 1.5, Image File Formats (page 14 on PNG, pages 19 to 20 on byte order, Table 1.2 on page 20). The book covers the formats at overview level, so the byte-level details come from the specifications below.

## Specifications

PNG
- RFC 2083, PNG (Portable Network Graphics) Specification, version 1.0. Sections 2.1, 3.1, 3.2, 3.4, 4.1.1 to 4.1.4, 5 and 6.
- RFC 1950, ZLIB Compressed Data Format Specification.
- RFC 1951, DEFLATE Compressed Data Format Specification.

JPEG
- ITU-T Recommendation T.81 (1992). Annex B (markers and segments), Annex A (DCT) and Annex K (example quantization tables).
- E. Hamilton, JPEG File Interchange Format, Version 1.02, 1992.

GIF
- Graphics Interchange Format, Version 89a. The sections on the Logical Screen Descriptor and blocks, and the appendix on LZW compression.

## Java documentation

- javax.imageio.ImageIO, java.util.zip.CRC32 and java.util.zip.Inflater from the JDK API documentation.