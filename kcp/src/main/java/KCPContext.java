import java.nio.ByteBuffer;
import java.util.*;

public class KCPContext {

    private boolean fastAckConserve = true;

    //基本参数
    private int conversationId;//会话ID
    private int MTU;//最大传输单元
    private int MSS;//最大分片大小
    private int state;//当前状态

    //序号和窗口管理
    private int sendUnacknowledgedSegmentId;//未确认的最小发送序号
    private int nextSendSegmentId;//下一个要发送的数据序号
    private int nextReceiveSegmentId;//下一个期望接收的数据序号
    private int sendWindow;//发送窗口大小，用于控制发送方能发送多少未确认的包。
    private int receiveWindow;//接收窗口大小，表示接收方的缓冲区剩余空间
    private int remoteWindow;//远端窗口大小，即对端能接收的窗口大小。
    private int crowdedWindow;//拥塞窗口大小，用于控制拥塞避免算法。

    //计时和重传管理
    private long lastSendTimeStamp;//最近一次发送的时间戳
    private long lastACKTimeStamp;//最近一次接收到的ACK包的时间戳
    private int slowStartThresh;//慢启动阈值，用于拥塞控制
    private long rttVal;//RTT波动值（Round Trip Time Variation），用于计算RTO（重传超时）
    private long smoothRtti;//平滑的RTT（Smoothed RTT），表示RTT的均值。
    private int currentRTO;//当前的重传超时时间（Retransmission Timeout）。
    private int minRto;//最小重传超时时间，表示RTO的下限。
    private long nextFlushTimeStamp;//定时刷新时间戳，用于周期性发送数据。

    //定时器和探测
    private long current;//当前时间戳
    private int interval;//定时器触发间隔，用于刷新和检查是否需要发送包。
    private long nextProbeTimeStamp;//下一次探测时间戳，用于检测对端窗口大小。
    private int probeWait;//探测等待时间，当远端窗口为0时，发送探测包的间隔时间。
    private int probe;//探测标志位，表示是否需要发送窗口探测包。

    //统计信息
    private int sendCount;//发送数据包的总次数。

    //控制和策略
    private int noDelay;//是否启用无延迟模式。无延迟模式下，发送更快但可能导致网络波动。
    private boolean updated;//是否已经调用了update函数。用于确定KCP的初始化状态。
    private int dead_link;//死链检测，表示经过多少次未收到ACK后认为连接断开。
    private int incr;//可增加的字节数，用于控制拥塞窗口的增长。

    //队列和缓冲区
    private LinkedList<KCPSegment> sendQueue;//发送队列，存储还未发送的Segment。
    private LinkedList<KCPSegment> receiveQueue;//接收队列，存储已接收但未处理的Segment。
    private LinkedList<KCPSegment> sendBuff;//发送缓冲区，存储已发送但未确认的Segment。
    private LinkedList<KCPSegment> receiveBuff;//接收缓冲区，存储乱序接收到的Segment。

    //ACK管理
    private List<KCPACKInfo> ackList;//保存待发送的ACK序号和时间戳的列表。
    private int ackCount;//待发送的ACK数量。

    //其他
    private Object user;//用户数据
    private ByteBuffer buffer;//缓冲区，通常用于临时存储需要发送的数据。
    private int fastResend;//快速重传参数，当某个包被跳过多次ACK时会触发快速重传。
    private int fastLimit;//限制快速重传的最大次数，超过此次数的包不会继续快速重传。
    private boolean isNoCrowdedWindow;//是否禁用拥塞窗口控制，禁用后发送速度只受限于接收窗口。
    private int stream;//流模式控制，决定是否按顺序处理数据。
    private int logMask;//日志掩码，用于控制日志输出的类别。

    private IKCPContext IKCPContext;//回调方法


