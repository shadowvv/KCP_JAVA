package org.drop.kcp.exception;

import org.drop.kcp.core.KCPContext;

public class KCPBufferDataNotEnoughToReceiveException extends RuntimeException {
    public KCPBufferDataNotEnoughToReceiveException(String message, KCPContext context) {
        super(message);
    }
}
