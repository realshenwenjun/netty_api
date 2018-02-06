package io.netty.filter;

import io.netty.annotation.NettyMapping;
import io.netty.core.HttpRequestMessage;

/**
 * Created by ASUS on 2017/1/11.
 */
public abstract class DefaultFilter {
    static final String patten = "*";

    public boolean match(HttpRequestMessage requestMessage){
        NettyMapping m = this.getClass().getAnnotation(NettyMapping.class);
        String uri = requestMessage.getUri();

        String mapping = m.value();

        // 找到所有*号位置
        if (mapping.lastIndexOf(patten) == -1)
            if (!mapping.equals(uri))
                return false;
            else
            return true;
        for(int i=0; i<=mapping.lastIndexOf(patten); ++i){
            int j = mapping.indexOf(patten,i);
            if (uri.indexOf(mapping.substring(i,j)) == -1)
                return false;
            i = j + 1;
        }
        if (uri.indexOf(mapping.substring(mapping.lastIndexOf(patten) + 1)) == -1)
            return false;
        return true;
    }

    public abstract void chain(HttpRequestMessage requestMessage) throws Throwable;
}
