package imgformats;

/** One labelled byte range in an image file. Offsets count from byte 0 of the file. */
public record Segment(long offset, int length, String name, String category, String description) {
    public long end() {
        return offset + length;
    }
}
