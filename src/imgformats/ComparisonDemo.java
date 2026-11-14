package imgformats;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Lists every sample image with its size, first bytes and the format its magic bytes name.
 * The extension is never used to decide the format.
 */
public class ComparisonDemo {
    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : "samples");
        if (!Files.isDirectory(root)) {
            System.out.println("No folder named " + root + ". Run this from the repo root.");
            return;
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).filter(ComparisonDemo::isImage).sorted().toList();
        }

        System.out.printf("%-30s %8s  %-23s %-8s %8s %6s%n", "FILE", "BYTES", "FIRST 8 BYTES", "MAGIC", "RAW RGB", "RATIO");
        for (Path p : files) {
            byte[] data = Files.readAllBytes(p);
            String name = root.relativize(p).toString().replace('\\', '/');
            String raw = "-";
            String ratio = "-";
            BufferedImage img = ImageIO.read(p.toFile());
            if (img != null) {
                int rawBytes = img.getWidth() * img.getHeight() * 3;
                raw = String.valueOf(rawBytes);
                ratio = String.format(Locale.ROOT, "%.2f", (double) data.length / rawBytes);
            }
            System.out.printf("%-30s %8d  %-23s %-8s %8s %6s%n",
                    name, data.length, HexDump.hex(data, 0, 8), identify(data), raw, ratio);
        }
    }

    private static boolean isImage(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif");
    }

    /** Decides the format from the first bytes only. */
    static String identify(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "PNG";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "JPEG";
        }
        if (d.length >= 6) {
            String head = new String(d, 0, 6, StandardCharsets.ISO_8859_1);
            if (head.equals("GIF87a") || head.equals("GIF89a")) {
                return head;
            }
        }
        return "unknown";
    }
}
