package imgformats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Walks a GIF file byte by byte and returns labelled segments. */
public final class GifInspector implements FormatInspector {

    @Override public String formatName() { return "GIF"; }

    @Override
    public List<Segment> inspect(byte[] f) throws IOException {
        List<Segment> out = new ArrayList<>();
        int p = 0;

        // 1. Signature: 6 bytes "GIF87a" or "GIF89a"
        String sig = new String(f, p, 6);
        out.add(new Segment(p, 6, "Signature", "signature", "Magic bytes " + sig));
        p += 6;

        // 2. Logical Screen Descriptor: 7 bytes
        int w = u16le(f, p);
        int h = u16le(f, p + 2);
        int packed = f[p + 4] & 0xFF;
        boolean gctFlag = (packed & 0x80) != 0;
        int gctSize = packed & 0x07;             // real entries = 2^(gctSize + 1)
        int bgIndex = f[p + 5] & 0xFF;
        int aspect = f[p + 6] & 0xFF;
        out.add(new Segment(p, 7, "LogicalScreenDescriptor", "header",
                "w=" + w + " h=" + h + " packed=0x" + String.format("%02X", packed)
                        + " (gct=" + gctFlag + ", size=" + gctSize + ") bg=" + bgIndex + " aspect=" + aspect));
        p += 7;

        // 3. Global Color Table (optional): 3 * 2^(size+1) bytes
        if (gctFlag) {
            int entries = 1 << (gctSize + 1);
            int bytes = entries * 3;
            out.add(new Segment(p, bytes, "GlobalColorTable", "table",
                    entries + " entries, " + bytes + " bytes (3 bytes per RGB entry)"));
            p += bytes;
        }

        // 4. Blocks loop until trailer 0x3B
        while (p < f.length) {
            int marker = f[p] & 0xFF;
            if (marker == 0x3B) {
                out.add(new Segment(p, 1, "Trailer", "terminator", "End of GIF stream (0x3B)"));
                p += 1;
                break;
            } else if (marker == 0x21) {
                // Extension Introducer
                int label = f[p + 1] & 0xFF;
                String kind;
                switch (label) {
                    case 0xF9 -> kind = "GraphicControl";
                    case 0xFE -> kind = "Comment";
                    case 0x01 -> kind = "PlainText";
                    case 0xFF -> kind = "ApplicationExt";
                    default   -> kind = "Extension_0x" + String.format("%02X", label);
                }
                int extStart = p;
                p += 2; // skip 21 LL
                // sub-blocks: length byte + data, terminated by 00
                while (p < f.length) {
                    int len = f[p] & 0xFF;
                    p += 1;
                    if (len == 0) break;
                    p += len;
                }
                out.add(new Segment(extStart, p - extStart, kind, "metadata",
                        "Extension label 0x" + String.format("%02X", label) + " plus sub-blocks and terminator"));
            } else if (marker == 0x2C) {
                // Image Descriptor
                int idStart = p;
                int il = u16le(f, p + 1);
                int it = u16le(f, p + 3);
                int iw = u16le(f, p + 5);
                int ih = u16le(f, p + 7);
                int ipacked = f[p + 9] & 0xFF;
                boolean lctFlag = (ipacked & 0x80) != 0;
                int lctSize = ipacked & 0x07;
                out.add(new Segment(idStart, 10, "ImageDescriptor", "header",
                        "left=" + il + " top=" + it + " w=" + iw + " h=" + ih
                                + " packed=0x" + String.format("%02X", ipacked)
                                + " (lct=" + lctFlag + ")"));
                p += 10;
                if (lctFlag) {
                    int entries = 1 << (lctSize + 1);
                    int bytes = entries * 3;
                    out.add(new Segment(p, bytes, "LocalColorTable", "table",
                            entries + " entries, " + bytes + " bytes"));
                    p += bytes;
                }
                // LZW minimum code size: 1 byte
                int lzwMin = f[p] & 0xFF;
                out.add(new Segment(p, 1, "LzwMinCodeSize", "header",
                        "Starting code size = " + lzwMin + " bits"));
                p += 1;
                // Image data sub-blocks until 0x00 terminator
                int dataStart = p;
                int subCount = 0;
                int dataBytes = 0;
                while (p < f.length) {
                    int len = f[p] & 0xFF;
                    p += 1;
                    if (len == 0) break;
                    subCount++;
                    dataBytes += len;
                    p += len;
                }
                out.add(new Segment(dataStart, p - dataStart, "ImageDataSubBlocks", "data",
                        subCount + " sub-blocks, " + dataBytes + " compressed bytes, terminated by 0x00"));
            } else {
                throw new IOException("Unknown GIF block marker 0x"
                        + String.format("%02X", marker) + " at offset " + p);
            }
        }

        if (p != f.length) {
            throw new IOException("Trailing bytes after GIF trailer at offset " + p);
        }
        return out;
    }

    private static int u16le(byte[] a, int i) {
        return (a[i] & 0xFF) | ((a[i + 1] & 0xFF) << 8);
    }
}
