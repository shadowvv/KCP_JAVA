package core;

public class KCPUtils {

    /**
     * 推送数据命令
     */
    public static final int KCP_CMD_PUSH = 81;
    /**
     * 推送ack信息命令
     */
    public static final int KCP_CMD_ACK = 82;
    /**
     * 请求窗口大小命令
     */
    public static final int KCP_CMD_WINDOW_ASK = 83;
    /**
     * 通知窗口大小命令
     */
    public static final int KCP_CMD_WINDOW_SIZE = 84;


    /**
     * no delay min rto
     */
    public static final int KCP_RTO_NO_DELAY_MIN = 30;
    /**
     * normal min rto
     */
    public static final int KCP_RTO_MIN = 100;
    public static final int KCP_RTO_DEF = 200;
    public static final int KCP_RTO_MAX = 60000;

    public static final int KCP_ASK_SEND = 1;               // need to send KCP_CMD_WINDOW_ASK
    public static final int KCP_ASK_TELL = 2;               // need to send KCP_CMD_WINS
    public static final int KCP_WND_SND = 32;
    public static final int KCP_WND_RCV = 128;              // must >= max fragment size
    public static final int KCP_MTU_DEF = 1400;
    public static final int KCP_ACK_FAST = 3;
    public static final int KCP_INTERVAL = 100;
    public static final int KCP_DEAD_LINK = 20;
    public static final int KCP_THRESH_INIT = 2;
    public static final int KCP_THRESH_MIN = 2;
    /**
     * 7 secs to probe window size
     */
    public static final int KCP_PROBE_INIT = 7000;
    /**
     * up to 120 secs to probe window
     */
    public static final int KCP_PROBE_LIMIT = 120000;
    /**
     *  max times to trigger fast ack
     */
    public static final int KCP_FAST_ACK_LIMIT = 5;

    public static final int KCP_LOG_OUTPUT = 1;
    public static final int KCP_LOG_INPUT = 2;
    public static final int KCP_LOG_SEND = 4;
    public static final int KCP_LOG_REC = 8;
    public static final int KCP_LOG_IN_DATA = 16;
    public static final int KCP_LOG_IN_ACK = 32;
    public static final int KCP_LOG_IN_PROBE = 64;
    public static final int KCP_LOG_IN_WINS = 128;
    public static final int KCP_LOG_OUT_DATA = 256;
    public static final int KCP_LOG_OUT_ACK = 512;
    public static final int KCP_LOG_OUT_PROBE = 1024;
    public static final int KCP_LOG_OUT_WINS = 2048;
    public static final int KCP_LOG_ALL = KCP_LOG_OUTPUT | KCP_LOG_INPUT | KCP_LOG_SEND | KCP_LOG_REC | KCP_LOG_IN_DATA | KCP_LOG_IN_ACK
            | KCP_LOG_IN_PROBE | KCP_LOG_IN_WINS | KCP_LOG_OUT_DATA | KCP_LOG_OUT_ACK | KCP_LOG_OUT_PROBE | KCP_LOG_OUT_WINS;

    public static final int KCP_FINAL_FRAGMENT_ID = 0;

    public static final int KCP_OPERATION_SUCCESS = 0;
    public static final int KCP_ERROR_NO_DATA = -1;
    public static final int KCP_ERROR_INVALID_DATA_LENGTH = -4;
    public static final int KCP_ERROR_DATA_SIZE_WRONG = -6;
    public static final int KCP_ERROR_CONVERSATION_WRONG = -7;
    public static final int KCP_ERROR_CMD_WRONG = -8;

}
