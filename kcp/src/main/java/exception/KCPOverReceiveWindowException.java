package exception;

import core.KCPContext;

public class KCPOverReceiveWindowException extends RuntimeException {
    public KCPOverReceiveWindowException(String message, KCPContext kcpContext) {
        super(message);
    }
}
