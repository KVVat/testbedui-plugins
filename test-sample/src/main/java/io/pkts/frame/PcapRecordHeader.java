package io.pkts.frame;

import io.pkts.buffer.Buffer;
import io.pkts.buffer.Buffers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;

/**
 * @author jonas@jonasborjesson.com
 * 
 */
public final class PcapRecordHeader {
    /**
     * pcaprec_hdr_s struct is SIZE bytes long.
     */
    public static final int SIZE = 16;

    private final ByteOrder byteOrder;

    private final Buffer body;

    private final boolean nsTimestamps;

    public PcapRecordHeader(final ByteOrder byteOrder, final Buffer body) {
        this(byteOrder, body, false);
    }

    public PcapRecordHeader(final ByteOrder byteOrder, final Buffer body, final boolean nsTimestamps) {
        assert body != null;
        assert body.capacity() == SIZE;

        this.byteOrder = byteOrder;
        this.body = body;
        this.nsTimestamps = nsTimestamps;
    }

    public static PcapRecordHeader createDefaultHeader(final long timestamp) {
        final byte[] body = new byte[SIZE];
        final Buffer buffer = Buffers.wrap(body);
        buffer.setUnsignedInt(0, timestamp / 1000L);
        buffer.setUnsignedInt(4, timestamp % 1000L * 1000L);
        return new PcapRecordHeader(ByteOrder.LITTLE_ENDIAN, buffer);
    }

    public long getTimeStampSeconds() {
        return PcapGlobalHeader.getUnsignedInt(0, this.body.getArray(), this.byteOrder);
    }

    @Deprecated
    public long getTimeStampMicroSeconds() {
        return PcapGlobalHeader.getUnsignedInt(4, this.body.getArray(), this.byteOrder);
    }

    public long getTimeStampMicroOrNanoSeconds() {
        return PcapGlobalHeader.getUnsignedInt(4, this.body.getArray(), this.byteOrder);
    }

    public long getTotalLength() {
        return PcapGlobalHeader.getUnsignedInt(12, this.body.getArray(), this.byteOrder);
    }

    public void setTotalLength(final long length) {
        this.body.setUnsignedInt(12, length);
    }

    public long getCapturedLength() {
        return PcapGlobalHeader.getUnsignedInt(8, this.body.getArray(), this.byteOrder);
    }

    public void setCapturedLength(final long length) {
        this.body.setUnsignedInt(8, length);
    }

    public void write(final OutputStream out) throws IOException {
        out.write(this.body.getArray());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        final long ts = getTimeStampSeconds();
        final long tsMicroOrNanoSeconds = getTimeStampMicroOrNanoSeconds();
        sb.append("ts_s: ").append(ts).append("\n");

        if (this.nsTimestamps) {
          sb.append("ts_ns: ");
        } else {
          sb.append("ts_us: ");
        }

        sb.append(tsMicroOrNanoSeconds).append("\n")
          .append("octects: ").append(getTotalLength()).append("\n")
          .append("length: ").append(getCapturedLength()).append("\n");

        return sb.toString();
    }
}
