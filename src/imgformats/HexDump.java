package imgformats;

import java.util.List;

public final class HexDump {
    private HexDump() {}

    public static String hex(byte[] data, int from, int len) {
        StringBuilder sb = new StringBuilder();
        int end = Math.min(from + len, data.length);
        for (int i = from; i < end; i++) {
            sb.append(String.format("%02X", data[i] & 0xFF));
            if (i < end - 1) sb.append(' ');
        }
        return sb.toString();
    }

    /** Prints one row per segment: offset, length, name, first 16 bytes in hex, meaning. */
    public static void printSegments(byte[] data, List<Segment> segs) {
        System.out.printf("%-8s %-7s %-22s %-50s %s%n", "OFFSET", "LEN", "NAME", "BYTES (first 16)", "MEANING");
        for (Segment s : segs) {
            int shown = Math.min(s.length(), 16);
            String bytes = hex(data, (int) s.offset(), shown) + (s.length() > shown ? " ..." : "");
            System.out.printf("%08X %-7d %-22s %-50s %s%n", s.offset(), s.length(), s.name(), bytes, s.description());
        }
    }

    /** Returns null if the segments tile the whole file, otherwise a message about the first problem. */
    public static String checkCoverage(byte[] data, List<Segment> segs) {
        long pos = 0;
        for (Segment s : segs) {
            if (s.offset() != pos) {
                return "Gap or overlap at offset " + pos + " (next segment starts at " + s.offset() + ")";
            }
            pos = s.end();
        }
        return pos == data.length ? null : "Segments end at " + pos + " but the file has " + data.length + " bytes";
    }
}
