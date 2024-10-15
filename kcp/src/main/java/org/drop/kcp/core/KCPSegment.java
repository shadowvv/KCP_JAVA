package org.drop.kcp.core;

import java.nio.ByteBuffer;

/**
 * KCP 分片
 */
public class KCPSegment {

    public static final int KCP_OVERHEAD = 36;

    /**
     * 会话ID，用于区分不同的KCP连接
     */
    private int conversationId;
    /**
     * 分片序号
     */
    private int segmentId;
    /**
     * 编号，用于重组分片数据
     */
    private int fragmentId;
    /**
     * 指令类型
     */
    private int commandId;
    /**
     * 发送时间戳
     */
    private long timeStamp;


    /**
     * 未确认序号
     */
    private int unacknowledgedSegmentId;
    /**
     * 窗口大小
     */
    private int windowSize;
    /**
     * 重新发送的时间戳
     */
    private long resendTimeStamp;
    /**
     * 超时时间（重传定时器）
     */
    private long RTO;
    /**
     * 快速确认标志，标识收到的确认数
     */
    private int fastAcknowledgedCount;
    /**
     * 发送次数
     */
    private int sendCount;

    /**
     * 数据
     */
    private byte[] data;

    /**
     *
     * @param length 数据大小
     */
    public KCPSegment(int length) {
        data = new byte[length];
    }

    /**
     *
     * @param data 数据
     */
    public KCPSegment(byte[] data) {
        this.data = data;
    }

    /**
     * 编码分片头
     * @param buffer 数据缓存
     */
    public void encodeHead(ByteBuffer buffer) {
        buffer.putInt(conversationId);
        buffer.putInt(segmentId);
        buffer.putInt(fragmentId);
        buffer.putInt(commandId);
        buffer.putLong(timeStamp);

        buffer.putInt(unacknowledgedSegmentId);
        buffer.putInt(windowSize);
        buffer.putInt(data.length);
    }

    /**
     * 编码分片数据
     * @param buffer 数据缓存
     */
    public void encodeData(ByteBuffer buffer) {
        buffer.put(data);
    }


    public long getConversationId() {
        return conversationId;
    }

    public void setConversationId(int conversationId) {
        this.conversationId = conversationId;
    }

    public long getCommandId() {
        return commandId;
    }

    public void setCommandId(int commandId) {
        this.commandId = commandId;
    }

    public int getFragmentId() {
        return fragmentId;
    }

    public void setFragmentId(int fragmentId) {
        this.fragmentId = fragmentId;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public int getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(int segmentId) {
        this.segmentId = segmentId;
    }

    public int getUnacknowledgedSegmentId() {
        return unacknowledgedSegmentId;
    }

    public void setUnacknowledgedSegmentId(int unacknowledgedSegmentId) {
        this.unacknowledgedSegmentId = unacknowledgedSegmentId;
    }

    public int getLength() {
        if (data == null) {
            return 0;
        }
        return data.length;
    }

    public long getResendTimeStamp() {
        return resendTimeStamp;
    }

    public void setResendTimeStamp(long resendTimeStamp) {
        this.resendTimeStamp = resendTimeStamp;
    }

    public long getRTO() {
        return RTO;
    }

    public void setRTO(long RTO) {
        this.RTO = RTO;
    }

    public int getFastAcknowledgedCount() {
        return fastAcknowledgedCount;
    }

    public void setFastAcknowledgedCount(int fastAcknowledgedCount) {
        this.fastAcknowledgedCount = fastAcknowledgedCount;
    }

    public int getSendCount() {
        return sendCount;
    }

    public void setSendCount(int sendCount) {
        this.sendCount = sendCount;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "KCPSegment [conversationId=" + conversationId + ", segmentId=" + segmentId
                + ", fragmentId=" + fragmentId + ", commandId=" + commandId + ", timeStamp=" + timeStamp
                + ", unacknowledgedSegmentId=" + unacknowledgedSegmentId
                + ", windowSize=" + windowSize + ", resendTimeStamp=" + resendTimeStamp
                + ", RTO=" + RTO + ", fastAck=" + fastAcknowledgedCount + ", sendCount=" + sendCount + "]";
    }
}
