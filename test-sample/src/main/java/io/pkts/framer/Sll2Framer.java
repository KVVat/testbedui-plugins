package io.pkts.framer;

import io.pkts.buffer.Buffer;
import io.pkts.packet.MACPacket;
import io.pkts.packet.PCapPacket;
import io.pkts.packet.impl.MACPacketImpl;
import io.pkts.protocol.Protocol;

import java.io.IOException;

/**
 * SLL2 is the linux cooked-mode capture v2.
 * 
 * Structure:
 * - 2 bytes: Protocol type (EtherType)
 * - 2 bytes: Reserved
 * - 4 bytes: Interface index
 * - 2 bytes: ARPHRD_ type
 * - 1 byte: Packet type
 * - 1 byte: Link-layer address length
 * - 8 bytes: Link-layer address
 * Total: 20 bytes
 */
public class Sll2Framer implements Framer<PCapPacket, MACPacket> {

    public Sll2Framer() {
    }

    @Override
    public Protocol getProtocol() {
        return Protocol.SLL2;
    }

    @Override
    public MACPacket frame(final PCapPacket parent, final Buffer buffer) throws IOException {
        if (parent == null) {
            throw new IllegalArgumentException("The parent frame cannot be null");
        }

        final Buffer headers = buffer.readBytes(20);
        final Buffer payload = buffer.slice(buffer.capacity());
        return new MACPacketImpl(Protocol.SLL2, parent, headers, payload);
    }

    @Override
    public boolean accept(final Buffer buffer) throws IOException {
        buffer.markReaderIndex();
        try {
            final Buffer test = buffer.readBytes(20);
            final byte b0 = test.getByte(0);
            final byte b1 = test.getByte(1);
            // EtherType is at offset 0 in SLL2
            return EthernetFramer.getEtherTypeSafe(b0, b1) != null;
        } catch (final IndexOutOfBoundsException e) {
            return false;
        } finally {
            buffer.resetReaderIndex();
        }
    }
}
