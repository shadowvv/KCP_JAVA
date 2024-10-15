package core;

import java.nio.ByteBuffer;
import exception.*;
import java.util.*;

/**
 * KCP 连接传输上下文
 */
public class KCPContext {

    private boolean fastAckConserve = true;

    //基本参数
    /**
     *
     */
    private int conversationId;
    /**
     * 最大传输单元
     */
    private int MTU;
    /**
     * 最大分片大小
     */
    private int MSS;
    /**
     * 当前状态
     * TODO:当前没有作用
     */
    private int state;

    //序号
    /**
     * 未确认的最小发送序号
     */
    private int sendUnacknowledgedSegmentId;
    /**
     * 下一个要发送的数据序号
     */
    private int nextSendSegmentId;
    /**
     * 下一个期望接收的数据序号
     */
    private int nextReceiveSegmentId;

    //队列和缓冲区
    /**
     * 发送队列，存储还未发送的Segment。
     */
    private LinkedList<KCPSegment> sendQueue;
    /**
     * 接收队列，存储已接收但未处理的Segment。
     */
    private LinkedList<KCPSegment> receiveQueue;
    /**
     * 发送缓冲区，存储已发送但未确认的Segment。
     */
    private LinkedList<KCPSegment> sendBuff;
    /**
     * 接收缓冲区，存储乱序接收到的Segment。
     */
    private LinkedList<KCPSegment> receiveBuff;

    //ACK管理
    /**
     * 保存待发送的ACK序号和时间戳的列表。
     */
    private List<KCPACKInfo> ackList;

    //窗口管理
    /**
     * 发送窗口大小，用于控制发送方能发送多少未确认的包。
     */
    private int sendWindow;
    /**
     * 接收窗口大小，表示接收方的缓冲区剩余空间
     */
    private int receiveWindow;
    /**
     * 拥塞窗口大小，用于控制拥塞避免算法。
     */
    private int crowdedWindow;
    /**
     * 远端窗口大小，即对端能接收的窗口大小。
     */
    private int remoteWindow;
    /**
     * 探测标志位，表示是否需要发送窗口探测包。
     */
    private int probe;

    //计时和重传管理
    /**
     * 当前时间戳
     */
    private long current;
    /**
     * 定时器触发间隔，用于刷新和检查是否需要发送包。
     */
    private int interval;
    /**
     * 最近一次发送的时间戳
     */
    private long lastSendTimeStamp;
    /**
     * 最近一次接收到的ACK包的时间戳
     */
    private long lastACKTimeStamp;
    /**
     * 定时刷新时间戳，用于周期性发送数据。
     */
    private long nextFlushTimeStamp;
    /**
     * 下一次探测时间戳，用于检测对端窗口大小。
     */
    private long nextProbeTimeStamp;
    /**
     * 探测等待时间，当远端窗口为0时，发送探测包的间隔时间。
     */
    private int probeWait;
    /**
     * 慢启动阈值，用于拥塞控制
     */
    private int slowStartThresh;
    /**
     * RTT波动值（Round Trip Time Variation），用于计算RTO（重传超时）
     */
    private int rttVal;
    /**
     * 平滑的RTT（Smoothed RTT），表示RTT的均值。
     */
    private int smoothRtti;
    /**
     * 当前的重传超时时间（Retransmission Timeout）。
     */
    private int currentRTO;
    /**
     * 最小重传超时时间，表示RTO的下限。
     */
    private int minRto;

    //控制和策略
    /**
     * 是否启用无延迟模式。无延迟模式下，发送更快但可能导致网络波动。
     */
    private boolean noDelay;
    /**
     * 是否已经调用了update函数。用于确定KCP的初始化状态。
     */
    private boolean updated;
    /**
     * 死链检测，表示经过多少次未收到ACK后认为连接断开。
     */
    private int deadLink;
    /**
     * 可增加的字节数，用于控制拥塞窗口的增长。
     */
    private int incr;

    //其他
    /**
     * 缓冲区，通常用于临时存储需要发送的数据。
     */
    private ByteBuffer buffer;
    /**
     * 快速重传参数，当某个包被跳过多次ACK时会触发快速重传。
     */
    private int fastResend;
    /**
     * 限制快速重传的最大次数，超过此次数的包不会继续快速重传。
     */
    private int fastLimit;
    /**
     * 是否禁用拥塞窗口控制，禁用后发送速度只受限于接收窗口。
     */
    private boolean isNoCrowdedWindow;
    /**
     * 流模式控制，决定是否按顺序处理数据。
     */
    private boolean isStream;
    /**
     * 日志掩码，用于控制日志输出的类别。
     */
    private int logMask;

    /**
     * 用户数据
     */
    private Object user;
    /**
     * 用户回调方法
     */
    private IKCPContext IKCPContext;

    /**
     *
     * @param conversationId 会话ID
     * @param user 用户数据
     * @param IKCPContext 用户回调方法
     */
    public KCPContext(int conversationId, Object user, IKCPContext IKCPContext) {
        this.conversationId = conversationId;
        this.user = user;
        this.IKCPContext = IKCPContext;

        this.sendQueue = new LinkedList<>();
        this.receiveQueue = new LinkedList<>();
        this.sendBuff = new LinkedList<>();
        this.receiveBuff = new LinkedList<>();
        this.ackList = new ArrayList<>();
    }

    /**
     * 释放数据
     */
    public void release() {
        this.sendQueue.clear();
        this.receiveQueue.clear();
        this.sendBuff.clear();
        this.receiveBuff.clear();
        this.ackList.clear();
        this.buffer = null;
    }

