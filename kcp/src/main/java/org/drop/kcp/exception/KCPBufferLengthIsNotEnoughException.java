package org.drop.kcp.exception;

import org.drop.kcp.core.KCPContext;

public class KCPBufferLengthIsNotEnoughException extends RuntimeException {
    public KCPBufferLengthIsNotEnoughException(String message, KCPContext context) {
        super(message);
    }
}
