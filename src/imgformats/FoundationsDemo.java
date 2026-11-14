package imgformats;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

/** Prints the basic numbers behind the foundations write-up: raw sizes of T16 and byte order. */
public class FoundationsDemo {
    public static void main(String[] args) {
        BufferedImage img = TestImage.t16();
        int w = img.getWidth();
        int h = img.getHeight();

        // Pack the pixels into plain byte arrays, the way a decoder would hold them in memory.
        byte[] rgb = new byte[w * h * 3];
        byte[] rgba = new byte[w * h * 4];
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int a = (argb >>> 24) & 0xFF;
                int i3 = (y * w + x) * 3;
                rgb[i3] = (byte) r;
                rgb[i3 + 1] = (byte) g;
                rgb[i3 + 2] = (byte) b;
                int i4 = (y * w + x) * 4;
                rgba[i4] = (byte) r;
                rgba[i4 + 1] = (byte) g;
                rgba[i4 + 2] = (byte) b;
                rgba[i4 + 3] = (byte) a;
                colours.add(argb);
            }
        }

        System.out.println("T16 is " + w + " x " + h + " with " + (w * h) + " pixels");
        System.out.println("Distinct colours: " + colours.size());
        System.out.println("Raw RGB bytes:  " + rgb.length + " (3 per pixel)");
        System.out.println("Raw RGBA bytes: " + rgba.length + " (4 per pixel)");

        int x = 5;
        int y = 3;
        int index = (y * w + x) * 3;
        System.out.println();
        System.out.println("Pixel at x=" + x + ", y=" + y + " starts at index (" + y + " * " + w + " + " + x + ") * 3 = " + index);
        System.out.println("Its RGB bytes: " + HexDump.hex(rgb, index, 3));

        System.out.println();
        System.out.println("Byte order of 0x12345678 as a 32 bit number");
        System.out.println("Big-endian:    " + HexDump.hex(order(0x12345678, 4, ByteOrder.BIG_ENDIAN), 0, 4));
        System.out.println("Little-endian: " + HexDump.hex(order(0x12345678, 4, ByteOrder.LITTLE_ENDIAN), 0, 4));

        System.out.println();
        System.out.println("The width " + w + " written in three ways");
        System.out.println("32 bit big-endian:    " + HexDump.hex(order(w, 4, ByteOrder.BIG_ENDIAN), 0, 4));
        System.out.println("16 bit big-endian:    " + HexDump.hex(order(w, 2, ByteOrder.BIG_ENDIAN), 0, 2));
        System.out.println("16 bit little-endian: " + HexDump.hex(order(w, 2, ByteOrder.LITTLE_ENDIAN), 0, 2));
    }

    /** Writes value into a buffer of the given size using the given byte order. */
    private static byte[] order(int value, int size, ByteOrder order) {
        ByteBuffer buf = ByteBuffer.allocate(size).order(order);
        if (size == 4) {
            buf.putInt(value);
        } else {
            buf.putShort((short) value);
        }
        return buf.array();
    }
}