    /**
     * 将数据发送到KCP的发送队列中
     *
     * @param buffer 发送的数据缓冲区
     * @param length 要发送的数据长度
     * @return 发送的数据长度，小于0表示返回错误
     */
    public int send(ByteBuffer buffer, int length) {

        assert (this.MSS > 0);
        if (length <= 0) {
            throw new KCPInvalidDataLengthException("length <= 0",this);
        }

        int sent = 0;
        //如果为流式数据,尝试补满数据
        if (this.isStream) {
            if (!this.sendQueue.isEmpty()) {
                KCPSegment old = this.sendQueue.getLast();
                if (old.getLength() < this.MSS) {
                    int capacity = this.MSS - old.getLength();
                    int extend = Math.min(length, capacity);
                    KCPSegment segment = new KCPSegment(old.getLength() + extend);

                    System.arraycopy(old.getData(), 0, segment.getData(), 0, old.getLength());
                    buffer.get(segment.getData(), old.getLength(), extend);

                    segment.setFragmentId(0);
                    length -= extend;
                    this.sendQueue.removeLast();
                    this.sendQueue.add(segment);
                    sent = extend;
                }
            }
            if (length <= 0) {
                return sent;
            }
        }

        //计算分片拆分数量
        int count;
        if (length <= this.MSS) {
            count = 1;
        } else {
            count = (length + this.MSS - 1) / this.MSS;
        }
        if (count >= KCPUtils.KCP_WND_RCV) {
            if (this.isStream && sent > 0) {
                return sent;
            } else {
                throw new KCPOverReceiveWindowException("segment count over receive window",this);
            }
        }
        if (count == 0) {
            count = 1;
        }

        //根据拆分数量初始化分片数据
        for (int i = 0; i < count; i++) {
            int size = Math.min(length, this.MSS);
            KCPSegment segment = new KCPSegment(size);
            if (length > 0) {
                buffer.get(segment.getData(), 0, size);
            }
            segment.setFragmentId(!this.isStream ? (count - i - 1) : 0);
            this.sendQueue.add(segment);
            length -= size;
            sent += size;
        }
        return sent;
    }

    /**
     * 接收数据
     *
     * @param buffer 存储接收到数据的缓冲区
     * @param length 接收缓冲区的长度。如果 length < 0，表示是"peek"操作，即不移除队列中的数据，只是预览。
     * @return 接受数据大小，小于0表示返回错误
     */
    public int receive(ByteBuffer buffer, int length) {
        if (this.receiveQueue.isEmpty()) {
            return KCPUtils.KCP_ERROR_NO_DATA;
        }

        boolean isPeek = length < 0;

        if (length < 0) {
            length = -length;
        }

        //检测下一个分片数据大小跟接收缓冲区大小
        int nextDataLength = getNextSegmentDataLength();
        if (nextDataLength < KCPUtils.KCP_OPERATION_SUCCESS) {
            return nextDataLength;
        }

        if (nextDataLength > length) {
            throw new KCPBufferLengthIsNotEnoughException("the buffer length is not enough",this);
        }

        //是否需要通知远端当前接收窗口大小,当receiveQueue大小从大于接收窗口变为小于接收窗口时通知。
        boolean recover = this.receiveQueue.size() >= this.receiveWindow;

        //拼接分片数据
        int dataLength = 0;
        Iterator<KCPSegment> iterator = this.receiveQueue.iterator();
        while (iterator.hasNext()) {
            KCPSegment segment = iterator.next();
            buffer.put(segment.getData(), 0, segment.getLength());
            dataLength += segment.getLength();

            if (canLog(KCPUtils.KCP_LOG_REC)) {
                writeLog(KCPUtils.KCP_LOG_REC, "receive sn=%d", segment.getSegmentId());
            }

            if (!isPeek) {
                iterator.remove();
            }

            if (segment.getFragmentId() == KCPUtils.KCP_FINAL_FRAGMENT_ID) {
                break;
            }
        }
        assert (dataLength == nextDataLength);

        // 移动下一个分表数据 receiveBuff -> receiveQueue.根据接收窗口大小，可能只会移动下一个分片的部分数据
        Iterator<KCPSegment> bufferIterator = this.receiveBuff.iterator();
        while (bufferIterator.hasNext()) {
            KCPSegment segment = bufferIterator.next();
            if (segment.getSegmentId() == this.nextReceiveSegmentId && this.receiveQueue.size() < this.receiveWindow) {
                bufferIterator.remove();
                this.receiveQueue.addLast(segment);
                this.nextReceiveSegmentId++;
            } else {
                break;
            }
        }

        // fast recover
        if (this.receiveQueue.size() < this.receiveWindow && recover) {
            // ready to send back KCP_CMD_WINS in flush
            // tell remote my window size
            this.probe |= KCPUtils.KCP_ASK_TELL;
        }

        return dataLength;
    }

    /**
     * @return 下一个完整消息的总长度
     */
    private int getNextSegmentDataLength() {
        KCPSegment segment = this.receiveQueue.getFirst();
        if (segment.getFragmentId() == KCPUtils.KCP_FINAL_FRAGMENT_ID) {
            return segment.getLength();
        }
        if (this.receiveQueue.size() < segment.getFragmentId() + 1) {
            throw new KCPReceiveQueueNextSegmentNotComplete("receive queue next segment is not complete",this,segment.getSegmentId());
        }

        int length = 0;
        for (KCPSegment kcpSegment : this.receiveQueue) {
            length += kcpSegment.getLength();
            if (kcpSegment.getFragmentId() == KCPUtils.KCP_FINAL_FRAGMENT_ID)
                break;
        }
        return length;
    }

