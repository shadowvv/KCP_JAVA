public class KCPBufferLengthIsNotEnoughException extends RuntimeException {
    public KCPBufferLengthIsNotEnoughException(String message,KCPContext context) {
        super(message);
    }
}
