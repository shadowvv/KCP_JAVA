public interface IKCPContext {

    int output(byte[] buff, int len, KCPContext kcp, Object user);

    void writeLog(String log, KCPContext kcp, Object user);

}
