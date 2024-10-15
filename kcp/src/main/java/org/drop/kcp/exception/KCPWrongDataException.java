package org.drop.kcp.exception;

import org.drop.kcp.core.KCPContext;

public class KCPWrongDataException extends RuntimeException {
    public KCPWrongDataException(String message, KCPContext kcpContext) {
        super(message);
    }
}
