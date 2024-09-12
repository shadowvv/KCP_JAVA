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
     * 指令类型
     */
    private int commandId;
    /**
     * 编号，用于重组分片数据
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
     * 分片序号
     */
    private int segmentId;
    /**
     * 未确认序号
     */
    private int unacknowledgedSegmentId;
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

    /**
     *
     * @param length 数据大小
     */
    public KCPSegment(int length) {
        data = new byte[length];
    }

    /**
     * 编码分片头
     * @param buffer 数据缓存
     */
    public void encodeHead(ByteBuffer buffer) {
        buffer.putInt(conversationId);
        buffer.putInt(commandId);
        buffer.putInt(fragmentId);
        buffer.putInt(windowSize);
        buffer.putLong(timeStamp);
        buffer.putInt(segmentId);
        buffer.putInt(unacknowledgedSegmentId);
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
