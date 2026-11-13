package imgformats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small LZW encoder plus decoder for a short index sequence.
 * Prints a step table so the trace can go on a slide.
 * This is the LZW variant GIF uses: variable-width codes, LSB-first bit packing,
 * a clear code and an end code reserved up front.
 */
public final class GifLzwDemo {

    public static void main(String[] args) {
        // Small input over an alphabet of 4 colours (indices 0..3).
        // Patterns repeat so the dictionary actually helps.
        int[] indices = {1, 0, 1, 0, 1, 0, 2, 1, 0, 2, 1, 0, 2};
        int minCodeSize = 2; // 4-colour alphabet

        System.out.println("Input indices: " + arr(indices));
        System.out.println("Minimum code size: " + minCodeSize + " bits");
        System.out.println();

        List<Integer> codes = encode(indices, minCodeSize);

        System.out.println();
        System.out.println("Emitted codes: " + codes);

        int[] round = decode(codes, minCodeSize);
        System.out.println("Decoded back:  " + arr(round));
        System.out.println("Round trip OK: " + sameArray(indices, round));
    }

    // -------- encoder --------
    static List<Integer> encode(int[] indices, int minCodeSize) {
        int clear = 1 << minCodeSize;        // e.g. 4
        int end   = clear + 1;               // e.g. 5
        int codeSize = minCodeSize + 1;      // start with one extra bit

        Map<String, Integer> dict = new HashMap<>();
        resetDict(dict, minCodeSize);
        int nextCode = end + 1;

        List<Integer> out = new ArrayList<>();
        out.add(clear);
        System.out.printf("%-8s %-20s %-8s %-20s %s%n",
                "STEP", "CURRENT", "EMIT", "NEW TABLE ENTRY", "CODE SIZE");
        System.out.printf("%-8s %-20s %-8d %-20s %d (clear code)%n",
                "init", "-", clear, "-", codeSize);

        StringBuilder w = new StringBuilder();
        int step = 1;
        for (int k = 0; k < indices.length; k++) {
            String kStr = String.valueOf(indices[k]);
            String wk = w.length() == 0 ? kStr : w + "," + kStr;
            if (dict.containsKey(wk)) {
                w.setLength(0);
                w.append(wk);
            } else {
                int emit = dict.get(w.toString());
                dict.put(wk, nextCode);
                out.add(emit);
                System.out.printf("%-8d %-20s %-8d %-20s %d%n",
                        step++, w.toString(), emit, wk + " = " + nextCode, codeSize);
                nextCode++;
                // widen code size when the next free code needs another bit
                if (nextCode == (1 << codeSize) && codeSize < 12) {
                    codeSize++;
                }
                w.setLength(0);
                w.append(kStr);
            }
        }
        if (w.length() > 0) {
            int emit = dict.get(w.toString());
            out.add(emit);
            System.out.printf("%-8s %-20s %-8d %-20s %d%n",
                    "flush", w.toString(), emit, "-", codeSize);
        }
        out.add(end);
        System.out.printf("%-8s %-20s %-8d %-20s %d (end code)%n",
                "end", "-", end, "-", codeSize);
        return out;
    }

    private static void resetDict(Map<String, Integer> dict, int minCodeSize) {
        dict.clear();
        int alphabet = 1 << minCodeSize;
        for (int i = 0; i < alphabet; i++) dict.put(String.valueOf(i), i);
    }

    // -------- decoder (proves the round trip) --------
    static int[] decode(List<Integer> codes, int minCodeSize) {
        int clear = 1 << minCodeSize;
        int end   = clear + 1;

        List<int[]> table = new ArrayList<>();
        resetTable(table, minCodeSize);
        List<Integer> out = new ArrayList<>();
        Integer prev = null;

        for (int c : codes) {
            if (c == clear) {
                resetTable(table, minCodeSize);
                prev = null;
                continue;
            }
            if (c == end) break;
            int[] entry;
            if (c < table.size()) entry = table.get(c);
            else {
                int[] p = table.get(prev);
                entry = new int[p.length + 1];
                System.arraycopy(p, 0, entry, 0, p.length);
                entry[p.length] = p[0];
            }
            for (int v : entry) out.add(v);
            if (prev != null) {
                int[] p = table.get(prev);
                int[] ne = new int[p.length + 1];
                System.arraycopy(p, 0, ne, 0, p.length);
                ne[p.length] = entry[0];
                table.add(ne);
            }
            prev = c;
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    private static void resetTable(List<int[]> table, int minCodeSize) {
        table.clear();
        int alphabet = 1 << minCodeSize;
        for (int i = 0; i < alphabet; i++) table.add(new int[]{i});
        table.add(new int[0]); // clear
        table.add(new int[0]); // end
    }

    private static String arr(int[] a) {
        StringBuilder s = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) { if (i > 0) s.append(", "); s.append(a[i]); }
        return s.append("]").toString();
    }
    private static boolean sameArray(int[] a, int[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }
}
