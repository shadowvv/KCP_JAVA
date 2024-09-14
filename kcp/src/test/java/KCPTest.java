import java.nio.ByteBuffer;
import java.util.Scanner;

public class KCPTest implements IKCPContext {

    KCPLatencySimulator simulator;

    public KCPTest() {

    }

    /**
     * 模拟网络：模拟发送一个 udp包
     * @param buff 数据
     * @param length 数据长度
     * @param kcpContext kcp连接
     * @param user user标识
     * @return 操作是否成功
     */
    public int output(byte[] buff, int length, KCPContext kcpContext, Object user){
        if (user instanceof Integer){
            int id = (int) user;
            simulator.send(id,buff,length);
            return 0;
        }
        return -1;
    }

    @Override
    public void writeLog(String log, KCPContext kcp, Object user) {
        System.out.println(log);
    }

    public KCPContext buildTestObject(int conversionId, Object user) {
        KCPContext context = new KCPContext();
        context.setConversationId(conversionId);
        context.setUser(user);

        context.setSendUnacknowledgedSegmentId(0);
        context.setNextSendSegmentId(0);
        context.setNextReceiveSegmentId(0);
        context.setLastSendTimeStamp(0);
        context.setLastACKTimeStamp(0);
        context.setNextProbeTimeStamp(0);
        context.setProbeWait(0);
        context.setWindowSize(KCPUtils.KCP_WND_SND,KCPUtils.KCP_WND_RCV);
        context.setRemoteWindow(KCPUtils.KCP_WND_RCV);
        context.setCrowdedWindow(0);
        context.setIncr(0);
        context.setProbe(0);
        context.setMTU(KCPUtils.KCP_MTU_DEF);
        context.setMSS(context.getMTU() - KCPSegment.KCP_OVERHEAD);
        context.setIsStream(false);

        context.setBuffer(ByteBuffer.allocate((context.getMTU() + KCPSegment.KCP_OVERHEAD) * 3));

        context.setState(0);
        context.setSmoothRtti(0);
        context.setRttVal(0);
        context.setCurrentRTO(KCPUtils.KCP_RTO_DEF);
        context.setMinRto(KCPUtils.KCP_RTO_MIN);
        context.setCurrent(0);
        context.setInterval(KCPUtils.KCP_INTERVAL);
        context.setNextFlushTimeStamp(KCPUtils.KCP_INTERVAL);
        context.setNoDelay(0);
        context.setUpdated(false);
        context.setLogMask(0);
        context.setSlowStartThresh(KCPUtils.KCP_THRESH_INIT);
        context.setFastResend(0);
        context.setFastLimit(KCPUtils.KCP_FAST_ACK_LIMIT);
        context.setIsNoCrowdedWindow(false);
        context.setSendCount(0);
        context.setDeadLink(KCPUtils.KCP_DEAD_LINK);
        return context;
    }

