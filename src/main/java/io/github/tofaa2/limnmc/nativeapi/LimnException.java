package io.github.tofaa2.limnmc.nativeapi;

public final class LimnException extends RuntimeException {
    public LimnException(String message) {
        super(message);
    }

    public LimnException(String message, Throwable cause) {
        super(message, cause);
    }
}
