package org.drop.kcp.exception;

import org.drop.kcp.core.KCPContext;

public class KCPDataHeadWrongConversationIdException extends RuntimeException {
    public KCPDataHeadWrongConversationIdException(String message, KCPContext kcpContext) {
        super(message);
    }
}
