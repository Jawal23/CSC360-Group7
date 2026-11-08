package imgformats;

import java.io.IOException;
import java.util.List;

public interface FormatInspector {
    String formatName();

    /**
     * Walks the file and returns segments in file order.
     * Every byte must belong to exactly one segment: no gaps, no overlaps.
     * Category is one of: signature, header, metadata, table, data, checksum, terminator, other.
     */
    List<Segment> inspect(byte[] file) throws IOException;
}
