public class KCPReceiveQueueNextSegmentNotComplete extends RuntimeException {
    public KCPReceiveQueueNextSegmentNotComplete(String message, KCPContext context, int segmentId) {
        super(message);
    }
}
