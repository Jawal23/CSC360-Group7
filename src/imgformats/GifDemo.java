package imgformats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Writes T16 as a GIF, then runs GifInspector on the file and prints the segment table. */
public final class GifDemo {
    public static void main(String[] args) throws IOException {
        Path out = Path.of("samples", "gif", "t16.gif");
        Files.createDirectories(out.getParent());

        // Write T16 via ImageIO
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ImageIO.write(TestImage.t16Rgb(), "gif", buf);
        byte[] bytes = buf.toByteArray();
        Files.write(out, bytes);

        System.out.println("Wrote " + out + " (" + bytes.length + " bytes)");
        System.out.println();

        // First 32 bytes raw
        System.out.println("First 32 bytes:");
        System.out.println(HexDump.hex(bytes, 0, Math.min(32, bytes.length)));
        System.out.println();

        // Inspect
        GifInspector insp = new GifInspector();
        List<Segment> segs = insp.inspect(bytes);
        HexDump.printSegments(bytes, segs);
        FormatSummary.gif(out.toString(), bytes, segs);
    }
}