    /**
     * 测试用例
     * @param mode 0:默认模式;1:普通模式，关闭流控等;2:启动快速模式
     */
    private void test(int mode) throws InterruptedException {

        // 创建模拟网络：丢包率10%，Rtt 60ms~125ms
        this.simulator = new KCPLatencySimulator(10,60,125,1000);

        // 创建两个端点的 kcp对象，第一个参数 conv是会话编号，同一个会话需要相同
        // 最后一个是 user参数，用来传递标识
        KCPContext KCPContext1 = buildTestObject(0x11223344,0);
        KCPContext KCPContext2 = buildTestObject(0x11223344,1);

        // 设置kcp的下层输出，这里为 udp_output，模拟udp网络输出函数
        KCPContext1.setIKCPContext(this);
        KCPContext2.setIKCPContext(this);

        long current = System.currentTimeMillis() & 0xffffffffL;
        long slap = current + 20;
        int index = 0;
        long next = 0;
        long sumRtt = 0;
        int count = 0;
        long maxRtt = 0;

        // 配置窗口大小：平均延迟200ms，每20ms发送一个包，
        // 而考虑到丢包重发，设置最大收发窗口为128
        KCPContext1.setWindowSize(128,128);
        KCPContext2.setWindowSize(128,128);

        // 判断测试用例的模式
        if (mode == 0){
            // 默认模式
            KCPContext1.setNoDelay(0,10,0,false);
            KCPContext2.setNoDelay(0,10,0,false);
        }else if (mode == 1){
            // 普通模式，关闭流控等
            KCPContext1.setNoDelay(0,10,0,true);
            KCPContext2.setNoDelay(0,10,0,true);
        }else {
            // 启动快速模式
            // 第一个参数 noDelay-启用以后若干常规加速将启动
            // 第二个参数 interval为内部处理时钟，默认设置为 10ms
            // 第三个参数 resend为快速重传指标，设置为2
            // 第四个参数 为是否禁用常规流控，这里禁止
            KCPContext1.setNoDelay(2,10,2,true);
            KCPContext2.setNoDelay(2,10,2,true);
            KCPContext1.setMinRto(10);
            KCPContext1.setFastResend(1);
        }

        ByteBuffer buffer = ByteBuffer.allocate(2000);
        int hr;
        long ts1 = System.currentTimeMillis() & 0xffffffffL;

        while (true){
            Thread.sleep(1);
            current = System.currentTimeMillis() & 0xffffffffL;
            KCPContext1.update(current);
            KCPContext2.update(current);

            // 每隔 20ms，kcp1发送数据
            for (;current >= slap ;slap += 20){
                buffer.clear();
                buffer.putInt(index++);
                buffer.putLong(current);
                buffer.flip();
                // 发送上层协议包
                if(KCPContext1.send(buffer,12) < 0){
                    return;
                }
            }

            // 处理虚拟网络：检测是否有udp包从p1->p2
            while (true){
                buffer.clear();
                hr = simulator.receive(1,buffer,2000);
                if (hr < 0){
                    break;
                }
                // 如果 p2收到udp，则作为下层协议输入到kcp2
                buffer.flip();
                KCPContext2.input(buffer,hr);
            }

            // 处理虚拟网络：检测是否有udp包从p2->p1
            while (true){
                buffer.clear();
                hr = simulator.receive(0,buffer,2000);
                if (hr < 0){break;}
                // 如果 p1收到udp，则作为下层协议输入到kcp1
                buffer.flip();
                KCPContext1.input(buffer,hr);
            }

            // kcp2接收到任何包都返回回去
            while (true){
                buffer.clear();
                hr = KCPContext2.receive(buffer,14);
                // 没有收到包就退出
                if (hr < 0){break;}
                // 如果收到包就回射
                buffer.flip();
                KCPContext2.send(buffer,hr);
            }

            // kcp1收到kcp2的回射数据
            while (true){
                buffer.clear();
                hr = KCPContext1.receive(buffer,14);
                // 没有收到包就退出
                if (hr < 0){break;}

                // 如果收到包就回射
                buffer.flip();
                long sn = buffer.getInt();
                long ts = buffer.getLong();
                long rtt = current - ts;
                if (sn != next){
                    // 如果收到的包不连续
                    System.out.println("sn != next");
                    return;
                }

                next++;
                sumRtt += rtt;
                count++;
                if (rtt > maxRtt){
                    maxRtt = rtt;
                }
                System.out.printf("[RECV] mode=%d sn=%d rtt=%d\n",mode,sn,rtt);
            }
            if (next > 1000){
                break;
            }
        }
        ts1 = System.currentTimeMillis() & 0xffffffffL - ts1;
        KCPContext1.release();
        KCPContext2.release();

        String[] names = { "default", "normal", "fast" };

        // 输出结果
        System.out.printf("%s mode result (%dms):%n\n", names[mode], (int) ts1);
        System.out.printf("avgrtt=%d maxrtt=%d tx=%d%n\n", (int) (sumRtt / count), (int) maxRtt, simulator.getTx1());
        System.out.println("press enter to next ...");

        // 等待用户按下回车键
        Scanner scanner = new Scanner(System.in);

        scanner.nextLine();
    }

    public static void main(String[] args) throws InterruptedException {
        KCPTest test = new KCPTest();
        test.test(0);// 默认模式，类似 TCP：正常模式，无快速重传，常规流控
        test.test(1);// 普通模式，关闭流控等
        test.test(2);// 快速模式，所有开关都打开，且关闭流控
    }

}
