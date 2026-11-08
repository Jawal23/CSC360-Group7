package imgformats;

import java.awt.image.BufferedImage;

/** Shared 16x16 test image used for all three formats. R = x*17, G = y*17, B = 128, fully opaque. */
public final class TestImage {
    private TestImage() {}

    public static BufferedImage t16() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int r = x * 17, g = y * 17, b = 128;
                img.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    /** Same pixels without alpha. JPEG and GIF writers in ImageIO want this one. */
    public static BufferedImage t16Rgb() {
        BufferedImage src = t16();
        BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                out.setRGB(x, y, src.getRGB(x, y) & 0xFFFFFF);
            }
        }
        return out;
    }
}
