package io.netty.filter;

import io.netty.core.HttpRequestMessage;

/**
 * Created by ASUS on 2017/1/11.
 */
public interface DefaultFilter {
    boolean match(HttpRequestMessage requestMessage);

    void chain(HttpRequestMessage requestMessage) throws Throwable;
}
