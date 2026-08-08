package me.alexisbinh.openlootr.codec;

public final class CodecException extends Exception {
    private static final long serialVersionUID = 1L;

    public CodecException(String message) {
        super(message);
    }

    public CodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
