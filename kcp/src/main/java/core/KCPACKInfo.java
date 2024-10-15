package core;

import java.util.Objects;

/**
 * KCP 确认数据
 * @param segmentId 序号
 * @param timeStamp  时间戳
 */
public class KCPACKInfo {
    private final int segmentId;
    private final long timeStamp;

    public KCPACKInfo(int segmentId, long timeStamp) {
        this.segmentId = segmentId;
        this.timeStamp = timeStamp;
    }

    public int getSegmentId() {
        return segmentId;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    @Override
    public String toString() {
        return "KCPACKInfo{" +
                "segmentId=" + segmentId +
                ", timeStamp=" + timeStamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KCPACKInfo)) {
            return false;
        }
        KCPACKInfo that = (KCPACKInfo) o;
        return segmentId == that.segmentId && timeStamp == that.timeStamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(segmentId, timeStamp);
    }
}

