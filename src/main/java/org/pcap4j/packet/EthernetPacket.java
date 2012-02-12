/*_##########################################################################
  _##
  _##  Copyright (C) 2011-2012  Kaito Yamada
  _##
  _##########################################################################
*/

package org.pcap4j.packet;

import java.util.ArrayList;
import java.util.List;

import org.pcap4j.packet.namedvalue.EtherType;
import org.pcap4j.util.ByteArrays;
import org.pcap4j.util.MacAddress;
import static org.pcap4j.util.ByteArrays.MAC_ADDRESS_SIZE_IN_BYTE;
import static org.pcap4j.util.ByteArrays.SHORT_SIZE_IN_BYTE;

/**
 * @author Kaito Yamada
 * @since pcap4j 0.9.1
 */
public final class EthernetPacket extends AbstractPacket {

  private static final int MIN_ETHERNET_PACKET_LENGTH = 60;

  private final EthernetHeader header;
  private final Packet payload;

  // Ethernet frame must be at least 60 bytes except FCS. If it's less than 60 bytes, pad with this field.
  // Although this class handles trailer, it's actually responsibility of NIF.
  private final byte[] trailer;

  /**
   *
   * @param rawData
   */
  public EthernetPacket(byte[] rawData) {
    this.header = new EthernetHeader(rawData);

    byte[] rawPayload
      = ByteArrays.getSubArray(
          rawData,
          EthernetHeader.ETHERNET_HEADER_SIZE,
          rawData.length - EthernetHeader.ETHERNET_HEADER_SIZE
        );

    this.payload
      = PacketFactory.getInstance()
          .newPacketByEtherType(rawPayload, header.getType().value());

    int payloadLength = this.payload.length();
    if (rawData.length > payloadLength) {
      this.trailer
        = ByteArrays.getSubArray(
            rawData, payloadLength, rawData.length - payloadLength
          );
    }
    else {
      this.trailer = new byte[0];
    }
  }

  private EthernetPacket(Builder builder) {
    if (
         builder == null
      || builder.dstAddr == null
      || builder.srcAddr == null
      || builder.type == null
      || builder.payloadBuilder == null
      || builder.trailer == null
    ) {
      throw new NullPointerException();
    }

    this.payload = builder.payloadBuilder.build();
    this.header = new EthernetHeader(builder);

    if (
         builder.validateAtBuild
      && this.payload.length() + builder.trailer.length
           < MIN_ETHERNET_PACKET_LENGTH
    ) {
      this.trailer = new byte[MIN_ETHERNET_PACKET_LENGTH];
    }
    else {
      this.trailer = new byte[builder.trailer.length];
    }
    System.arraycopy(
      builder.trailer, 0, this.trailer, 0, builder.trailer.length
    );
  }

  @Override
  public EthernetHeader getHeader() {
    return header;
  }

  @Override
  public Packet getPayload() {
    return payload;
  }

//  @Override
//  public boolean isValid() {
//    if (super.buildValid()) {
//      // A packet before padding may be captured. How do I verify?
//      return length() >= MIN_ETHERNET_PACKET_LENGTH;
//    }
//    else {
//      return false;
//    }
//  }

  @Override
  protected int measureLength() {
    int length = super.measureLength();
    length += trailer.length;
    return length;
  }

  @Override
  protected byte[] buildRawData() {
    byte[] rawData = super.buildRawData();
    if (trailer.length != 0) {
      System.arraycopy(
        trailer, 0, rawData, rawData.length - trailer.length, trailer.length
      );
    }
    return rawData;
  }

  @Override
  public Builder getBuilder() {
    return new Builder(this);
  }

  /**
   * @author Kaito Yamada
   * @since pcap4j 0.9.1
   */
  public static final class Builder implements Packet.Builder {

    private MacAddress dstAddr;
    private MacAddress srcAddr;
    private EtherType type;
    private Packet.Builder payloadBuilder;
    private byte[] trailer = new byte[0];
    private boolean validateAtBuild = true;

    /**
     *
     */
    public Builder() {}

    private Builder(EthernetPacket packet) {
      this.dstAddr = packet.header.dstAddr;
      this.srcAddr = packet.header.srcAddr;
      this.type = packet.header.type;
      this.payloadBuilder = packet.payload.getBuilder();
      if (packet.trailer != null) {
        this.trailer = new byte[packet.trailer.length];
        System.arraycopy(
          packet.trailer, 0, this.trailer, 0, packet.trailer.length
        );
      }
    }

