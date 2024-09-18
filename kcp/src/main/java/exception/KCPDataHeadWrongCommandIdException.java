package exception;

import core.KCPContext;

public class KCPDataHeadWrongCommandIdException extends RuntimeException {
    public KCPDataHeadWrongCommandIdException(String message, KCPContext kcpContext) {
        super(message);
    }
}
