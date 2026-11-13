package imgformats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks a JPEG file marker by marker. Every marker is FF followed by a code byte.
 * Most markers are followed by a 2-byte big-endian length that counts itself
 * but not the marker. After SOS comes the compressed data, which has no length,
 * so we scan it for the next real marker.
 */
public class JpegInspector implements FormatInspector {

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
            if (u8(f, pos) != 0xFF || pos + 1 >= f.length) {
                throw new IOException("Expected a marker at offset " + pos);
            }
            int code = u8(f, pos + 1);
            String name = markerName(code);

            // These markers stand alone, with no length field after them
            if (code == 0xD8 || code == 0xD9 || (code >= 0xD0 && code <= 0xD7) || code == 0x01) {
                String cat = code == 0xD8 ? "signature" : code == 0xD9 ? "terminator" : "other";
                String desc = code == 0xD8 ? "Start of image" : code == 0xD9 ? "End of image" : "Marker with no length";
                segs.add(new Segment(pos, 2, name, cat, desc));
                pos += 2;
                if (code == 0xD9) break;
                continue;
            }

            int len = u16(f, pos + 2);
            int total = 2 + len;
            if (pos + total > f.length) {
                throw new IOException(name + " at offset " + pos + " runs past the end of the file");
            }
            segs.add(new Segment(pos, total, name, category(code), describe(f, pos, code, len)));
            pos += total;

            if (code == 0xDA) {
                int end = endOfScan(f, pos);
                segs.add(new Segment(pos, end - pos, "compressed data", "data",
                        "Huffman coded blocks, FF bytes stuffed as FF 00"));
                pos = end;
            }
        }
        // Anything after EOI is not part of the image, but we still label it
        if (pos < f.length) {
            segs.add(new Segment(pos, f.length - pos, "trailing bytes", "other", "Data after EOI"));
        }
        return segs;
    }

    /**
     * Finds where the compressed data stops. Inside it, FF 00 means a real FF data byte
     * (byte stuffing) and FF D0 to FF D7 are restart markers that belong to the data.
     * Any other FF xx is the next real marker.
     */
    static int endOfScan(byte[] f, int start) {
        int i = start;
        while (i + 1 < f.length) {
            if (u8(f, i) == 0xFF) {
                int next = u8(f, i + 1);
                boolean partOfData = next == 0x00 || (next >= 0xD0 && next <= 0xD7);
                if (!partOfData) return i;
                i += 2;
            } else {
                i++;
            }
        }
        return f.length;
    }

    private static String describe(byte[] f, int pos, int code, int len) {
        int d = pos + 4; // first byte after the length field
        if (code == 0xE0 && len >= 7 && new String(f, d, 4).equals("JFIF")) {
            return String.format("JFIF %d.%02d, length %d", u8(f, d + 5), u8(f, d + 6), len);
        }
        if (code == 0xE1 && len >= 8 && new String(f, d, 4).equals("Exif")) {
            return "Exif data with a TIFF header, length " + len;
        }
        if (code == 0xDB) {
            // Each table is 1 byte (precision and id) plus 64 or 128 values
            StringBuilder ids = new StringBuilder();
            int p = d;
            while (p < pos + 2 + len) {
                int pq = u8(f, p) >> 4;
                ids.append(ids.length() == 0 ? "" : ",").append(u8(f, p) & 0x0F);
                p += 1 + 64 * (pq + 1);
            }
            return "Quantization table id " + ids + ", length " + len;
        }
        if (code == 0xC4) {
            StringBuilder ids = new StringBuilder();
            int p = d;
            while (p < pos + 2 + len) {
                int tc = u8(f, p) >> 4, th = u8(f, p) & 0x0F;
                int count = 0;
                for (int k = 1; k <= 16; k++) count += u8(f, p + k);
                ids.append(ids.length() == 0 ? "" : " ").append(tc == 0 ? "DC" : "AC").append(th);
                p += 17 + count;
            }
            return "Huffman table " + ids + ", length " + len;
        }
        if (code >= 0xC0 && code <= 0xCF && code != 0xC4 && code != 0xC8 && code != 0xCC) {
            return String.format("%dx%d, %d components, length %d", u16(f, d + 3), u16(f, d + 1), u8(f, d + 5), len);
        }
        if (code == 0xDA) {
            return u8(f, d) + " components in this scan, length " + len;
        }
        return "Length " + len;
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
            case 0xDD: return "DRI";
            case 0xFE: return "COM";
            default:
                if (code >= 0xC0 && code <= 0xCF) return "SOF" + (code - 0xC0);
                return String.format("FF%02X", code);
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
