package imgformats;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Writes the 16x16 test image as a JPEG with ImageIO, walks its markers,
 * and reads the JFIF version and the chroma subsampling back out of the file.
 * Run it from the repo root so the sample lands in samples/jpeg/.
 */
public class JpegDemo {

    public static void main(String[] args) throws IOException {
        File out = new File("samples/jpeg/t16.jpg");
        out.getParentFile().mkdirs();
        // JPEG has no alpha channel, so the writer needs the RGB version
        if (!ImageIO.write(TestImage.t16Rgb(), "jpg", out)) {
            throw new IOException("No JPEG writer found");
        }

        byte[] f = Files.readAllBytes(out.toPath());
        List<Segment> segs = new JpegInspector().inspect(f);

        System.out.println("File: " + out.getPath() + ", " + f.length + " bytes");
        System.out.println();
        HexDump.printSegments(f, segs);
        System.out.println();
        System.out.println("Coverage check: " + HexDump.checkCoverage(f, segs));
        System.out.println();

        for (Segment s : segs) {
            int d = (int) s.offset() + 4; // first byte after marker and length
            if (s.name().equals("APP0")) {
                System.out.printf("APP0 identifier: \"%s\", JFIF version %d.%02d%n",
                        new String(f, d, 4), f[d + 5] & 0xFF, f[d + 6] & 0xFF);
            }
            if (s.name().startsWith("SOF")) {
                printSampling(f, d);
            }
        }
    }

    /** SOF layout after the length: precision, height, width, component count, then 3 bytes per component. */
    private static void printSampling(byte[] f, int d) {
        int n = f[d + 5] & 0xFF;
        int hMax = 1, vMax = 1;
        int[] h = new int[n], v = new int[n];
        System.out.printf("SOF: precision %d bits, height %d, width %d, %d components%n",
                f[d] & 0xFF, JpegInspector.u16(f, d + 1), JpegInspector.u16(f, d + 3), n);
        for (int i = 0; i < n; i++) {
            int p = d + 6 + 3 * i;
            int id = f[p] & 0xFF, hv = f[p + 1] & 0xFF, tq = f[p + 2] & 0xFF;
            // Sampling factors share one byte: high 4 bits horizontal, low 4 bits vertical
            h[i] = hv >> 4;
            v[i] = hv & 0x0F;
            hMax = Math.max(hMax, h[i]);
            vMax = Math.max(vMax, v[i]);
            System.out.printf("  component %d: sampling byte %02X -> H=%d V=%d, quant table %d%n",
                    id, hv, h[i], v[i], tq);
        }
        System.out.printf("MCU (minimum coded unit) = %dx%d pixels%n", 8 * hMax, 8 * vMax);
        if (n == 3) {
            System.out.printf("Chroma kept at 1/%d width and 1/%d height of luma, which is %s%n",
                    hMax / h[1], vMax / v[1], name(hMax / h[1], vMax / v[1]));
        }
    }

    private static String name(int dh, int dv) {
        if (dh == 1 && dv == 1) return "4:4:4 (no subsampling)";
        if (dh == 2 && dv == 1) return "4:2:2";
        if (dh == 2 && dv == 2) return "4:2:0";
        return "an uncommon layout";
    }
}