    public static KCPContext buildTestObject(int conversionId, Object user) {
        KCPContext context = new KCPContext();
        context.conversationId = conversionId;
        context.user = user;

        context.sendUnacknowledgedSegmentId = 0;
        context.nextSendSegmentId = 0;
        context.nextReceiveSegmentId = 0;
        context.lastSendTimeStamp = 0;
        context.lastACKTimeStamp = 0;
        context.nextProbeTimeStamp = 0;
        context.probeWait = 0;
        context.sendWindow = KCPUtils.KCP_WND_SND;
        context.receiveWindow = KCPUtils.KCP_WND_RCV;
        context.remoteWindow = KCPUtils.KCP_WND_RCV;
        context.crowdedWindow = 0;
        context.incr = 0;
        context.probe = 0;
        context.MTU = KCPUtils.KCP_MTU_DEF;
        context.MSS = context.MTU - KCPUtils.KCP_OVERHEAD;
        context.stream = 0;

        context.buffer = ByteBuffer.allocate((context.MTU + KCPUtils.KCP_OVERHEAD) * 3);

        context.sendQueue = new LinkedList<>();
        context.receiveQueue = new LinkedList<>();
        context.sendBuff = new LinkedList<>();
        context.receiveBuff = new LinkedList<>();
        context.state = 0;
        context.ackList = new ArrayList<>();
        context.ackCount = 0;
        context.smoothRtti = 0;
        context.rttVal = 0;
        context.currentRTO = KCPUtils.KCP_RTO_DEF;
        context.minRto = KCPUtils.KCP_RTO_MIN;
        context.current = 0;
        context.interval = KCPUtils.KCP_INTERVAL;
        context.nextFlushTimeStamp = KCPUtils.KCP_INTERVAL;
        context.noDelay = 0;
        context.updated = false;
        context.logMask = KCPUtils.KCP_LOG_ALL;
        context.slowStartThresh = KCPUtils.KCP_THRESH_INIT;
        context.fastResend = 0;
        context.fastLimit = KCPUtils.KCP_FAST_ACK_LIMIT;
        context.isNoCrowdedWindow = false;
        context.sendCount = 0;
        context.dead_link = KCPUtils.KCP_DEAD_LINK;
        return context;
    }

    public void release() {
        this.sendQueue.clear();
        this.receiveQueue.clear();
        this.sendBuff.clear();
        this.receiveBuff.clear();
        this.ackList.clear();
        this.buffer = null;
        this.ackCount = 0;
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

        int peekSize = getPeekSize();
        if (peekSize < KCPUtils.KCP_OPERATION_SUCCESS) {
            return peekSize;
        }
        if (peekSize > length) {
            return KCPUtils.KCP_ERROR_NOT_ENOUGH_BUFFER;
        }

        boolean recover = this.receiveQueue.size() >= this.receiveWindow;

        // merge fragment
        int peekLength = 0;
        Iterator<KCPSegment> iterator = this.receiveQueue.iterator();
        while (iterator.hasNext()) {
            KCPSegment segment = iterator.next();
            buffer.put(segment.getData(), 0, segment.getLength());
            peekLength += segment.getLength();

            if (canLog(KCPUtils.KCP_LOG_REC)) {
                writeLog(KCPUtils.KCP_LOG_REC, "recv sn=%d", segment.getSegmentId());
            }

            if (!isPeek) {
                iterator.remove();
            }

            if (segment.getFragmentId() == KCPUtils.KCP_FINAL_FRAGMENT_ID) {
                break;
            }
        }
        assert (peekLength == peekSize);

        // move available data from receiveBuff -> receiveQueue
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

        return peekLength;
    }

