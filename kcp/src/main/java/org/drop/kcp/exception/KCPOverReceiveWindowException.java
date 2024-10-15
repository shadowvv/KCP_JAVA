package org.drop.kcp.exception;

import org.drop.kcp.core.KCPContext;

public class KCPOverReceiveWindowException extends RuntimeException {
    public KCPOverReceiveWindowException(String message, KCPContext kcpContext) {
        super(message);
    }
}
