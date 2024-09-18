package exception;

import core.KCPContext;

public class KCPDataHeadWrongConversationIdException extends RuntimeException {
    public KCPDataHeadWrongConversationIdException(String message, KCPContext kcpContext) {
        super(message);
    }
}
