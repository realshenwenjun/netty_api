package io.netty.Exception;

/**
 * Created by ASUS on 2017/1/9.
 */
public class RouteNotFoundException extends Exception implements NettyException{
    public RouteNotFoundException() {
    }

    public RouteNotFoundException(String message) {
        super(message);
    }

    public RouteNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public RouteNotFoundException(Throwable cause) {
        super(cause);
    }

    public RouteNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public int getCode() {
        return 404;
    }

    public String getMes() {
        return "Not Found";
    }
}
