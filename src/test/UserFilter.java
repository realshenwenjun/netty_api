package test;

import io.netty.Exception.RouteNotFoundException;
import io.netty.core.HttpRequestMessage;
import io.netty.filter.DefaultFilter;
import org.springframework.stereotype.Service;

/**
 * Created by ASUS on 2017/1/11.
 */
@Service
public class UserFilter implements DefaultFilter {
    @Override
    public boolean match(HttpRequestMessage requestMessage) {
        return true;
    }

    @Override
    public void chain(HttpRequestMessage requestMessage) throws Throwable{
        System.out.println(requestMessage);
        System.out.println("输出了");
    }
}
