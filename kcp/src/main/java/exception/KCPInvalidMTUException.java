package exception;

import core.KCPContext;

public class KCPInvalidMTUException extends RuntimeException {
    public KCPInvalidMTUException(String message, KCPContext kcpContext) {
        super(message);
    }
}
