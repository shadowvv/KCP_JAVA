package simulate;

/**
 * 带延迟的数据包
 */
import java.util.Arrays;
import java.util.Objects;

public class KCPDelayPacket {
    private final byte[] _data;
    private final int _size;
    private final long _timeStamp;

    public KCPDelayPacket(byte[] _data, int _size, long _timeStamp) {
        this._data = Arrays.copyOf(_data, _data.length); // 复制数据以保护原始数组
        this._size = _size;
        this._timeStamp = _timeStamp;
    }

    public byte[] data() {
        return Arrays.copyOf(_data, _data.length); // 返回数据的副本以保护原始数组
    }

    public int size() {
        return _size;
    }

    public long timeStamp() {
        return _timeStamp;
    }

    @Override
    public String toString() {
        return "KCPDelayPacket{" +
                "_data=" + Arrays.toString(_data) +
                ", _size=" + _size +
                ", _timeStamp=" + _timeStamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KCPDelayPacket)) return false;
        KCPDelayPacket that = (KCPDelayPacket) o;
        return _size == that._size && _timeStamp == that._timeStamp && Arrays.equals(_data, that._data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(_size, _timeStamp);
        result = 31 * result + Arrays.hashCode(_data);
        return result;
    }
}