    /**
     * @return 下一个完整消息的总长度
     */
    private int getPeekSize() {
        if (this.receiveQueue.isEmpty()) {
            return KCPUtils.KCP_ERROR_NO_DATA;
        }
        KCPSegment segment = this.receiveQueue.getFirst();
        if (segment.getFragmentId() == KCPUtils.KCP_FINAL_FRAGMENT_ID) {
            return segment.getLength();
        }
        if (this.receiveQueue.size() < segment.getFragmentId() + 1) {
            return KCPUtils.KCP_ERROR_SEGMENT_NOT_COMPLETE;
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
     * 将数据分段并发送到 KCP 的发送队列中
     *
     * @param buffer 发送的数据缓冲区
     * @param length 要发送的数据长度
     * @return 发送的数据长度，小于0表示返回错误
     */
    public int send(ByteBuffer buffer, int length) {

        assert (this.MSS > 0);
        if (length <= 0) {
            return KCPUtils.KCP_ERROR_ARGUMENT_INVALID;
        }

        int sent = 0;
        // append to previous segment in streaming mode (if possible)
        if (this.stream != 0) {
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

        int count;
        if (length <= this.MSS) {
            count = 1;
        } else {
            count = (length + this.MSS - 1) / this.MSS;
        }
        if (count >= KCPUtils.KCP_WND_RCV) {
            if (this.stream != 0 && sent > 0) {
                return sent;
            } else {
                return KCPUtils.KCP_ERROR_OVER_RCV_WINDOW;
            }
        }
        if (count == 0) {
            count = 1;
        }

        // fragment
        for (int i = 0; i < count; i++) {
            int size = Math.min(length, this.MSS);
            KCPSegment segment = new KCPSegment(size);
            if (length > 0) {
                buffer.get(segment.getData(), 0, size);
            }
            segment.setFragmentId(this.stream == 0 ? (count - i - 1) : 0);
            this.sendQueue.add(segment);
            length -= size;
            sent += size;
        }
        return sent;
    }

    /**
     * 接收数据
     *
     * @param buffer 数据buff
     * @param size   接受数据长度
     * @return 返回操作是否成功，小于0代表返回错误
     */
    public int input(ByteBuffer buffer, int size) {
        long prevUnacknowledgedId = this.sendUnacknowledgedSegmentId;
        long maxAck = 0;
        long lastTimeStamp = 0;
        int flag = 0;

        if (canLog(KCPUtils.KCP_LOG_INPUT)) {
            writeLog(KCPUtils.KCP_LOG_INPUT, "[RI] %d bytes", size);
        }
        if (size < KCPUtils.KCP_OVERHEAD) {
            return KCPUtils.KCP_ERROR_DATA_SIZE_WRONG;
        }

        while (size >= KCPUtils.KCP_OVERHEAD) {

            int conversationId = buffer.getInt();
            if (conversationId != this.conversationId) {
                return KCPUtils.KCP_ERROR_CONVERSATION_WRONG;
            }

            int commandId = buffer.getInt();
            int fragmentId = buffer.getInt();
            int windowSize = buffer.getInt();
            long timeStamp = buffer.getLong();
            int segmentId = buffer.getInt();
            int unacknowledgedNumber = buffer.getInt();
            int length = buffer.getInt();

            size -= KCPUtils.KCP_OVERHEAD;
            if (size < length || length < 0) {
                return KCPUtils.KCP_ERROR_DATA_SIZE_WRONG;
            }
            if (commandId != KCPUtils.KCP_CMD_PUSH && commandId != KCPUtils.KCP_CMD_ACK
                    && commandId != KCPUtils.KCP_CMD_WINDOW_ASK && commandId != KCPUtils.KCP_CMD_WINS) {
                return KCPUtils.KCP_ERROR_CMD_WRONG;
            }

            this.remoteWindow = windowSize;
            this.parseUnacknowledgedNumber(unacknowledgedNumber);
            this.shrinkBuff();

            switch (commandId) {
                case KCPUtils.KCP_CMD_ACK: {
                    if (this.current - timeStamp >= 0) {
                        updateAck(this.current - timeStamp);
                    }
                    this.parseAck(segmentId);
                    this.shrinkBuff();
                    if (flag == 0) {
                        flag = 1;
                        maxAck = segmentId;
                        lastTimeStamp = timeStamp;
                    } else {
                        if (segmentId - maxAck > 0) {
                            if (!fastAckConserve) {
                                maxAck = segmentId;
                                lastTimeStamp = timeStamp;
                            }
                            if (timeStamp - lastTimeStamp > 0) {
                                maxAck = segmentId;
                                lastTimeStamp = timeStamp;
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
                    if (segmentId - (this.nextReceiveSegmentId + this.receiveWindow) < 0) {
                        this.pushAck(segmentId, timeStamp);
                        if (segmentId - this.nextReceiveSegmentId >= 0) {
                            KCPSegment segment = new KCPSegment(length);
                            segment.setConversationId(conversationId);
                            segment.setCommandId(commandId);
                            segment.setFragmentId(fragmentId);
                            segment.setWindowSize(windowSize);
                            segment.setTimeStamp(timeStamp);
                            segment.setSegmentId(segmentId);
                            segment.setUnacknowledgedSegmentId(unacknowledgedNumber);
                            if (length > 0) {
                                buffer.get(segment.getData(), 0, length);
                            }
                            this.parseData(segment);
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
                case KCPUtils.KCP_CMD_WINS: {
                    if (canLog(KCPUtils.KCP_LOG_IN_WINS)) {
                        writeLog(KCPUtils.KCP_LOG_IN_WINS, "input wins: %d", windowSize);
                    }
                    break;
                }
                default:
                    return KCPUtils.KCP_ERROR_CMD_WRONG;
            }
            size -= length;
        }

        if (flag != 0) {
            parseFastAck(maxAck, lastTimeStamp);
        }
        //拥塞控制
        if (this.sendUnacknowledgedSegmentId - prevUnacknowledgedId > 0) {
            if (this.crowdedWindow < this.remoteWindow) {
                int mss = this.MSS;
                if (this.crowdedWindow < this.slowStartThresh) {
                    this.crowdedWindow++;
                    this.incr += mss;
                } else {
                    if (this.incr < mss) {
                        this.incr = mss;
                    }
                    this.incr = this.incr + ((mss * mss) / this.incr + (mss / 16));
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
     * @param unacknowledgedNumber 未确认Id
     */
    private void parseUnacknowledgedNumber(long unacknowledgedNumber) {
        Iterator<KCPSegment> it = this.sendBuff.iterator();
        while (it.hasNext()) {
            KCPSegment seg = it.next();
            if (unacknowledgedNumber - seg.getSegmentId() > 0) {
                it.remove();
            } else {
                break;
            }
        }
    }

    /**
     * 更新发送窗口内的最小未确认序列号
     */
    private void shrinkBuff() {
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
    private void updateAck(long rtt) {
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
        this.smoothRtti = Math.clamp(rto, this.minRto, KCPUtils.KCP_RTO_MAX);
    }

    /**
     * 根据确认Id移除已确认的 segment
     *
     * @param segmentId 确认Id
     */
    private void parseAck(long segmentId) {
        if (segmentId - this.sendUnacknowledgedSegmentId < 0 || segmentId - this.nextSendSegmentId >= 0) {
            return;
        }
        Iterator<KCPSegment> it = this.sendBuff.iterator();
        while (it.hasNext()) {
            KCPSegment seg = it.next();
            if (seg.getSegmentId() == segmentId) {
                it.remove();
                break;
            }
            if (segmentId - seg.getSegmentId() < 0) {
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
    private void pushAck(int segmentId, long timeStamp) {
        KCPACKInfo item = new KCPACKInfo(segmentId, timeStamp);
        ackList.add(item);
    }

    /**
     * 装配数据
     *
     * @param segment 数据分片
     */
    private void parseData(KCPSegment segment) {
        int segmentId = segment.getSegmentId();
        boolean repeat = false;

        if (segmentId - (this.nextReceiveSegmentId + this.receiveWindow) >= 0 || segmentId - this.nextReceiveSegmentId < 0) {
            return;
        }

        ListIterator<KCPSegment> it = this.receiveBuff.listIterator(this.receiveBuff.size());
        while (it.hasPrevious()) {
            KCPSegment tempSegment = it.previous();
            if (tempSegment.getSegmentId() == segmentId) {
                repeat = true;
                break;
            }
            if (segmentId - tempSegment.getSegmentId() > 0) {
                break;
            }
        }

        if (!repeat) {
            it.add(segment);
        }

        while (!this.receiveBuff.isEmpty()) {
            KCPSegment tempSegment = this.receiveBuff.getFirst();
            if (tempSegment.getSegmentId() == this.nextReceiveSegmentId && this.receiveQueue.size() < this.receiveWindow) {
                this.receiveBuff.removeFirst();
                this.receiveQueue.add(tempSegment);
                this.nextReceiveSegmentId++;
            } else {
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
    private void parseFastAck(long segmentId, long timeStamp) {
        if (segmentId - this.sendUnacknowledgedSegmentId < 0 || segmentId - this.nextSendSegmentId >= 0) {
            return;
        }
        for (KCPSegment segment : this.sendBuff) {
            if (segmentId - segment.getSegmentId() < 0) {
                break;
            } else if (segmentId != segment.getSegmentId()) {
                if (!fastAckConserve) {
                    segment.setFastAck(segment.getFastAck() + 1);
                }
                if (timeStamp - segment.getTimeStamp() >= 0) {
                    segment.setFastAck(segment.getFastAck() + 1);
                }
            }
        }
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

    //---------------------------------------------------------------------
    // update state (call it repeatedly, every 10ms-100ms), or you can ask
    // check when to call it again (without input/_send calling).
    // 'current' - current timestamp in timeMillis.
    //---------------------------------------------------------------------
    public void update(long current) {
        long slap;
        this.current = current;
        if (!this.updated) {
            this.updated = true;
            this.nextFlushTimeStamp = this.current;
        }
        slap = this.current - this.nextFlushTimeStamp;
        if (slap >= 10000 || slap < -10000) {
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

    /**
     * flush
     */
    private void flush() {

        if (!this.updated)
            return;

        long current = this.current;
        int change = 0;
        int lost = 0;

        KCPSegment templateSegment = new KCPSegment(12);
        templateSegment.setConversationId(this.conversationId);
        templateSegment.setCommandId(KCPUtils.KCP_CMD_ACK);
        templateSegment.setFragmentId(0);
        templateSegment.setWindowSize(this.getWindowUnused());
        templateSegment.setUnacknowledgedSegmentId(this.nextReceiveSegmentId);
        templateSegment.setSegmentId(0);
        templateSegment.setTimeStamp(0);

        // flush acknowledges
        int count = this.ackList.size();
        for (int i = 0; i < count; i++) {
            int size = buffer.position();
            if (size + KCPUtils.KCP_OVERHEAD > this.MTU) {
                buffer.flip();
                this.output(buffer, size);
                buffer.clear();
            }
            this.getAck(i, templateSegment);
            templateSegment.encodeHead(buffer);
        }
        this.ackList.clear();

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

        // flush window probing commands
        if ((this.probe & KCPUtils.KCP_ASK_SEND) != 0) {
            templateSegment.setCommandId(KCPUtils.KCP_CMD_WINDOW_ASK);
            int size = buffer.position();
            if (size + KCPUtils.KCP_OVERHEAD > this.MTU) {
                buffer.flip();
                this.output(buffer, size);
                buffer.clear();
            }
            templateSegment.encodeHead(buffer);
        }

        if ((this.probe & KCPUtils.KCP_ASK_TELL) != 0) {
            templateSegment.setCommandId(KCPUtils.KCP_CMD_WINS);
            int size = buffer.position();
            if (size + KCPUtils.KCP_OVERHEAD > this.MTU) {
                buffer.flip();
                this.output(buffer, size);
                buffer.clear();
            }
            templateSegment.encodeHead(buffer);
        }
        this.probe = 0;

        // calculate window size
        int cwnd = Math.min(this.sendWindow, this.remoteWindow);
        if (!this.isNoCrowdedWindow) {
            cwnd = Math.min(this.crowdedWindow, cwnd);
        }

        // move data from snd_queue to snd_buf
        while (this.nextSendSegmentId - (this.sendUnacknowledgedSegmentId + cwnd) < 0) {
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
            segment.setFastAck(0);
            segment.setSendCount(0);
            this.sendBuff.add(segment);

            this.nextSendSegmentId++;
        }

        //calculate resent
        int resent = this.fastResend > 0 ? this.fastResend : 0xffffffff;
        int rtoMin = this.noDelay == 0 ? (this.currentRTO >> 3) : 0;

        // flush data segments
        for (KCPSegment segment : this.sendBuff) {
            int needSend = 0;
            if (segment.getSendCount() == 0) {
                needSend = 1;
                segment.setSendCount(segment.getSendCount() + 1);
                segment.setRTO(this.currentRTO);
                segment.setResendTimeStamp(current + segment.getRTO() + rtoMin);
            } else if (current - segment.getResendTimeStamp() >= 0) {
                needSend = 1;
                segment.setSendCount(segment.getSendCount() + 1);
                this.sendCount++;
                if (this.noDelay == 0) {
                    segment.setRTO(segment.getRTO() + Math.max(segment.getRTO(), this.currentRTO));
                } else {
                    long step = (this.noDelay < 2) ? segment.getRTO() : this.currentRTO;
                    segment.setRTO(segment.getRTO() + step / 2);
                }
                segment.setResendTimeStamp(current + segment.getRTO());
                lost = 1;
            } else if (segment.getFastAck() >= resent) {
                if (segment.getSendCount() <= this.fastLimit || this.fastLimit <= 0) {
                    needSend = 1;
                    segment.setSendCount(segment.getSendCount() + 1);
                    segment.setFastAck(0);
                    segment.setResendTimeStamp(current + segment.getRTO());
                    change++;
                }
            }

            if (needSend != 0) {
                int need;
                segment.setTimeStamp(current);
                segment.setWindowSize(templateSegment.getWindowSize());
                segment.setUnacknowledgedSegmentId(this.nextReceiveSegmentId);

                int size = buffer.position();
                need = KCPUtils.KCP_OVERHEAD + segment.getLength();

                if (size + need > this.MTU) {
                    buffer.flip();
                    this.output(buffer, size);
                    buffer.clear();
                }
                segment.encodeHead(buffer);

                if (segment.getLength() > 0) {
                    segment.encodeData(buffer);
                }

                if (segment.getSendCount() >= this.dead_link) {
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
        if (change != 0) {
            int inflight = this.nextSendSegmentId - this.sendUnacknowledgedSegmentId;
            this.slowStartThresh = inflight / 2;
            if (this.slowStartThresh < KCPUtils.KCP_THRESH_MIN) {
                this.slowStartThresh = KCPUtils.KCP_THRESH_MIN;
            }
            this.crowdedWindow = this.slowStartThresh + resent;
            this.incr = this.crowdedWindow * this.MSS;
        }

        if (lost != 0) {
            this.slowStartThresh = cwnd / 2;
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

    /**
     * 设置分片的acknowledge信息
     *
     * @param index   索引
     * @param segment 分片
     */
    private void getAck(int index, KCPSegment segment) {
        KCPACKInfo info = ackList.get(index);
        if (info != null) {
            segment.setSegmentId(info.segmentId());
            segment.setTimeStamp(info.timeStamp());
        }
    }

    /**
     * 将数据传递给上层逻辑
     *
     * @param buffer 数据buffer
     * @param size   数据大小
     * @return 传送数据大小
     */
    private int output(ByteBuffer buffer, int size) {
        byte[] data = new byte[size];
        buffer.get(data);

        if (canLog(KCPUtils.KCP_LOG_OUTPUT)) {
            this.writeLog(KCPUtils.KCP_LOG_OUTPUT, "[RO] user:%d size:%d", this.user, size);
        }
        if (size == 0) {
            return 0;
        }
        if (this.IKCPContext != null) {
            return IKCPContext.output(data, size, this, user);
        }
        return 0;
    }

    //---------------------------------------------------------------------
    // Determine when should you invoke update:
    // returns when you should invoke update in timeMillis, if there
    // is no input/send calling. you can call update in that
    // time, instead of call update repeatedly.
    // Important to reduce unnecessary update invoking. use it to
    // schedule update (eg. implementing an epoll-like mechanism,
    // or optimize update when handling massive kcp connections)
    //---------------------------------------------------------------------
    public long check(long current) {
        long flushTimeStamp = this.nextFlushTimeStamp;
        long tm_packet = 0x7fffffff;
        long minimal;

        if (!this.updated) {
            return current;
        }

        if (current - flushTimeStamp >= 10000 || current < -10000) {
            flushTimeStamp = current;
        }

        if (current - flushTimeStamp >= 0) {
            return current;
        }

        long tm_flush = flushTimeStamp - current;

        for (KCPSegment segment : this.sendBuff) {
            long diff = segment.getResendTimeStamp() - current;
            if (diff <= 0) {
                return current;
            }
            if (diff < tm_packet) {
                tm_packet = diff;
            }
        }

        minimal = Math.min(tm_packet, tm_flush);
        if (minimal >= this.interval) {
            minimal = this.interval;
        }

        return current + minimal;
    }

    public void setInterval(int interval) {
        if (interval > 5000) {
            interval = 5000;
        } else if (interval < 10) {
            interval = 10;
        }
        this.interval = interval;
    }

    public int setMTU(int mtu) {
        if (mtu < 50 || mtu < KCPUtils.KCP_OVERHEAD) {
            return KCPUtils.KCP_ERROR_ARGUMENT_INVALID;
        }
        ByteBuffer buffer = ByteBuffer.allocate((mtu + KCPUtils.KCP_OVERHEAD) * 3);
        this.MTU = mtu;
        this.MSS = this.MTU - KCPUtils.KCP_OVERHEAD;
        this.buffer = buffer;
        return KCPUtils.KCP_OPERATION_SUCCESS;
    }

    public void setWindowSize(int sendWindow, int receiveWindow) {
        if (sendWindow > 0) {
            this.sendWindow = sendWindow;
        }
        if (receiveWindow > 0) {
            this.receiveWindow = Math.max(receiveWindow, KCPUtils.KCP_WND_RCV);
        }
    }

    public int getWaitSend() {
        return this.sendBuff.size() + this.sendQueue.size();
    }

    /**
     * @param noDelay
     * @param interval
     * @param reSend
     * @param isNoCrowdedWindow
     */
    public void setNoDelay(int noDelay, int interval, int reSend, boolean isNoCrowdedWindow) {
        if (noDelay >= 0) {
            this.noDelay = noDelay;
            if (noDelay != 0) {
                this.minRto = KCPUtils.KCP_RTO_NDL;
            } else {
                this.minRto = KCPUtils.KCP_RTO_MIN;
            }
        }
        if (interval >= 0) {
            if (interval > 5000) {
                interval = 5000;
            } else if (interval < 10) {
                interval = 10;
            }
            this.interval = interval;
        }
        if (reSend >= 0) {
            this.fastResend = reSend;
        }
        this.isNoCrowdedWindow = isNoCrowdedWindow;
    }

    public void setIKCPContext(IKCPContext IKCPContext) {
        this.IKCPContext = IKCPContext;
    }

    private boolean canLog(int mask) {
        if ((mask & this.logMask) == 0) {
            return false;
        }
        return this.IKCPContext != null;
    }

    private void writeLog(int mask, String fmt, Object... args) {
        if ((mask & this.logMask) == 0) {
            return;
        }
        String message = String.format(fmt, args);
        if (this.IKCPContext != null) {
            this.IKCPContext.writeLog(message, this, user);
        }
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

    public long getRttVal() {
        return rttVal;
    }

    public void setRttVal(long rttVal) {
        this.rttVal = rttVal;
    }

    public long getSmoothRtti() {
        return smoothRtti;
    }

    public void setSmoothRtti(long smoothRtti) {
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

    public int getSendCount() {
        return sendCount;
    }

    public void setSendCount(int sendCount) {
        this.sendCount = sendCount;
    }

    public int getNoDelay() {
        return noDelay;
    }

    public void setNoDelay(int noDelay) {
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

    public int getDead_link() {
        return dead_link;
    }

    public void setDead_link(int dead_link) {
        this.dead_link = dead_link;
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

    public int getAckCount() {
        return ackCount;
    }

    public void setAckCount(int ackCount) {
        this.ackCount = ackCount;
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

    public int getStream() {
        return stream;
    }

    public void setStream(int stream) {
        this.stream = stream;
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
