package io.netty.Exception;

/**
 * Created by ASUS on 2017/1/9.
 */
public class InitRouteMappingException extends Exception implements NettyException {
    public InitRouteMappingException() {
    }

    public InitRouteMappingException(String message) {
        super(message);
    }

    public InitRouteMappingException(String message, Throwable cause) {
        super(message, cause);
    }

    public InitRouteMappingException(Throwable cause) {
        super(cause);
    }

    public InitRouteMappingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public int getCode() {
        return 500;
    }

    public String getMes() {
        return "Internal Server Error";
    }
}
