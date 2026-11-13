package imgformats;

import java.awt.image.BufferedImage;

/** Applies the five PNG row filters to a short row, so the write-up examples come from real output. */
public class PngFilterDemo {

    private static final String[] NAMES = {"None", "Sub", "Up", "Average", "Paeth"};

    public static void main(String[] args) {
        // Example 1: one grayscale row (1 byte per pixel) and the row above it
        int[] above = {98, 100, 103, 107, 112, 118};
        int[] row = {100, 102, 105, 109, 114, 120};
        System.out.println("Example 1: grayscale, 1 byte per pixel, values in decimal");
        show("Row above", above, false);
        show("Current row", row, false);
        for (int type = 0; type < 5; type++) {
            show(type + " " + NAMES[type], filter(type, row, above, 1), false);
        }

        // Example 2: first 4 pixels of row 1 of T16 (RGBA, 4 bytes per pixel), row 0 is above
        BufferedImage img = TestImage.t16();
        int[] top = pixelBytes(img, 0, 4);
        int[] next = pixelBytes(img, 1, 4);
        System.out.println();
        System.out.println("Example 2: first 4 pixels of T16 row 1, RGBA, 4 bytes per pixel, values in hex");
        show("Row above", top, true);
        show("Current row", next, true);
        for (int type = 0; type < 5; type++) {
            show(type + " " + NAMES[type], filter(type, next, top, 4), true);
        }
    }

    // bpp is bytes per pixel. A byte left of the row start counts as 0, as does the row above the first row.
    static int[] filter(int type, int[] cur, int[] above, int bpp) {
        int[] out = new int[cur.length];
        for (int i = 0; i < cur.length; i++) {
            int a = i >= bpp ? cur[i - bpp] : 0;
            int b = above[i];
            int c = i >= bpp ? above[i - bpp] : 0;
            int predicted = switch (type) {
                case 0 -> 0;
                case 1 -> a;
                case 2 -> b;
                case 3 -> (a + b) / 2;
                default -> paeth(a, b, c);
            };
            out[i] = (cur[i] - predicted) & 0xFF;
        }
        return out;
    }

    // Picks whichever of left, above and upper left is closest to a + b - c
    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) {
            return a;
        }
        return pb <= pc ? b : c;
    }

    // Reads the first n pixels of one row as R, G, B, A bytes
    private static int[] pixelBytes(BufferedImage img, int y, int n) {
        int[] bytes = new int[n * 4];
        for (int x = 0; x < n; x++) {
            int argb = img.getRGB(x, y);
            bytes[x * 4] = (argb >> 16) & 0xFF;
            bytes[x * 4 + 1] = (argb >> 8) & 0xFF;
            bytes[x * 4 + 2] = argb & 0xFF;
            bytes[x * 4 + 3] = (argb >>> 24) & 0xFF;
        }
        return bytes;
    }

    private static void show(String label, int[] values, boolean hex) {
        StringBuilder sb = new StringBuilder();
        for (int v : values) {
            sb.append(hex ? String.format("%02X", v) : String.valueOf(v)).append(' ');
        }
        System.out.printf("%-12s %s%n", label, sb.toString().trim());
    }
}
