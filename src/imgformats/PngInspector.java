package imgformats;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/** Splits a PNG file into segments: signature, then length, type, data and CRC of every chunk. */
public class PngInspector implements FormatInspector {

    private static final byte[] SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Override
    public String formatName() {
        return "PNG";
    }

    @Override
    public List<Segment> inspect(byte[] file) throws IOException {
        if (file.length < SIGNATURE.length) {
            throw new IOException("File is shorter than the 8 byte signature");
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (file[i] != SIGNATURE[i]) {
                throw new IOException("Signature does not match, this is not a PNG file");
            }
        }

        List<Segment> segs = new ArrayList<>();
        segs.add(new Segment(0, 8, "Signature", "signature",
                "marks the file as PNG and catches common transfer damage"));

        // ByteBuffer reads big endian by default, which is the byte order PNG uses
        ByteBuffer buf = ByteBuffer.wrap(file);
        int pos = SIGNATURE.length;
        boolean firstIdat = true;

        while (pos < file.length) {
            if (pos + 12 > file.length) {
                throw new IOException("Chunk at offset " + pos + " is cut off");
            }
            int len = buf.getInt(pos);
            if (len < 0 || len > file.length - pos - 12) {
                throw new IOException("Chunk at offset " + pos + " claims more data than the file holds");
            }
            String type = new String(file, pos + 4, 4, StandardCharsets.US_ASCII);
            int dataPos = pos + 8;
            int crcPos = dataPos + len;

            // the CRC covers the type and the data, not the length
            CRC32 crc = new CRC32();
            crc.update(file, pos + 4, len + 4);
            long calculated = crc.getValue();
            long stored = buf.getInt(crcPos) & 0xFFFFFFFFL;

            segs.add(new Segment(pos, 4, type + " length", "other",
                    "number of data bytes, " + len));
            boolean critical = Character.isUpperCase(type.charAt(0));
            segs.add(new Segment(pos + 4, 4, type + " type",
                    type.equals("IEND") ? "terminator" : "other",
                    critical ? "chunk name, uppercase first letter so it is critical"
                             : "chunk name, lowercase first letter so it is optional"));

            if (type.equals("IHDR") && len == 13) {
                addHeaderFields(segs, file, buf, dataPos);
            } else if (len > 0) {
                String category = "metadata";
                String text = "chunk data";
                if (type.equals("PLTE")) {
                    category = "table";
                    text = "palette, " + (len / 3) + " colours of 3 bytes each";
                } else if (type.equals("IDAT")) {
                    category = "data";
                    text = "compressed pixel rows";
                    if (firstIdat && len >= 2) {
                        text += ", zlib stream starts with " + HexDump.hex(file, dataPos, 2);
                    }
                    firstIdat = false;
                }
                segs.add(new Segment(dataPos, len, type + " data", category, text));
            }

            String match = stored == calculated
                    ? "matches our own CRC32 calculation"
                    : "does NOT match, we calculated " + String.format("%08X", calculated);
            segs.add(new Segment(crcPos, 4, type + " CRC", "checksum",
                    "stored value " + HexDump.hex(file, crcPos, 4) + " " + match));

            pos = crcPos + 4;
            if (type.equals("IEND")) {
                break;
            }
        }

        if (pos < file.length) {
            segs.add(new Segment(pos, file.length - pos, "Trailing bytes", "other",
                    "extra bytes after IEND"));
        }
        return segs;
    }

    // IHDR has 13 data bytes, one segment per field
    private static void addHeaderFields(List<Segment> segs, byte[] file, ByteBuffer buf, int p) {
        int colour = file[p + 9] & 0xFF;
        int interlace = file[p + 12] & 0xFF;
        segs.add(new Segment(p, 4, "IHDR width", "header", "width " + buf.getInt(p) + " pixels"));
        segs.add(new Segment(p + 4, 4, "IHDR height", "header", "height " + buf.getInt(p + 4) + " pixels"));
        segs.add(new Segment(p + 8, 1, "IHDR bit depth", "header",
                (file[p + 8] & 0xFF) + " bits per sample"));
        segs.add(new Segment(p + 9, 1, "IHDR colour type", "header",
                colour + " means " + colourName(colour)));
        segs.add(new Segment(p + 10, 1, "IHDR compression", "header",
                (file[p + 10] & 0xFF) + " means zlib with DEFLATE"));
        segs.add(new Segment(p + 11, 1, "IHDR filter method", "header",
                (file[p + 11] & 0xFF) + " means the five standard row filters"));
        segs.add(new Segment(p + 12, 1, "IHDR interlace", "header",
                interlace + (interlace == 0 ? " means no interlacing" : " means Adam7 interlacing")));
    }

    private static String colourName(int type) {
        return switch (type) {
            case 0 -> "grayscale";
            case 2 -> "RGB";
            case 3 -> "palette";
            case 4 -> "grayscale with alpha";
            case 6 -> "RGB with alpha";
            default -> "unknown";
        };
    }
}
