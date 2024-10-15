package org.drop.kcp.core;

/**
 * KCP 确认数据
 * @param segmentId 序号
 * @param timeStamp  时间戳
 */
public record KCPACKInfo(int segmentId, long timeStamp) {

}
