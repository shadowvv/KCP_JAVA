package org.drop.kcp.exception;

import org.drop.kcp.core.KCPContext;

public class KCPInvalidMTUException extends RuntimeException {
    public KCPInvalidMTUException(String message, KCPContext kcpContext) {
        super(message);
    }
}
