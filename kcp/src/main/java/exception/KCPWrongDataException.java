package exception;

import core.KCPContext;

public class KCPWrongDataException extends RuntimeException {
    public KCPWrongDataException(String message, KCPContext kcpContext) {
        super(message);
    }
}