    /**
     * 接收数据
     *
     * @param buffer 数据buff
     * @param length   接受数据长度
     * @return 返回操作是否成功，小于0代表返回错误
     */
    public int input(ByteBuffer buffer, int length) {

        if (length < KCPSegment.KCP_OVERHEAD) {
            throw new KCPBufferDataNotEnoughToReceiveException("the buffer length is not enough to receive",this);
        }

        if (canLog(KCPUtils.KCP_LOG_INPUT)) {
            writeLog(KCPUtils.KCP_LOG_INPUT, "[RI] %d bytes", length);
        }

        int prevUnacknowledgedSegmentId = this.sendUnacknowledgedSegmentId;
        int maxAckSegmentId = 0;
        long lastAckSegmentTimeStamp = 0;
        boolean needFastAck = false;

        //读取数据
        while (length >= KCPSegment.KCP_OVERHEAD) {

            int conversationId = buffer.getInt();
            if (conversationId != this.conversationId) {
                throw new KCPDataHeadWrongConversationIdException("data head has wrong conversation id",this);
            }

            int segmentId = buffer.getInt();
            int fragmentId = buffer.getInt();
            int commandId = buffer.getInt();
            if (commandId != KCPUtils.KCP_CMD_PUSH && commandId != KCPUtils.KCP_CMD_ACK
                    && commandId != KCPUtils.KCP_CMD_WINDOW_ASK && commandId != KCPUtils.KCP_CMD_WINDOW_SIZE) {
                throw new KCPDataHeadWrongCommandIdException("data head has wrong command id",this);
            }

            long timeStamp = buffer.getLong();
            int unacknowledgedNumber = buffer.getInt();
            int windowSize = buffer.getInt();
            int segmentLength = buffer.getInt();
            length -= KCPSegment.KCP_OVERHEAD;
            if (length < segmentLength) {
                throw new KCPWrongDataException("data left length is less than segment length",this);
            }

            this.remoteWindow = windowSize;
            this.removeSegmentFromSendBuffByUnacknowledgedSegmentId(unacknowledgedNumber);
            this.updateSendUnacknowledgedSegmentId();

            switch (commandId) {
                case KCPUtils.KCP_CMD_ACK: {
                    if (this.current - timeStamp >= 0) {
                        updateRTTInfo((int) (this.current - timeStamp));
                    }
                    this.removeSegmentFromSendBuffByAcknowledgedSegmentId(segmentId);
                    this.updateSendUnacknowledgedSegmentId();
                    if (!needFastAck) {
                        needFastAck = true;
                        maxAckSegmentId = segmentId;
                        lastAckSegmentTimeStamp = timeStamp;
                    } else {
                        if (segmentId - maxAckSegmentId > 0) {
                            if (!fastAckConserve) {
                                maxAckSegmentId = segmentId;
                                lastAckSegmentTimeStamp = timeStamp;
                            }
                            if (timeStamp - lastAckSegmentTimeStamp > 0) {
                                maxAckSegmentId = segmentId;
                                lastAckSegmentTimeStamp = timeStamp;
                            }
                        }
                    }
                    if (canLog(KCPUtils.KCP_LOG_IN_ACK)) {
                        writeLog(KCPUtils.KCP_LOG_IN_ACK, "input ack: sn=%d rtt=%d rto=%d", segmentId, (this.current - timeStamp), this.currentRTO);
                    }
                    break;
                }
                case KCPUtils.KCP_CMD_PUSH: {
                    if (canLog(KCPUtils.KCP_LOG_IN_DATA)) {
                        writeLog(KCPUtils.KCP_LOG_IN_DATA, "input psh: sn=%d ts=%d", segmentId, timeStamp);
                    }
                    byte[] data = new byte[segmentLength];
                    buffer.get(data, 0, segmentLength);
                    //接收的segment没有超过receive window
                    if (segmentId - (this.nextReceiveSegmentId + this.receiveWindow) < 0) {
                        this.pushAcknowledgedInfo(segmentId, timeStamp);
                        //接收的segment比需要接收的segmentId大
                        if (segmentId - this.nextReceiveSegmentId >= 0) {
                            KCPSegment segment = new KCPSegment(data);
                            segment.setConversationId(conversationId);
                            segment.setCommandId(commandId);
                            segment.setFragmentId(fragmentId);
                            segment.setWindowSize(windowSize);
                            segment.setTimeStamp(timeStamp);
                            segment.setSegmentId(segmentId);
                            segment.setUnacknowledgedSegmentId(unacknowledgedNumber);
                            //向receive queue中插入数据
                            this.insertSegment(segment);
                        }
                    }
                    break;
                }
                case KCPUtils.KCP_CMD_WINDOW_ASK: {
                    this.probe |= KCPUtils.KCP_ASK_TELL;
                    if (canLog(KCPUtils.KCP_LOG_IN_PROBE)) {
                        writeLog(KCPUtils.KCP_LOG_IN_PROBE, "input probe");
                    }
                    break;
                }
                case KCPUtils.KCP_CMD_WINDOW_SIZE: {
                    if (canLog(KCPUtils.KCP_LOG_IN_WINS)) {
                        writeLog(KCPUtils.KCP_LOG_IN_WINS, "input wins: %d", windowSize);
                    }
                    break;
                }
                default:
                    throw new KCPDataHeadWrongCommandIdException("data head has wrong command id",this);
            }
            length -= segmentLength;
        }

        if (needFastAck) {
            updateFastAcknowledgedCount(maxAckSegmentId, lastAckSegmentTimeStamp);
        }

        //拥塞控制,更新拥挤窗口大小。
        if (this.sendUnacknowledgedSegmentId - prevUnacknowledgedSegmentId > 0) {
            if (this.crowdedWindow < this.remoteWindow) {
                int mss = this.MSS;
                //慢启动阶段
                if (this.crowdedWindow < this.slowStartThresh) {
                    this.crowdedWindow++;
                    this.incr += mss;
                } else {
                    //拥塞避免阶段
                    if (this.incr < mss) {
                        this.incr = mss;
                    }
                    //拥塞窗口以渐进的方式增大
                    this.incr = this.incr + ((mss * mss) / this.incr + (mss / 16));
                    //增量足够大，crowdedWindow 会相应增加
                    if ((this.crowdedWindow + 1) * mss <= this.incr) {
                        this.crowdedWindow = (this.incr + mss - 1) / ((mss > 0) ? mss : 1);
                    }
                }
                if (this.crowdedWindow > this.remoteWindow) {
                    this.crowdedWindow = this.remoteWindow;
                    this.incr = this.remoteWindow * mss;
                }
            }
        }
        return KCPUtils.KCP_OPERATION_SUCCESS;
    }

