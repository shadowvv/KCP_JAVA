/**
 * 带延迟的数据包
 */
public record KCPDelayPacket(byte[] data, int size,long timeStamp) {

}
