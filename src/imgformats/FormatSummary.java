package imgformats;

import java.util.List;
import java.util.Locale;

/**
 * Prints the same set of facts for a PNG, JPEG or GIF file, with the same labels,
 * so the three formats can be compared line by line. Every value is read from the file bytes.
 */
public final class FormatSummary {
    private FormatSummary() {}

    private static final String[] ROLES = {"signature", "header", "metadata", "table", "data", "checksum", "terminator", "other"};

    public static void png(String path, byte[] f, List<Segment> segs) {
        int w = u32be(f, (int) find(segs, "IHDR width").offset());
        int h = u32be(f, (int) find(segs, "IHDR height").offset());
        int p = (int) find(segs, "IHDR height").offset() + 4; // bit depth and colour type follow the height
        int bits = f[p] & 0xFF, type = f[p + 1] & 0xFF;
        String model = switch (type) {
            case 0 -> "greyscale";
            case 2 -> "RGB";
            case 3 -> "palette";
            case 4 -> "greyscale with alpha";
            case 6 -> "RGBA";
            default -> "colour type " + type;
        };
        print("PNG", path, f, segs, 8, w, h, bits + " bits per sample, " + model,
                "big-endian",
                "chunks: length, type, data, CRC",
                "row filters, then DEFLATE (lossless)",
                "CRC32 on every chunk");
    }

    public static void jpeg(String path, byte[] f, List<Segment> segs) {
        int d = (int) find(segs, "SOF0").offset() + 4; // skip the marker and the length
        int bits = f[d] & 0xFF, h = u16be(f, d + 1), w = u16be(f, d + 3), n = f[d + 5] & 0xFF;
        String comps = n + " components" + (n == 3 ? " (Y, Cb, Cr)" : n == 1 ? " (Y)" : "");
        print("JPEG", path, f, segs, 3, w, h, bits + " bits per sample, " + comps,
                "big-endian (lengths and sizes)",
                "marker segments: FF code, length, data",
                "DCT, quantization, Huffman coding (lossy)",
                "none");
    }

    public static void gif(String path, byte[] f, List<Segment> segs) {
        int w = u16le(f, 6), h = u16le(f, 8);
        int colours = find(segs, "GlobalColorTable").length() / 3;
        int bits = (f[10] & 0x07) + 1; // low 3 bits of the packed byte give the table size as 2^(n+1)
        print("GIF", path, f, segs, 6, w, h, "palette of " + colours + " colours, " + bits + " bits per index",
                "little-endian (width and height)",
                "blocks: introducer byte, data, sub-blocks",
                "LZW on palette indices (lossless)",
                "none");
    }

    private static void print(String format, String path, byte[] f, List<Segment> segs, int magicLen,
                              int w, int h, String image, String order, String built, String comp, String integrity) {
        int raw = w * h * 3; // same raw RGB size for all three formats so the ratios compare fairly
        String gap = HexDump.checkCoverage(f, segs);
        System.out.println();
        System.out.println("Summary");
        row("Format", format);
        row("File", path);
        row("Magic bytes", HexDump.hex(f, 0, magicLen));
        row("File size", String.format(Locale.ROOT, "%d bytes (raw RGB %d, ratio %.2f)", f.length, raw, (double) f.length / raw));
        row("Image", w + " x " + h + ", " + image);
        row("Byte order", order);
        row("Built from", built);
        row("Compression", comp);
        row("Integrity", integrity);
        row("Coverage", gap == null ? "OK, every byte belongs to exactly one segment" : gap);
        System.out.println("  Bytes by role");
        for (String role : ROLES) {
            long sum = 0;
            for (Segment s : segs) if (s.category().equals(role)) sum += s.length();
            if (sum > 0) {
                System.out.printf(Locale.ROOT, "    %-12s %6d %-5s %6.1f%%%n", role, sum, sum == 1 ? "byte" : "bytes", 100.0 * sum / f.length);
            }
        }
    }

    private static void row(String label, String value) {
        System.out.printf("  %-14s%s%n", label, value);
    }

    private static Segment find(List<Segment> segs, String name) {
        for (Segment s : segs) if (s.name().equals(name)) return s;
        throw new IllegalArgumentException("No segment named " + name);
    }

    private static int u16be(byte[] f, int i) { return ((f[i] & 0xFF) << 8) | (f[i + 1] & 0xFF); }

    private static int u16le(byte[] f, int i) { return (f[i] & 0xFF) | ((f[i + 1] & 0xFF) << 8); }

    private static int u32be(byte[] f, int i) { return (u16be(f, i) << 16) | u16be(f, i + 2); }
}
