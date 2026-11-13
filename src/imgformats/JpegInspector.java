package imgformats;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Walks a JPEG file marker by marker. Every marker is FF followed by a code byte.
 * Most markers are followed by a 2-byte big-endian length that counts itself
 * but not the marker. After SOS comes the compressed data, which has no length,
 * so we scan it for the next real marker.
 * Running main writes the test image as a JPEG and prints its layout,
 * its image properties and its quantization tables.
 */
public class JpegInspector implements FormatInspector {

    public static void main(String[] args) throws IOException {
        File out = new File("samples/jpeg/t16.jpg");
        out.getParentFile().mkdirs();
        // JPEG has no alpha channel, so the writer needs the RGB version
        ImageIO.write(TestImage.t16Rgb(), "jpg", out);
        byte[] f = Files.readAllBytes(out.toPath());
        List<Segment> segs = new JpegInspector().inspect(f);

        System.out.println("File: " + out.getPath() + ", " + f.length + " bytes\n");
        HexDump.printSegments(f, segs);
        // Every byte must belong to exactly one segment, so offsets and lengths add up
        String gap = HexDump.checkCoverage(f, segs);
        if (gap != null) System.out.println("\nWarning: " + gap);
        for (Segment s : segs) {
            int d = (int) s.offset() + 4; // first byte after marker and length
            if (s.name().equals("SOF0")) printProperties(f, d);
        }
        for (Segment s : segs) {
            if (s.name().equals("DQT")) printTable(f, (int) s.offset() + 4);
        }
    }

    /** SOF0 after the length: precision, height, width, component count, then 3 bytes per component. */
    static void printProperties(byte[] f, int d) {
        int n = u8(f, d + 5);
        System.out.println("\nImage properties (from SOF0)");
        System.out.printf("  precision %d bits, height %d, width %d, %d components%n", u8(f, d), u16(f, d + 1), u16(f, d + 3), n);
        int[] h = new int[n], v = new int[n];
        for (int i = 0; i < n; i++) {
            int p = d + 6 + 3 * i, hv = u8(f, p + 1);
            // Sampling factors share one byte: high 4 bits horizontal, low 4 bits vertical
            h[i] = hv >> 4;
            v[i] = hv & 0x0F;
            System.out.printf("  component %d: sampling byte %02X -> H=%d V=%d, quant table %d%n", u8(f, p), hv, h[i], v[i], u8(f, p + 2));
        }
        if (n == 3) {
            int dh = h[0] / h[1], dv = v[0] / v[1];
            String name = dh == 1 && dv == 1 ? "4:4:4" : dh == 2 && dv == 1 ? "4:2:2" : dh == 2 && dv == 2 ? "4:2:0" : "uncommon";
            System.out.printf("  colour kept at 1/%d width and 1/%d height of brightness, which is %s%n", dh, dv, name);
        }
    }

    /** Prints one 8-bit DQT table as an 8x8 grid. The file stores the 64 values in zigzag order. */
    static void printTable(byte[] f, int d) {
        int[] grid = new int[64];
        for (int k = 0; k < 64; k++) grid[JpegDctDemo.ZIGZAG[k]] = u8(f, d + 1 + k);
        System.out.println("\nQuantization table " + (u8(f, d) & 0x0F) + " (8x8, top-left divides the average)");
        for (int r = 0; r < 8; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < 8; c++) sb.append(String.format("%4d", grid[r * 8 + c]));
            System.out.println(sb);
        }
    }

    @Override
    public String formatName() {
        return "JPEG";
    }

    @Override
    public List<Segment> inspect(byte[] f) throws IOException {
        List<Segment> segs = new ArrayList<>();
        if (f.length < 4 || u8(f, 0) != 0xFF || u8(f, 1) != 0xD8) {
            throw new IOException("Not a JPEG file, it does not start with FF D8");
        }
        int pos = 0;
        while (pos < f.length) {
            if (u8(f, pos) != 0xFF || pos + 1 >= f.length) throw new IOException("Expected a marker at offset " + pos);
            int code = u8(f, pos + 1);
            String name = markerName(code);

            // SOI, EOI and restart markers stand alone, with no length field
            if (code == 0xD8 || code == 0xD9 || (code >= 0xD0 && code <= 0xD7)) {
                String cat = code == 0xD8 ? "signature" : code == 0xD9 ? "terminator" : "other";
                segs.add(new Segment(pos, 2, name, cat, describe(code)));
                pos += 2;
                if (code == 0xD9) break;
                continue;
            }

            int len = u16(f, pos + 2);
            if (pos + 2 + len > f.length) throw new IOException(name + " at offset " + pos + " runs past the end of the file");
            String desc = describe(code) + (code == 0xDB ? " id " + (u8(f, pos + 4) & 0x0F) : "") + ", length " + len;
            segs.add(new Segment(pos, 2 + len, name, category(code), desc));
            pos += 2 + len;

            if (code == 0xDA) {
                int end = endOfScan(f, pos);
                segs.add(new Segment(pos, end - pos, "compressed data", "data", "Compressed image data"));
                pos = end;
            }
        }
        // Anything after EOI is not part of the image, but we still label it
        if (pos < f.length) segs.add(new Segment(pos, f.length - pos, "trailing bytes", "other", "Data after EOI"));
        return segs;
    }

    /**
     * Finds where the compressed data stops. Inside it, FF 00 means a real FF data byte
     * (byte stuffing) and FF D0 to FF D7 are restart markers that belong to the data.
     * Any other FF xx is the next real marker.
     */
    static int endOfScan(byte[] f, int i) {
        while (i + 1 < f.length) {
            if (u8(f, i) != 0xFF) { i++; continue; }
            int next = u8(f, i + 1);
            if (next != 0x00 && (next < 0xD0 || next > 0xD7)) return i;
            i += 2;
        }
        return f.length;
    }

    private static String describe(int code) {
        if (code == 0xE0) return "JFIF header";
        if (code >= 0xE1 && code <= 0xEF) return "Application data";
        if (code >= 0xD0 && code <= 0xD7) return "Restart marker";
        switch (code) {
            case 0xD8: return "Start of image";
            case 0xD9: return "End of image";
            case 0xDB: return "Quantization table";
            case 0xC0: return "Frame header: size, precision, components";
            case 0xC4: return "Huffman table";
            case 0xDA: return "Start of scan";
            default: return "Other segment";
        }
    }

    private static String category(int code) {
        if (code >= 0xE0 && code <= 0xEF || code == 0xFE) return "metadata";
        if (code == 0xDB || code == 0xC4) return "table";
        if (code >= 0xC0 && code <= 0xCF || code == 0xDA) return "header";
        return "other";
    }

    static String markerName(int code) {
        if (code >= 0xE0 && code <= 0xEF) return "APP" + (code - 0xE0);
        if (code >= 0xD0 && code <= 0xD7) return "RST" + (code - 0xD0);
        switch (code) {
            case 0xD8: return "SOI";
            case 0xD9: return "EOI";
            case 0xDB: return "DQT";
            case 0xC4: return "DHT";
            case 0xDA: return "SOS";
            default: return code >= 0xC0 && code <= 0xCF ? "SOF" + (code - 0xC0) : String.format("FF%02X", code);
        }
    }

    static int u8(byte[] f, int i) {
        return f[i] & 0xFF;
    }

    /** Big-endian 16-bit value, as used by every JPEG length and size field. */
    static int u16(byte[] f, int i) {
        return (u8(f, i) << 8) | u8(f, i + 1);
    }
}
