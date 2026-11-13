package imgformats;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Pushes one 8x8 block of T16 brightness through the lossy middle of JPEG:
 * level shift, DCT, quantization, then back again. It shows that the DCT alone
 * loses nothing and that the rounding in quantization is where detail goes.
 */
public class JpegDctDemo {

    // Luminance table from the JPEG standard (T.81 Annex K), row by row
    static final int[] BASE = {
        16, 11, 10, 16, 24, 40, 51, 61,   12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,   14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99 };

    // Zigzag order: the k-th value stored in a DQT segment belongs at this row-major index
    static final int[] ZIGZAG = {
        0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
        35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63 };

    public static void main(String[] args) throws IOException {
        BufferedImage img = TestImage.t16Rgb();
        double[][] block = new double[8][8];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                double lum = Math.round(0.299 * r + 0.587 * g + 0.114 * b); // JFIF brightness
                block[y][x] = lum - 128; // level shift so values sit around zero
            }
        }
        print("Top-left 8x8 block of T16 brightness, level shifted (Y - 128)", block);

        double[][] coef = dct(block);
        print("DCT coefficients (our own DCT, rounded for printing)", coef);
        System.out.println("DCT then inverse DCT with no quantization, biggest pixel error: "
                + maxError(block, idct(coef)));

        for (int q : new int[] {75, 25}) {
            int[] table = scaledTable(q);
            double[][] quant = new double[8][8], back = new double[8][8];
            int zeros = 0;
            for (int i = 0; i < 64; i++) {
                double v = Math.round(coef[i / 8][i % 8] / table[i]); // the lossy step
                quant[i / 8][i % 8] = v;
                back[i / 8][i % 8] = v * table[i];
                if (v == 0) zeros++;
            }
            double[][] rec = idct(back);
            System.out.println();
            print("Quality " + q + ": quantized coefficients", quant);
            System.out.println("Zero coefficients: " + zeros + " of 64");
            print("Quality " + q + ": reconstructed block (Y - 128)", rec);
            System.out.println("Biggest pixel error: " + maxError(block, rec));
        }

        double[][] flat = new double[8][8];
        for (double[] row : flat) java.util.Arrays.fill(row, 100 - 128);
        System.out.println();
        print("Flat block (every pixel Y = 100), DCT coefficients", dct(flat));

        checkAgainstFile(scaledTable(75));
    }

    /** Library convention (IJG, also used by ImageIO): quality only scales the table. Not part of T.81. */
    static int[] scaledTable(int quality) {
        int scale = quality < 50 ? 5000 / quality : 200 - 2 * quality;
        int[] t = new int[64];
        for (int i = 0; i < 64; i++) t[i] = Math.max(1, Math.min(255, (BASE[i] * scale + 50) / 100));
        return t;
    }

    /** Compares our quality 75 table with table 0 in the ImageIO sample, which uses its default quality. */
    static void checkAgainstFile(int[] ours) throws IOException {
        File f = new File("samples/jpeg/t16.jpg");
        if (!f.exists()) return;
        byte[] d = Files.readAllBytes(f.toPath());
        int p = 2;
        while (!(d[p + 1] == (byte) 0xDB && d[p + 4] == 0)) p += 2 + JpegInspector.u16(d, p + 2);
        boolean same = true;
        for (int k = 0; k < 64; k++) same &= (d[p + 5 + k] & 0xFF) == ours[ZIGZAG[k]];
        System.out.println();
        System.out.println("Our quality 75 table equals DQT table 0 in " + f.getPath() + ": " + same);
    }

    static double[][] dct(double[][] p) {
        double[][] out = new double[8][8];
        for (int u = 0; u < 8; u++)
            for (int v = 0; v < 8; v++) {
                double sum = 0;
                for (int y = 0; y < 8; y++)
                    for (int x = 0; x < 8; x++)
                        sum += p[y][x] * Math.cos((2 * x + 1) * v * Math.PI / 16) * Math.cos((2 * y + 1) * u * Math.PI / 16);
                out[u][v] = 0.25 * c(u) * c(v) * sum;
            }
        return out;
    }

    static double[][] idct(double[][] f) {
        double[][] out = new double[8][8];
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) {
                double sum = 0;
                for (int u = 0; u < 8; u++)
                    for (int v = 0; v < 8; v++)
                        sum += c(u) * c(v) * f[u][v] * Math.cos((2 * x + 1) * v * Math.PI / 16) * Math.cos((2 * y + 1) * u * Math.PI / 16);
                out[y][x] = Math.round(0.25 * sum); // decoders round back to whole pixel values
            }
        return out;
    }

    static double c(int k) {
        return k == 0 ? 1 / Math.sqrt(2) : 1;
    }

    static long maxError(double[][] a, double[][] b) {
        long worst = 0;
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) worst = Math.max(worst, Math.round(Math.abs(a[y][x] - b[y][x])));
        return worst;
    }

    static void print(String title, double[][] m) {
        System.out.println(title);
        for (double[] row : m) {
            StringBuilder sb = new StringBuilder();
            for (double v : row) sb.append(String.format("%6d", Math.round(v) == 0 ? 0 : Math.round(v)));
            System.out.println(sb);
        }
    }
}
