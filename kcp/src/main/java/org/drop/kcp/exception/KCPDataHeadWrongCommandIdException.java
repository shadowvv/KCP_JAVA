package org.drop.kcp.exception;

import org.drop.kcp.core.KCPContext;

public class KCPDataHeadWrongCommandIdException extends RuntimeException {
    public KCPDataHeadWrongCommandIdException(String message, KCPContext kcpContext) {
        super(message);
    }
}