    /**
     *
     * @param dstAddr
     * @return
     */
    public Builder dstAddr(MacAddress dstAddr) {
      this.dstAddr = dstAddr;
      return this;
    }

    /**
     *
     * @param srcAddr
     * @return
     */
    public Builder srcAddr(MacAddress srcAddr) {
      this.srcAddr = srcAddr;
      return this;
    }

    /**
     *
     * @param type
     * @return
     */
    public Builder type(EtherType type) {
      this.type = type;
      return this;
    }

    /**
     *
     * @param payloadBuilder
     * @return
     */
    public Builder payloadBuilder(Packet.Builder payloadBuilder) {
      this.payloadBuilder = payloadBuilder;
      return this;
    }

    /**
     *
     * @param trailer
     * @return
     */
    public Builder trailer(byte[] trailer) {
      this.trailer = trailer;
      return this;
    }

    /**
     *
     * @param validateAtBuild
     * @return
     */
    public Builder validateAtBuild(boolean validateAtBuild) {
      this.validateAtBuild = validateAtBuild;
      return this;
    }

    public EthernetPacket build() {
      return new EthernetPacket(this);
    }

  }

  /**
   * @author Kaito Yamada
   * @since pcap4j 0.9.1
   */
  public final class EthernetHeader extends AbstractHeader {

    private static final int DST_ADDR_OFFSET = 0;
    private static final int DST_ADDR_SIZE = MAC_ADDRESS_SIZE_IN_BYTE;
    private static final int SRC_ADDR_OFFSET = DST_ADDR_OFFSET + DST_ADDR_SIZE;
    private static final int SRC_ADDR_SIZE = MAC_ADDRESS_SIZE_IN_BYTE;
    private static final int TYPE_OFFSET = SRC_ADDR_OFFSET + SRC_ADDR_SIZE;
    private static final int TYPE_SIZE = SHORT_SIZE_IN_BYTE;
    private static final int ETHERNET_HEADER_SIZE = TYPE_OFFSET + TYPE_SIZE;

    private final MacAddress dstAddr;
    private final MacAddress srcAddr;
    private final EtherType type;

    private EthernetHeader(byte[] rawData) {
      if (rawData.length < ETHERNET_HEADER_SIZE) {
        throw new IllegalArgumentException();
      }

      this.dstAddr = ByteArrays.getMacAddress(rawData, DST_ADDR_OFFSET);
      this.srcAddr = ByteArrays.getMacAddress(rawData, SRC_ADDR_OFFSET);
      this.type
        = EtherType.getInstance(ByteArrays.getShort(rawData, TYPE_OFFSET));
    }

    private EthernetHeader(Builder builder) {
      this.dstAddr = builder.dstAddr;
      this.srcAddr = builder.srcAddr;
      this.type = builder.type;
    }

    /**
     *
     * @return
     */
    public MacAddress getDstAddr() {
      return dstAddr;
    }

    /**
     *
     * @return
     */
    public MacAddress getSrcAddr() {
      return srcAddr;
    }

    /**
     *
     * @return
     */
    public EtherType getType() {
      return type;
    }

    @Override
    protected List<byte[]> getRawFields() {
      List<byte[]> rawFields = new ArrayList<byte[]>();
      rawFields.add(ByteArrays.toByteArray(dstAddr));
      rawFields.add(ByteArrays.toByteArray(srcAddr));
      rawFields.add(ByteArrays.toByteArray(type.value()));
      return rawFields;
    }

    @Override
    public int length() {
      return ETHERNET_HEADER_SIZE;
    }

    @Override
    protected String buildString() {
      StringBuilder sb = new StringBuilder();

      sb.append("[Ethernet Header (")
        .append(length())
        .append(" bytes)]\n");
      sb.append("  Destination address: ")
        .append(dstAddr)
        .append("\n");
      sb.append("  Source address: ")
        .append(srcAddr)
        .append("\n");
      sb.append("  Type: ")
        .append(type)
        .append("\n");

      if (trailer != null && trailer.length != 0) {
        sb.append("  Trailer: 0x")
          .append(ByteArrays.toHexString(trailer, ""))
          .append("\n");
      }

      return sb.toString();
    }

  }

}
