package core;

/**
 * kcp上层逻辑调用接口
 */
public interface IKCPContext {

    /**
     * 数据输出给上层逻辑
     * @param buff 数据
     * @param length 数据长度
     * @param kcp kcp连接
     * @param user kcp对应的应用层用户Id
     * @return 传输的数据长度
     */
    int output(byte[] buff, int length, KCPContext kcp, Object user);

    /**
     * 上层日志输出
     * @param log 日志信息
     * @param kcp kcp连接
     * @param user kcp对应的应用层用户Id
     */
    void writeLog(String log, KCPContext kcp, Object user);

}
