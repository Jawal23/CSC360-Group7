package imgformats;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Draws docs/overview.png: the test image next to the labelled parts of each real sample file. */
public class OverviewFigure {
    private static final int W = 1280, H = 590, SCALE = 2;
    private static final int STRIP_X = 300, STRIP_W = 940, GAP = 3, MIN_BOX = 38, BOX_H = 46;
    private static final Color DARK = new Color(0x1A1A1A), GREY = new Color(0x666666);

    /** One box in a strip: a label and the number of bytes it covers. */
    private record Part(String label, int bytes, boolean data) {}

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        BufferedImage img = new BufferedImage(W * SCALE, H * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(SCALE, SCALE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);

        text(g, "One 16 x 16 image, three file formats", 40, 52, font(Font.BOLD, 28), DARK);
        text(g, "Each strip is a real sample file split into labelled parts. Wider boxes hold more bytes.",
                40, 80, font(Font.PLAIN, 14), GREY);

        drawTestImage(g, 40, 120);
        drawRow(g, 0, "PNG", Path.of("samples", "png", "t16.png"), new PngInspector(), new Color(0x1B7F79));
        drawRow(g, 1, "JPEG", Path.of("samples", "jpeg", "t16.jpg"), new JpegInspector(), new Color(0xC8641E));
        drawRow(g, 2, "GIF", Path.of("samples", "gif", "t16.gif"), new GifInspector(), new Color(0x6A4C93));
        text(g, "Dark boxes hold the compressed image data. Light boxes are signatures, headers, tables and checksums.",
                STRIP_X, 575, font(Font.PLAIN, 13), GREY);
        g.dispose();

        Path out = Path.of("docs", "overview.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
        System.out.println("Wrote " + out);
    }

    private static void drawTestImage(Graphics2D g, int left, int top) {
        BufferedImage t = TestImage.t16();
        int cell = 12;
        for (int y = 0; y < t.getHeight(); y++) {
            for (int x = 0; x < t.getWidth(); x++) {
                g.setColor(new Color(t.getRGB(x, y)));
                g.fillRect(left + x * cell, top + y * cell, cell, cell);
            }
        }
        g.setColor(GREY);
        g.drawRect(left - 1, top - 1, t.getWidth() * cell + 1, t.getHeight() * cell + 1);
        int below = top + t.getHeight() * cell + 26;
        text(g, "T16, " + t.getWidth() + " x " + t.getHeight() + " pixels", left, below, font(Font.BOLD, 15), DARK);
        text(g, "R = x * 17, G = y * 17, B = 128", left, below + 22, font(Font.PLAIN, 14), GREY);
        text(g, "Raw RGB: " + t.getWidth() * t.getHeight() * 3 + " bytes", left, below + 44, font(Font.PLAIN, 14), GREY);
    }

    private static void drawRow(Graphics2D g, int index, String format, Path file, FormatInspector inspector,
            Color color) throws IOException {
        byte[] data = Files.readAllBytes(file);
        List<Part> parts = parts(format, inspector.inspect(data));
        int top = 110 + index * 150;
        text(g, format, STRIP_X, top + 24, font(Font.BOLD, 22), DARK);
        text(g, file.toString().replace('\\', '/') + "   " + String.format(Locale.ROOT, "%,d", data.length) + " bytes",
                STRIP_X + 76, top + 24,
                new Font(Font.MONOSPACED, Font.PLAIN, 14), GREY);
        text(g, "starts with  " + HexDump.hex(data, 0, 8), STRIP_X, top + 50,
                new Font(Font.MONOSPACED, Font.BOLD, 15), DARK);
        drawStrip(g, parts, top + 64, color);
    }

    /** Turns inspector segments into boxes. PNG chunks are merged so each chunk is one box. */
    private static List<Part> parts(String format, List<Segment> segs) {
        List<Part> out = new ArrayList<>();
        String lastKey = null;
        for (Segment s : segs) {
            boolean merge = format.equals("PNG");
            String key = merge ? s.name().split(" ")[0] : s.name();
            boolean data = s.category().equals("data");
            if (merge && key.equals(lastKey)) {
                Part p = out.remove(out.size() - 1);
                out.add(new Part(p.label(), p.bytes() + s.length(), p.data() || data));
            } else {
                out.add(new Part(label(format, key), s.length(), data));
            }
            lastKey = key;
        }
        return out;
    }

    private static String label(String format, String key) {
        return switch (key) {
            case "Signature" -> format.equals("GIF") ? "Header" : "Signature";
            case "LogicalScreenDescriptor" -> "Screen";
            case "GlobalColorTable" -> "Palette";
            case "ImageDescriptor" -> "Image";
            case "LzwMinCodeSize" -> "Code";
            case "ImageDataSubBlocks", "compressed data" -> "Data";
            default -> key;
        };
    }

    /** Box width grows with the square root of the byte count, and never drops below MIN_BOX. */
    private static void drawStrip(Graphics2D g, List<Part> parts, int top, Color base) {
        int n = parts.size();
        double[] w = new double[n];
        boolean[] small = new boolean[n];
        boolean changed = true;
        while (changed) {
            changed = false;
            double room = STRIP_W - GAP * (n - 1);
            double sum = 0;
            for (int i = 0; i < n; i++) {
                if (small[i]) room -= MIN_BOX; else sum += Math.sqrt(parts.get(i).bytes());
            }
            for (int i = 0; i < n; i++) {
                w[i] = small[i] ? MIN_BOX : room * Math.sqrt(parts.get(i).bytes()) / sum;
                if (!small[i] && w[i] < MIN_BOX) { small[i] = true; changed = true; }
            }
        }
        double x = STRIP_X;
        for (int i = 0; i < n; i++) {
            Part p = parts.get(i);
            Color fill = p.data() ? base : tint(base);
            g.setColor(fill);
            g.fillRoundRect((int) Math.round(x), top, (int) Math.round(w[i]), BOX_H, 8, 8);
            fitted(g, p.label(), x, w[i], top + 28, p.data() ? Color.WHITE : DARK, Font.BOLD, 14);
            fitted(g, p.bytes() + " B", x, w[i], top + BOX_H + 18, GREY, Font.PLAIN, 12);
            x += w[i] + GAP;
        }
    }

    private static Color tint(Color c) {
        return new Color(255 - (255 - c.getRed()) * 35 / 100, 255 - (255 - c.getGreen()) * 35 / 100,
                255 - (255 - c.getBlue()) * 35 / 100);
    }

    /** Draws the text centred in a box and shrinks the font until it fits. */
    private static void fitted(Graphics2D g, String s, double x, double width, int y, Color c, int style, int size) {
        Font f = font(style, size);
        FontMetrics m = g.getFontMetrics(f);
        while (m.stringWidth(s) > width - 4 && size > 8) {
            f = font(style, --size);
            m = g.getFontMetrics(f);
        }
        text(g, s, (int) Math.round(x + (width - m.stringWidth(s)) / 2), y, f, c);
    }

    private static Font font(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    private static void text(Graphics2D g, String s, int x, int y, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, x, y);
    }
}
