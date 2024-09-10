import java.nio.ByteBuffer;

/**
 * KCP 分片
 */
public class KCPSegment {
    /**
     * 会话ID，用于区分不同的KCP连接
     */
    private int conversationId;
    /**
     * 指令类型 (e.g., KCP_CMD_PUSH, KCP_CMD_ACK)
     */
    private int commandId;
    /**
     * 分片编号，用于重组数据
     */
    private int fragmentId;
    /**
     * 窗口大小
     */
    private int windowSize;
    /**
     * 发送时间戳
     */
    private long timeStamp;
    /**
     * 段序号
     */
    private int segmentId;
    /**
     * 未确认序号
     */
    private int unacknowledgedSegmentId;
    /**
     * 数据长度
     */
    private int length;
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
    private int fastAck;
    /**
     * 发送次数
     */
    private int sendCount;
    /**
     * 数据
     */
    private byte[] data;

    public KCPSegment(int size) {
        data = new byte[size];
    }

    public void encodeHead(ByteBuffer buffer) {
        buffer.putInt(conversationId);
        buffer.putInt(commandId);
        buffer.putInt(fragmentId);
        buffer.putInt(windowSize);
        buffer.putLong(timeStamp);
        buffer.putInt(segmentId);
        buffer.putInt(unacknowledgedSegmentId);
        buffer.putInt(length);
    }

    public void decodeHead(ByteBuffer buffer) {
        conversationId = buffer.getInt();
        commandId = buffer.getInt();
        fragmentId = buffer.getInt();
        windowSize = buffer.getInt();
        timeStamp = buffer.getLong();
        segmentId = buffer.getInt();
        unacknowledgedSegmentId = buffer.getInt();
        length = buffer.getInt();
    }

    public void encodeData(ByteBuffer buffer) {
        buffer.put(data, 0, length);
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
        return length;
    }

    public void setLength(int length) {
        this.length = length;
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

    public int getFastAck() {
        return fastAck;
    }

    public void setFastAck(int fastAck) {
        this.fastAck = fastAck;
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
}
