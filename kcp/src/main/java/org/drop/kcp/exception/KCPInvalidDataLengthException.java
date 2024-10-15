package org.drop.kcp.exception;

import org.drop.kcp.core.KCPContext;

/**
 * 数据长度异常
 */
public class KCPInvalidDataLengthException extends IllegalArgumentException {
    public KCPInvalidDataLengthException(String message, KCPContext kcpContext) {
        super(message);
    }
}
