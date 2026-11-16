package imgformats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
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

        System.out.println();
        printChunkChecks(file);
        System.out.println();
        printPixelData(file);
        FormatSummary.png(out.toString(), file, segs);
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
            String type = new String(file, pos + 4, 4, StandardCharsets.US_ASCII);
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

    // Joins the IDAT data, inflates it and shows the filter byte that starts each row
    private static void printPixelData(byte[] file) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(file);
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        int pos = 8;
        while (pos + 12 <= file.length) {
            int len = buf.getInt(pos);
            if (new String(file, pos + 4, 4, StandardCharsets.US_ASCII).equals("IDAT")) {
                idat.write(file, pos + 8, len);
            }
            pos += len + 12;
        }

        int height = buf.getInt(20);
        Inflater inflater = new Inflater();
        inflater.setInput(idat.toByteArray());
        byte[] raw = new byte[1 << 16]; // plenty for a 16x16 image
        int size;
        try {
            size = inflater.inflate(raw);
        } catch (DataFormatException e) {
            throw new IOException("IDAT data is not a valid zlib stream", e);
        }
        boolean done = inflater.finished();
        inflater.end();

        int rowSize = size / height;
        System.out.println("IDAT data joined: " + idat.size() + " bytes, inflated: " + size + " bytes"
                + (done ? "" : " (not finished)"));
        System.out.println(height + " rows of " + rowSize + " bytes, first byte of each row is the filter type:");
        StringBuilder filters = new StringBuilder();
        for (int row = 0; row < height; row++) {
            filters.append(String.format("%02X ", raw[row * rowSize]));
        }
        System.out.println(filters.toString().trim());
    }
}