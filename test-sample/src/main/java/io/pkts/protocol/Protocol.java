package io.pkts.protocol;

import io.pkts.framer.Framer;
import io.pkts.framer.FramerManager;

/**
 * @author jonas@jonasborjesson.com
 */
public enum Protocol {
    ICMP("icmp", Layer.LAYER_3),
    ICMP6("icmp6", Layer.LAYER_3),
    IGMP("igmp", Layer.LAYER_3),
    TLS("tcp", Layer.LAYER_7),
    TCP("tcp", Layer.LAYER_4),
    UDP("udp", Layer.LAYER_4),
    SCTP("sctp", Layer.LAYER_4),
    SIP("sip", Layer.LAYER_7),
    SDP("sdp", Layer.LAYER_7),
    ETHERNET_II("eth", Layer.LAYER_2, 1L),
    SLL("sll", Layer.LAYER_2, 113L),
    SLL2("sll2", Layer.LAYER_2, 276L),
    IPv4("ip", Layer.LAYER_3, 101L),
    IPv6("ipv6", Layer.LAYER_3, 229L),
    PCAP("pcap", Layer.LAYER_1),
    RTP("rtp", Layer.LAYER_7),
    RTCP("rtcp", Layer.LAYER_7),
    ARP("arp", Layer.LAYER_3),
    UNKNOWN("unknown", null);

    private final String name;

    private final Layer layer;

    /** Nullable representation of the LINK-LAYER HEADER TYPE VALUES - see http://www.tcpdump.org/linktypes.html */
    private Long linkType = null;

    private Protocol(final String name, final Layer layer) {
        this.name = name;
        this.layer = layer;
    }

    private Protocol(final String name, final Layer layer, Long linkType) {
        this.name = name;
        this.layer = layer;
        this.linkType = linkType;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public Layer getProtocolLayer() {
        return this.layer;
    }

    public static Protocol valueOf(final byte code) {
        switch (code) {
            case (byte) 0x01:
                return ICMP;
            case (byte) 0x02:
                return IGMP;
            case (byte) 0x06:
                return TCP;
            case (byte) 0x11:
                return UDP;
            case (byte) 0x84:
                return SCTP;
            case (byte) 0x3A:
                return ICMP6;
            default:
                return null;
        }
    }

    public Long getLinkType() {
        return linkType;
    }

    public static enum Layer {
        LAYER_1, LAYER_2, LAYER_3, LAYER_4, LAYER_7;
    }

}
