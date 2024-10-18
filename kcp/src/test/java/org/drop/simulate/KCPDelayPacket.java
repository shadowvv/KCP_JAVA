package org.drop.simulate;

/**
 * 带延迟的数据包
 */
public record KCPDelayPacket(byte[] data, int size, long timeStamp) {

}
