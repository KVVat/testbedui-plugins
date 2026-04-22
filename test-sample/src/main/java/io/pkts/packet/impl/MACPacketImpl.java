/**
 *
 */
package io.pkts.packet.impl;

import io.pkts.buffer.Buffer;
import io.pkts.buffer.Buffers;
import io.pkts.frame.UnknownEtherType;
import io.pkts.framer.EthernetFramer;
import io.pkts.framer.IPv4Framer;
import io.pkts.framer.IPv6Framer;
import io.pkts.packet.IPPacket;
import io.pkts.packet.MACPacket;
import io.pkts.packet.PCapPacket;
import io.pkts.packet.PacketParseException;
import io.pkts.protocol.Protocol;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Monkey-patched copy of upstream io.pkts.packet.impl.MACPacketImpl.
 *
 * Upstream hardcodes {@link #getNextProtocol()} to return {@link Protocol#IPv4}
 * whenever the parent link type is not Ethernet II (see upstream TODO comment:
 * "figure out how an SLL packet indicates IPv4 vs IPv6"). That breaks IPv6
 * flows captured over SLL / SLL2, because the IPv6 payload gets handed to the
 * IPv4 framer and downstream parsers read IPv6 address bytes as TCP ports.
 *
 * This version adds explicit EtherType dispatch for SLL and SLL2:
 *   - SLL2 (20-byte header, pcap link type 276): EtherType at offset 0-1
 *   - SLL  (16-byte header, pcap link type 113): EtherType at offset 14-15
 * Everything else is byte-identical to upstream.
 *
 * @author jonas@jonasborjesson.com (original)
 */
public final class MACPacketImpl extends AbstractPacket implements MACPacket {

    private static final IPv4Framer ipv4Framer = new IPv4Framer();
    private static final IPv6Framer ipv6Framer = new IPv6Framer();

    private final PCapPacket parent;
    private final String sourceMacAddress;
    private final String destinationMacAddress;

    private final Buffer headers;

    public static MACPacketImpl create(final PCapPacket parent, final Buffer headers) {
        if (headers.capacity() < 14) {
            throw new IllegalArgumentException("Not enough bytes to create this header");
        }

        if (parent == null) {
            throw new IllegalArgumentException("The parent packet cannot be null");
        }

        return new MACPacketImpl(Protocol.ETHERNET_II, parent, headers, null);
    }

    public MACPacketImpl(final Protocol protocol, final PCapPacket parent, final Buffer headers, final Buffer payload) {
        super(protocol, parent, payload);
        this.parent = parent;
        this.headers = headers;
        this.sourceMacAddress = null;
        this.destinationMacAddress = null;
    }

    @Override
    public final String getSourceMacAddress() {
        if (this.sourceMacAddress != null) {
            return this.sourceMacAddress;
        }

        try {
            return toHexString(this.headers, 6, 6);
        } catch (final IOException e) {
            throw new RuntimeException("Unable to read data from the underlying Buffer.", e);
        }
    }

    public static String toHexString(final Buffer buffer, final int start, final int length) throws IOException {
        final StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + length; ++i) {
            final byte b = buffer.getByte(i);
            sb.append(String.format("%02X", b));
            if (i < start + length - 1) {
                sb.append(":");
            }
        }
        return sb.toString();
    }

    @Override
    public final String getDestinationMacAddress() {
        if (this.destinationMacAddress != null) {
            return this.destinationMacAddress;
        }

        try {
            return toHexString(this.headers, 0, 6);
        } catch (final IOException e) {
            throw new RuntimeException("Unable to read data from the underlying Buffer.", e);
        }
    }

    @Override
    public void verify() {
        // nothing to verify
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Destination Mac Address: ").append(this.destinationMacAddress)
          .append(" Source Mac Address: ").append(this.sourceMacAddress);
        return sb.toString();
    }

    @Override
    public long getArrivalTime() {
        return this.parent.getArrivalTime();
    }

    @Override
    public void write(final OutputStream out, final Buffer payload) throws IOException {
        this.parent.write(out, Buffers.wrap(this.headers, payload));
    }

    @Override
    public void setSourceMacAddress(final String macAddress) {
        setMacAddress(macAddress, true);
    }

    @Override
    public void setDestinationMacAddress(final String macAddress) {
        setMacAddress(macAddress, false);
    }

    private void setMacAddress(final String macAddress, final boolean setSourceMacAddress)
            throws IllegalArgumentException {
        if (macAddress == null || macAddress.isEmpty()) {
            throw new IllegalArgumentException("Null or empty string cannot be a valid MAC Address.");
        }
        final String[] segments = macAddress.split(":");
        if (segments.length != 6) {
            throw new IllegalArgumentException("Invalid MAC Address. Not enough segments");
        }

        final int offset = setSourceMacAddress ? 6 : 0;
        for (int i = 0; i < 6; ++i) {
            final byte b = (byte) ((Character.digit(segments[i].charAt(0), 16) << 4) + Character.digit(segments[i]
                    .charAt(1), 16));
            this.headers.setByte(i + offset, b);
        }
    }

    @Override
    public MACPacket clone() {
        final PCapPacket pkt = this.parent.clone();
        return new MACPacketImpl(getProtocol(), pkt, this.headers.clone(), getPayload().clone());
    }

    public Protocol getNextProtocol() throws IOException {
        final Protocol linkProto = getProtocol();

        if (linkProto == Protocol.ETHERNET_II) {
            EthernetFramer.EtherType etherType;
            try {
                etherType = EthernetFramer.getEtherType(headers.getByte(12), headers.getByte(13));
            } catch (UnknownEtherType e) {
                throw new PacketParseException(12, "Unknown Ethernet type");
            }
            return etherTypeToProtocol(etherType);
        }

        if (linkProto == Protocol.SLL2) {
            // SLL2 header layout: [2 EtherType][2 reserved][4 ifindex][2 ARPHRD][1 ptype][1 lladdrlen][8 lladdr]
            return etherTypeToProtocol(headers.getByte(0), headers.getByte(1));
        }

        if (linkProto == Protocol.SLL) {
            // SLL header layout: [2 ptype][2 ARPHRD][2 lladdrlen][8 lladdr][2 EtherType]
            return etherTypeToProtocol(headers.getByte(14), headers.getByte(15));
        }

        return Protocol.IPv4;
    }

    private static Protocol etherTypeToProtocol(final byte b0, final byte b1) {
        final EthernetFramer.EtherType etherType = EthernetFramer.getEtherTypeSafe(b0, b1);
        if (etherType == null) {
            return Protocol.UNKNOWN;
        }
        return etherTypeToProtocol(etherType);
    }

    private static Protocol etherTypeToProtocol(final EthernetFramer.EtherType etherType) {
        if (etherType == null) {
            return Protocol.UNKNOWN;
        }
        switch (etherType) {
            case IPv4:
                return Protocol.IPv4;
            case IPv6:
                return Protocol.IPv6;
            case ARP:
                return Protocol.ARP;
            default:
                return Protocol.UNKNOWN;
        }
    }

    @Override
    public IPPacket getNextPacket() throws IOException {
        final Buffer payload = getPayload();
        if (payload == null) {
            return null;
        }
        switch (getNextProtocol()) {
            case IPv4:
                return ipv4Framer.frame(this, payload);
            case IPv6:
                return ipv6Framer.frame(this, payload);
            default:
                return null;
        }
    }
}
