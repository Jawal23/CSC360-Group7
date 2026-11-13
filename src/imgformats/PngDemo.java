package imgformats;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Writes the 16x16 test image as a PNG, prints every segment and checks the CRC rule. */
public class PngDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of("samples", "png", "t16.png");
        Files.createDirectories(out.getParent());
        if (!ImageIO.write(TestImage.t16(), "png", out.toFile())) {
            throw new IOException("ImageIO has no PNG writer");
        }
        byte[] file = Files.readAllBytes(out);
        System.out.println("Wrote " + out + ", " + file.length + " bytes");
        System.out.println();

        List<Segment> segs = new PngInspector().inspect(file);
        HexDump.printSegments(file, segs);

        String problem = HexDump.checkCoverage(file, segs);
        System.out.println();
        System.out.println(problem == null
                ? "Coverage: every byte belongs to exactly one segment"
                : "Coverage problem: " + problem);

        System.out.println();
        printChunkChecks(file);
    }

    // For each chunk: size on disk and which byte range the stored CRC really covers
    private static void printChunkChecks(byte[] file) {
        ByteBuffer buf = ByteBuffer.wrap(file);
        StringBuilder order = new StringBuilder();
        System.out.printf("%-6s %-8s %-8s %-9s %-10s %-16s %-20s%n",
                "Chunk", "Offset", "Data", "On disk", "Stored", "Type+data CRC", "Length+type+data CRC");

        int pos = 8;
        while (pos + 12 <= file.length) {
            int len = buf.getInt(pos);
            String type = new String(file, pos + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long stored = buf.getInt(pos + 8 + len) & 0xFFFFFFFFL;

            CRC32 withoutLength = new CRC32();
            withoutLength.update(file, pos + 4, len + 4);
            CRC32 withLength = new CRC32();
            withLength.update(file, pos, len + 8);

            System.out.printf("%-6s 0x%02X     %-8d %-9d %08X   %-16s %-20s%n",
                    type, pos, len, len + 12, stored,
                    withoutLength.getValue() == stored ? "match" : "no match",
                    withLength.getValue() == stored ? "match" : "no match");
            order.append(type).append(' ');
            pos += len + 12;
        }
        System.out.println();
        System.out.println("Chunk order: " + order.toString().trim());
    }
}