    /**
     * 根据未确认Id移除已确认的 segment
     *
     * @param unacknowledgedSegmentId 未确认Id
     */
    private void removeSegmentFromSendBuffByUnacknowledgedSegmentId(int unacknowledgedSegmentId) {
        Iterator<KCPSegment> it = this.sendBuff.iterator();
        while (it.hasNext()) {
            KCPSegment seg = it.next();
            if (unacknowledgedSegmentId - seg.getSegmentId() > 0) {
                it.remove();
            } else {
                break;
            }
        }
    }

    /**
     * 更新发送窗口内的最小未确认序列号
     */
    private void updateSendUnacknowledgedSegmentId() {
        if (!sendBuff.isEmpty()) {
            KCPSegment segment = this.sendBuff.getFirst();
            this.sendUnacknowledgedSegmentId = segment.getSegmentId();
        } else {
            this.sendUnacknowledgedSegmentId = this.nextSendSegmentId;
        }
    }

    /**
     * 根据新的 RTT 更新平滑 RTT、RTT 偏差和重传超时 (RTO) 值。通过平滑算法，它能很好地跟踪网络状态的变化并相应调整 RTO，这样可以提高协议的适应性，减少不必要的重传或重传超时导致的性能下降。
     *
     * @param rtt 消息来回花费时间
     */
    private void updateRTTInfo(int rtt) {
        int rto;
        // 如果平滑RTT (rx_sRtt) 是 0，直接设置为当前的RTT
        if (this.smoothRtti == 0) {
            this.smoothRtti = rtt;
            this.rttVal = rtt / 2;
        } else {
            // 计算新RTT与当前平滑RTT的差值
            int delta = (int) (rtt - this.smoothRtti);
            if (delta < 0) {
                delta = -delta;
            }
            // 更新RTT偏差 (rx_rttVal) 的平滑值，使用加权移动平均法
            this.rttVal = (3 * this.rttVal + delta) / 4;
            // 更新平滑RTT (rx_sRtt)，使用加权移动平均法
            this.smoothRtti = (7 * this.smoothRtti + rtt) / 8;
            // 确保平滑RTT不小于1，避免超时计算过小
            if (this.smoothRtti < 1) {
                this.smoothRtti = 1;
            }
        }
        // 根据新的平滑RTT和偏差，计算新的RTO
        rto = (int) (this.smoothRtti + Math.max(this.interval, 4 * this.rttVal));
        // 将计算出来的RTO限制在最小和最大范围之间
        this.smoothRtti = KCPContext.clamp(rto, this.minRto, KCPUtils.KCP_RTO_MAX);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * 根据确认Id移除已确认的 segment
     *
     * @param acknowledgedSegmentId 确认Id
     */
    private void removeSegmentFromSendBuffByAcknowledgedSegmentId(int acknowledgedSegmentId) {
        if (acknowledgedSegmentId - this.sendUnacknowledgedSegmentId < 0 || acknowledgedSegmentId - this.nextSendSegmentId >= 0) {
            return;
        }
        Iterator<KCPSegment> it = this.sendBuff.iterator();
        while (it.hasNext()) {
            KCPSegment seg = it.next();
            if (seg.getSegmentId() == acknowledgedSegmentId) {
                it.remove();
                break;
            }
            if (acknowledgedSegmentId - seg.getSegmentId() < 0) {
                break;
            }
        }
    }

    /**
     * 推送acknowledge信息
     *
     * @param segmentId 分片Id
     * @param timeStamp 发送的时间戳
     */
    private void pushAcknowledgedInfo(int segmentId, long timeStamp) {
        KCPACKInfo item = new KCPACKInfo(segmentId, timeStamp);
        ackList.add(item);
    }

    /**
     * 插入数据
     *
     * @param segment 数据分片
     */
    private void insertSegment(KCPSegment segment) {
        int segmentId = segment.getSegmentId();
        boolean repeat = false;
        boolean find = false;
        //按照segmentId插入segment
        ListIterator<KCPSegment> it = this.receiveBuff.listIterator(this.receiveBuff.size());
        while (it.hasPrevious()) {
            KCPSegment tempSegment = it.previous();
            if (tempSegment.getSegmentId() == segmentId) {
                repeat = true;
                break;
            }
            if (segmentId - tempSegment.getSegmentId() > 0) {
                it.next();
                it.add(segment);
                find = true;
                break;
            }
        }
        if (!repeat && !find) {
            this.receiveBuff.addFirst(segment);
        }

        //移动receive buff到receive queue
        while (!this.receiveBuff.isEmpty()) {
            KCPSegment tempSegment = this.receiveBuff.getFirst();
            if (tempSegment.getSegmentId() == this.nextReceiveSegmentId && this.receiveQueue.size() < this.receiveWindow) {
                this.receiveBuff.removeFirst();
                this.receiveQueue.add(tempSegment);
                this.nextReceiveSegmentId++;
            }else {
                break;
            }
        }
    }

    /**
     * 设置快速重传
     *
     * @param segmentId 分片id
     * @param timeStamp 时间戳
     */
    private void updateFastAcknowledgedCount(long segmentId, long timeStamp) {
        if (segmentId - this.sendUnacknowledgedSegmentId < 0 || segmentId - this.nextSendSegmentId >= 0) {
            return;
        }
        for (KCPSegment segment : this.sendBuff) {
            if (segmentId - segment.getSegmentId() < 0) {
                break;
            } else if (segmentId != segment.getSegmentId()) {
                if (!fastAckConserve) {
                    segment.setFastAcknowledgedCount(segment.getFastAcknowledgedCount() + 1);
                }
                if (timeStamp - segment.getTimeStamp() >= 0) {
                    segment.setFastAcknowledgedCount(segment.getFastAcknowledgedCount() + 1);
                }
            }
        }
    }

    /**
     * 更新KCP状态（重复调用，每 10ms-100ms 调用一次），也可以询问检查何时再次调用（无 input/send 调用）。
     * @param current 当前毫秒时间
     */
    public void update(long current) {
        this.current = current;
        if (!this.updated) {
            this.updated = true;
            this.nextFlushTimeStamp = this.current;
        }

        long slap = this.current - this.nextFlushTimeStamp;
        if (slap >= KCPUtils.KCP_MAX_UPDATE_SLAP || slap < -KCPUtils.KCP_MAX_UPDATE_SLAP) {
            this.nextFlushTimeStamp = this.current;
            slap = 0;
        }
        if (slap >= 0) {
            this.nextFlushTimeStamp += this.interval;
            if (this.current - this.nextFlushTimeStamp >= 0) {
                this.nextFlushTimeStamp = this.current + this.interval;
            }
            flush();
        }
    }

    private void flush() {

        if (!this.updated)
            return;

        long current = this.current;

        // probe window size (if remote window size equals zero)
        if (this.remoteWindow == 0) {
            if (this.probeWait == 0) {
                this.probeWait = KCPUtils.KCP_PROBE_INIT;
                this.nextProbeTimeStamp = this.current + this.probeWait;
            } else {
                if (this.current - this.nextProbeTimeStamp >= 0) {
                    if (this.probeWait < KCPUtils.KCP_PROBE_INIT) {
                        this.probeWait = KCPUtils.KCP_PROBE_INIT;
                    }
                    this.probeWait += this.probeWait / 2;
                    if (this.probeWait > KCPUtils.KCP_PROBE_LIMIT) {
                        this.probeWait = KCPUtils.KCP_PROBE_LIMIT;
                    }
                    this.nextProbeTimeStamp = this.current + this.probeWait;
                    this.probe |= KCPUtils.KCP_ASK_SEND;
                }
            }
        } else {
            this.nextProbeTimeStamp = 0;
            this.probeWait = 0;
        }

        KCPSegment templateSegment = new KCPSegment(0);
        templateSegment.setConversationId(this.conversationId);
        templateSegment.setCommandId(KCPUtils.KCP_CMD_ACK);
        templateSegment.setFragmentId(KCPUtils.KCP_FINAL_FRAGMENT_ID);
        templateSegment.setWindowSize(this.getWindowUnused());
        templateSegment.setUnacknowledgedSegmentId(this.nextReceiveSegmentId);
        templateSegment.setSegmentId(0);
        templateSegment.setTimeStamp(0);

        // flush acknowledges
        flushAcknowledges(templateSegment);

        // flush window probing commands
        if ((this.probe & KCPUtils.KCP_ASK_SEND) != 0) {
            flushWindowProbeCommand(KCPUtils.KCP_CMD_WINDOW_ASK,templateSegment);
        }
        if ((this.probe & KCPUtils.KCP_ASK_TELL) != 0) {
            flushWindowProbeCommand(KCPUtils.KCP_CMD_WINDOW_SIZE,templateSegment);
        }
        this.probe = 0;

        // calculate window size
        int windowSize = Math.min(this.sendWindow, this.remoteWindow);
        if (!this.isNoCrowdedWindow) {
            windowSize = Math.min(this.crowdedWindow, windowSize);
        }

        // move data from sendQueue to sendBuff
        while (this.nextSendSegmentId - (this.sendUnacknowledgedSegmentId + windowSize) < 0) {
            if (this.sendQueue.isEmpty()) {
                break;
            }
            KCPSegment segment = this.sendQueue.removeFirst();
            segment.setConversationId(this.conversationId);
            segment.setCommandId(KCPUtils.KCP_CMD_PUSH);
            segment.setWindowSize(templateSegment.getWindowSize());
            segment.setTimeStamp(current);
            segment.setSegmentId(this.nextSendSegmentId);
            segment.setUnacknowledgedSegmentId(this.nextReceiveSegmentId);
            segment.setResendTimeStamp(current);
            segment.setRTO(this.currentRTO);
            segment.setFastAcknowledgedCount(0);
            segment.setSendCount(0);
            this.sendBuff.add(segment);

            this.nextSendSegmentId++;
        }

        //calculate resent
        int resent = this.fastResend > 0 ? this.fastResend : -1;
        int rtoMin = (!this.noDelay) ? (this.currentRTO >> 3) : 0;

        // flush data segments
        boolean change = false;
        boolean lost = false;
        for (KCPSegment segment : this.sendBuff) {
            boolean needSend = false;
            if (segment.getSendCount() == 0) {
                needSend = true;
                segment.setSendCount(segment.getSendCount() + 1);
                segment.setRTO(this.currentRTO);
                segment.setResendTimeStamp(current + segment.getRTO() + rtoMin);
            } else if (current - segment.getResendTimeStamp() >= 0) {
                //根据resendTimeStamp重发数据，根据noDelay确定下一次重发的时间
                needSend = true;
                segment.setSendCount(segment.getSendCount() + 1);
                if (!this.noDelay) {
                    segment.setRTO(segment.getRTO() + Math.max(segment.getRTO(), this.currentRTO));
                } else {
                    segment.setRTO(segment.getRTO() + this.currentRTO / 2);
                }
                segment.setResendTimeStamp(current + segment.getRTO());
                lost = true;
            } else if (segment.getFastAcknowledgedCount() >= resent) {
                //快速重传，不考虑resentTimeStamp
                if (segment.getSendCount() <= this.fastLimit || this.fastLimit <= 0) {
                    needSend = true;
                    segment.setSendCount(segment.getSendCount() + 1);
                    segment.setFastAcknowledgedCount(0);
                    segment.setResendTimeStamp(current + segment.getRTO());
                    change = true;
                }
            }

            if (needSend) {
                segment.setTimeStamp(current);
                segment.setWindowSize(templateSegment.getWindowSize());
                segment.setUnacknowledgedSegmentId(this.nextReceiveSegmentId);

                int size = buffer.position();
                int need = KCPSegment.KCP_OVERHEAD + segment.getLength();

                if (size + need > this.MTU) {
                    buffer.flip();
                    this.output(buffer, size);
                    buffer.clear();
                }
                segment.encodeHead(buffer);

                if (segment.getLength() > 0) {
                    segment.encodeData(buffer);
                }

                if (segment.getSendCount() >= this.deadLink) {
                    this.state = -1;
                }
            }
        }

        // flash remain segments
        int size = buffer.position();
        if (size > 0) {
            buffer.flip();
            output(buffer, size);
            buffer.clear();
        }

        // update slowStartThresh
        if (change) {
            int inflight = this.nextSendSegmentId - this.sendUnacknowledgedSegmentId;
            this.slowStartThresh = inflight / 2;
            if (this.slowStartThresh < KCPUtils.KCP_THRESH_MIN) {
                this.slowStartThresh = KCPUtils.KCP_THRESH_MIN;
            }
            this.crowdedWindow = this.slowStartThresh + resent;
            this.incr = this.crowdedWindow * this.MSS;
        }
        if (lost) {
            this.slowStartThresh = windowSize / 2;
            if (this.slowStartThresh < KCPUtils.KCP_THRESH_MIN) {
                this.slowStartThresh = KCPUtils.KCP_THRESH_MIN;
            }
            this.crowdedWindow = 1;
            this.incr = this.MSS;
        }
        if (this.crowdedWindow < 1) {
            this.crowdedWindow = 1;
            this.incr = this.MSS;
        }
    }

    private void flushWindowProbeCommand(int commandId, KCPSegment templateSegment) {
        templateSegment.setCommandId(commandId);
        int size = buffer.position();
        if (size + KCPSegment.KCP_OVERHEAD > this.MTU) {
            buffer.flip();
            this.output(buffer, size);
            buffer.clear();
        }
        templateSegment.setSegmentId(0);
        templateSegment.setTimeStamp(0);
        templateSegment.encodeHead(buffer);
    }

    /**
     * flush acknowledges
     * @param templateSegment 分片数据模板
     */
    private void flushAcknowledges(KCPSegment templateSegment) {
        for (KCPACKInfo kcpackInfo : this.ackList) {
            int size = buffer.position();
            if (size + KCPSegment.KCP_OVERHEAD > this.MTU) {
                buffer.flip();
                this.output(buffer, size);
                buffer.clear();
            }
            templateSegment.setSegmentId(kcpackInfo.getSegmentId());
            templateSegment.setTimeStamp(kcpackInfo.getTimeStamp());
            templateSegment.encodeHead(buffer);
        }
        this.ackList.clear();
    }

    /**
     * 获得剩余窗口大小
     *
     * @return 剩余窗口大小
     */
    private int getWindowUnused() {
        if (this.receiveQueue.size() < this.receiveWindow) {
            return this.receiveWindow - this.receiveQueue.size();
        }
        return 0;
    }

    /**
     * 将数据传递给上层逻辑
     *
     * @param buffer 数据buffer
     * @param size   数据大小
     */
    private void output(ByteBuffer buffer, int size) {
        byte[] data = new byte[size];
        buffer.get(data);

        if (canLog(KCPUtils.KCP_LOG_OUTPUT)) {
            this.writeLog(KCPUtils.KCP_LOG_OUTPUT, "[RO] user:%d size:%d", this.user, size);
        }
        if (size == 0) {
            return;
        }
        if (this.IKCPContext != null) {
            IKCPContext.output(data, size, this, user);
        }
    }

    /**
     * 确定何时应该调用 update：如果没有 input/send 调用，何时应该在调用 update。您可以在该时间内调用 Update，而不是重复调用 Update。
     * 减少不必要的更新调用非常重要。使用它来安排更新（例如，实现类似 epoll 的机制，或在处理大量 KCP 连接时优化更新）
     * @param current 当前时间
     * @return 下一次更新时间
     */
    public long check(long current) {
        long flushTimeStamp = this.nextFlushTimeStamp;
        long nextSendSegmentWaitTime = Integer.MAX_VALUE;

        if (!this.updated) {
            return current;
        }

        if (current - flushTimeStamp >= KCPUtils.KCP_MAX_UPDATE_SLAP || current - flushTimeStamp < -KCPUtils.KCP_MAX_UPDATE_SLAP) {
            flushTimeStamp = current;
        }

        if (current - flushTimeStamp >= 0) {
            return current;
        }

        long nextFlushWaitTime = flushTimeStamp - current;

        for (KCPSegment segment : this.sendBuff) {
            long diff = segment.getResendTimeStamp() - current;
            if (diff <= 0) {
                return current;
            }
            if (diff < nextSendSegmentWaitTime) {
                nextSendSegmentWaitTime = diff;
            }
        }

        long minimal = Math.min(nextSendSegmentWaitTime, nextFlushWaitTime);
        if (minimal >= this.interval) {
            minimal = this.interval;
        }

        return current + minimal;
    }

    /**
     * 更新flush时间间隔
     * @param interval 时间间隔
     */
    public void setInterval(int interval) {
        if (interval > KCPUtils.KCP_MAX_UPDATE_INTERVAL) {
            interval = KCPUtils.KCP_MAX_UPDATE_INTERVAL;
        } else if (interval < KCPUtils.KCP_MIN_UPDATE_INTERVAL) {
            interval = KCPUtils.KCP_MIN_UPDATE_INTERVAL;
        }
        this.interval = interval;
    }

    /**
     * 设置mtu
     *
     * @param mtu 新mtu值
     */
    public void setMTU(int mtu) {
        if (mtu < Math.min(KCPUtils.KCP_MIN_MTU,KCPSegment.KCP_OVERHEAD)) {
            throw new KCPInvalidMTUException("new mtu is invalid",this);
        }
        ByteBuffer buffer = ByteBuffer.allocate((mtu + KCPSegment.KCP_OVERHEAD) * 3);
        this.MTU = mtu;
        this.MSS = this.MTU - KCPSegment.KCP_OVERHEAD;
        this.buffer = buffer;
    }

    /**
     * 设置窗口大小
     * @param sendWindow 发送窗口
     * @param receiveWindow 接收窗口
     */
    public void setWindowSize(int sendWindow, int receiveWindow) {
        if (sendWindow > 0) {
            this.sendWindow = sendWindow;
        }
        if (receiveWindow > 0) {
            this.receiveWindow = Math.max(receiveWindow, KCPUtils.KCP_WND_RCV);
        }
    }

    /**
     * 设置kcp阻塞控制等参数
     * @param noDelay 启用以后若干常规加速将启动
     * @param interval 内部处理时钟
     * @param resend 快速重传指标
     * @param isNoCrowdedWindow 为是否禁用常规流控
     */
    public void setNoDelay(boolean noDelay, int interval, int resend, boolean isNoCrowdedWindow) {
        this.noDelay = noDelay;
        if (noDelay) {
            this.minRto = KCPUtils.KCP_RTO_NO_DELAY_MIN;
        } else {
            this.minRto = KCPUtils.KCP_RTO_MIN;
        }
        if (interval >= 0) {
            setInterval(interval);
        }
        if (resend >= 0) {
            this.fastResend = resend;
        }
        this.isNoCrowdedWindow = isNoCrowdedWindow;
    }

    /**
     * 是否可以写日志
     * @param mask 日志掩码
     * @return 是否
     */
    private boolean canLog(int mask) {
        if ((mask & this.logMask) == 0) {
            return false;
        }
        return this.IKCPContext != null;
    }

    /**
     * 写日志
     * @param mask 日志类型掩码
     * @param fmt 日志输出格式
     * @param args 日志参数
     */
    private void writeLog(int mask, String fmt, Object... args) {
        if ((mask & this.logMask) == 0) {
            return;
        }
        String message = String.format(fmt, args);
        if (this.IKCPContext != null) {
            this.IKCPContext.writeLog(message, this, user);
        }
    }

    public void setIKCPContext(IKCPContext IKCPContext) {
        this.IKCPContext = IKCPContext;
    }

    public int getWaitSend() {
        return this.sendBuff.size() + this.sendQueue.size();
    }

    public boolean isFastAckConserve() {
        return fastAckConserve;
    }

    public void setFastAckConserve(boolean fastAckConserve) {
        this.fastAckConserve = fastAckConserve;
    }

    public int getConversationId() {
        return conversationId;
    }

    public void setConversationId(int conversationId) {
        this.conversationId = conversationId;
    }

    public int getMTU() {
        return MTU;
    }

    public int getMSS() {
        return MSS;
    }

    public void setMSS(int MSS) {
        this.MSS = MSS;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public int getSendUnacknowledgedSegmentId() {
        return sendUnacknowledgedSegmentId;
    }

    public void setSendUnacknowledgedSegmentId(int sendUnacknowledgedSegmentId) {
        this.sendUnacknowledgedSegmentId = sendUnacknowledgedSegmentId;
    }

    public int getNextSendSegmentId() {
        return nextSendSegmentId;
    }

    public void setNextSendSegmentId(int nextSendSegmentId) {
        this.nextSendSegmentId = nextSendSegmentId;
    }

    public int getNextReceiveSegmentId() {
        return nextReceiveSegmentId;
    }

    public void setNextReceiveSegmentId(int nextReceiveSegmentId) {
        this.nextReceiveSegmentId = nextReceiveSegmentId;
    }

    public long getLastSendTimeStamp() {
        return lastSendTimeStamp;
    }

    public void setLastSendTimeStamp(int lastSendTimeStamp) {
        this.lastSendTimeStamp = lastSendTimeStamp;
    }

    public long getLastACKTimeStamp() {
        return lastACKTimeStamp;
    }

    public void setLastACKTimeStamp(int lastACKTimeStamp) {
        this.lastACKTimeStamp = lastACKTimeStamp;
    }

    public int getSlowStartThresh() {
        return slowStartThresh;
    }

    public void setSlowStartThresh(int slowStartThresh) {
        this.slowStartThresh = slowStartThresh;
    }

    public int getRttVal() {
        return rttVal;
    }

    public void setRttVal(int rttVal) {
        this.rttVal = rttVal;
    }

    public int getSmoothRtti() {
        return smoothRtti;
    }

    public void setSmoothRtti(int smoothRtti) {
        this.smoothRtti = smoothRtti;
    }

    public int getCurrentRTO() {
        return currentRTO;
    }

    public void setCurrentRTO(int currentRTO) {
        this.currentRTO = currentRTO;
    }

    public int getMinRto() {
        return minRto;
    }

    public void setMinRto(int minRto) {
        this.minRto = minRto;
    }

    public int getSendWindow() {
        return sendWindow;
    }

    public int getReceiveWindow() {
        return receiveWindow;
    }

    public int getRemoteWindow() {
        return remoteWindow;
    }

    public void setRemoteWindow(int remoteWindow) {
        this.remoteWindow = remoteWindow;
    }

    public int getCrowdedWindow() {
        return crowdedWindow;
    }

    public void setCrowdedWindow(int crowdedWindow) {
        this.crowdedWindow = crowdedWindow;
    }

    public int getProbe() {
        return probe;
    }

    public void setProbe(int probe) {
        this.probe = probe;
    }

    public long getCurrent() {
        return current;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getInterval() {
        return interval;
    }

    public long getNextFlushTimeStamp() {
        return nextFlushTimeStamp;
    }

    public void setNextFlushTimeStamp(long nextFlushTimeStamp) {
        this.nextFlushTimeStamp = nextFlushTimeStamp;
    }

    public boolean getNoDelay() {
        return noDelay;
    }

    public void setNoDelay(boolean noDelay) {
        this.noDelay = noDelay;
    }

    public boolean getUpdated() {
        return updated;
    }

    public void setUpdated(boolean updated) {
        this.updated = updated;
    }

    public long getNextProbeTimeStamp() {
        return nextProbeTimeStamp;
    }

    public void setNextProbeTimeStamp(long nextProbeTimeStamp) {
        this.nextProbeTimeStamp = nextProbeTimeStamp;
    }

    public int getProbeWait() {
        return probeWait;
    }

    public void setProbeWait(int probeWait) {
        this.probeWait = probeWait;
    }

    public int getDeadLink() {
        return deadLink;
    }

    public void setDeadLink(int deadLink) {
        this.deadLink = deadLink;
    }

    public int getIncr() {
        return incr;
    }

    public void setIncr(int incr) {
        this.incr = incr;
    }

    public List<KCPACKInfo> getAckList() {
        return ackList;
    }

    public void setAckList(List<KCPACKInfo> ackList) {
        this.ackList = ackList;
    }

    public LinkedList<KCPSegment> getSendQueue() {
        return sendQueue;
    }

    public void setSendQueue(LinkedList<KCPSegment> sendQueue) {
        this.sendQueue = sendQueue;
    }

    public LinkedList<KCPSegment> getReceiveQueue() {
        return receiveQueue;
    }

    public void setReceiveQueue(LinkedList<KCPSegment> receiveQueue) {
        this.receiveQueue = receiveQueue;
    }

    public LinkedList<KCPSegment> getSendBuff() {
        return sendBuff;
    }

    public void setSendBuff(LinkedList<KCPSegment> sendBuff) {
        this.sendBuff = sendBuff;
    }

    public LinkedList<KCPSegment> getReceiveBuff() {
        return receiveBuff;
    }

    public void setReceiveBuff(LinkedList<KCPSegment> receiveBuff) {
        this.receiveBuff = receiveBuff;
    }

    public Object getUser() {
        return user;
    }

    public void setUser(Object user) {
        this.user = user;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public int getFastResend() {
        return fastResend;
    }

    public void setFastResend(int fastResend) {
        this.fastResend = fastResend;
    }

    public int getFastLimit() {
        return fastLimit;
    }

    public void setFastLimit(int fastLimit) {
        this.fastLimit = fastLimit;
    }

    public boolean getIsNoCrowdedWindow() {
        return isNoCrowdedWindow;
    }

    public void setIsNoCrowdedWindow(boolean isNoCrowdedWindow) {
        this.isNoCrowdedWindow = isNoCrowdedWindow;
    }

    public boolean getIsStream() {
        return isStream;
    }

    public void setIsStream(boolean stream) {
        this.isStream = stream;
    }

    public int getLogMask() {
        return logMask;
    }

    public void setLogMask(int logMask) {
        this.logMask = logMask;
    }

    public IKCPContext getIKCPContext() {
        return IKCPContext;
    }
}
