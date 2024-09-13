import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 网络延迟模拟器
 */
public class KCPLatencySimulator {

    private int tx1;
    private int tx2;
    private long current;
    private final int lostRate;
    private final int rttMin;
    private final int rttMax;
    private final int nMax;
    private final KCPRandom r12;
    private final KCPRandom r21;
    private final List<KCPDelayPacket> p12;
    private final List<KCPDelayPacket> p21;

    /**
     * 构建
     * @param lostRate 往返一周丢包率的百分比，默认 10%
     * @param rttMin rtt最小值，默认 60ms
     * @param rttMax rtt最大值，默认 125ms
     * @param nMax 数据包最大值
     */
    KCPLatencySimulator(int lostRate, int rttMin, int rttMax, int nMax) {
        this.r12 = new KCPRandom(100);
        this.r21 = new KCPRandom(100);
        this.current = System.currentTimeMillis() & 0xffffffffL;
        this.lostRate = lostRate / 2; // 上面数据是往返丢包率，单程除以2
        this.rttMin = rttMin / 2;
        this.rttMax = rttMax / 2;
        this.nMax = nMax;
        this.tx1 = 0;
        this.tx2 = 0;
        this.p12 = new ArrayList<>();
        this.p21 = new ArrayList<>();
    }

    /**
     * 清除数据
     */
    public void clear() {
        this.p12.clear();
        this.p21.clear();
    }

    /**
     * 发送数据
     * @param peer 端点0/1，从0发送，从1接收；从1发送从0接收
     * @param data 数据
     * @param size 数据大小
     */
    public void send(int peer, byte[] data, int size) {
        if (peer == 0) {
            tx1++;
            if (r12.random() < this.lostRate) return;
            if (p12.size() >= nMax) return;
        } else {
            tx2++;
            if (r21.random() < this.lostRate) return;
            if (p21.size() >= nMax) return;
        }
        current = System.currentTimeMillis() & 0xffffffffL;
        int delay = rttMin;
        if (rttMax > rttMin) {
            delay += new Random().nextInt(rttMax - rttMin);
        }

        KCPDelayPacket dp = new KCPDelayPacket(data, size,current+delay);
        if (peer == 0) {
            p12.add(dp);
        } else {
            p21.add(dp);
        }
    }

    /**
     * 接收数据
     * @param peer 来源id
     * @param buffer 数据buffer
     * @param maxSize 接收数据最大值
     * @return 接收的数据大小
     */
    public int receive(int peer, ByteBuffer buffer, int maxSize) {
        Iterator<KCPDelayPacket> it;
        if (peer == 0) {
            if (p21.isEmpty()) return -1;
            it = p21.iterator();
        } else {
            if (p12.isEmpty()) return -1;
            it = p12.iterator();
        }

        current = System.currentTimeMillis() & 0xffffffffL;
        KCPDelayPacket dp = it.next();
        if (current < dp.timeStamp()) {
            return -2;
        }
        if (maxSize < dp.size()) {
            return -3;
        }
        maxSize = dp.size();
        buffer.put(dp.data(), 0, dp.size());
        if (peer == 0) {
            it.remove(); // 从 p21 删除
        } else {
            it.remove(); // 从 p12 删除
        }
        return maxSize;
    }

    public int getTx1() {
        return tx1;
    }
}
