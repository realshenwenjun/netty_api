package io.netty.core;

import io.netty.Exception.InitRouteMappingException;
import io.netty.Exception.NettyException;
import io.netty.Exception.RouteNotFoundException;
import io.netty.Invoke.Invoke;
import io.netty.Invoke.ObjectMapper;
import io.netty.Invoke.ResultModel;
import io.netty.annotation.NettyMapping;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ASUS on 2016/11/18.
 */
public class DefaultRoute {
    private ApplicationContext context;

    private static Map<String, Invoke> routes = new ConcurrentHashMap<String, Invoke>();

    public DefaultRoute() {
    }

    public DefaultRoute(ApplicationContext context) throws Exception {
        this.context = context;
        initRoute();
    }

    private void initRoute() throws Exception {
        Map<String, Object> beans = context.getBeansWithAnnotation(NettyMapping.class);
        for (String key : beans.keySet()) {
            try {
                Object o = beans.get(key);
                Method[] methods = o.getClass().getDeclaredMethods(); // 获取实体类的所有属性，返回Field数组
                for (int j = 0; j < methods.length; j++) { // 遍历所有属性
                    NettyMapping m = methods[j].getAnnotation(NettyMapping.class);
                    if (m != null) {//手动注入
                        Invoke invoke = new Invoke();
                        invoke.setO(o);
                        invoke.setM(methods[j]);
                        routes.put(m.value(), invoke);
                    }
                }
            } catch (Exception e) {
                throw new InitRouteMappingException("init " + key + " route mapping fail.");
            }
        }
    }

    public Invoke getInvoke(String cxt) throws RouteNotFoundException {
        // TODO 分析cxt得到key
        Invoke invoke = routes.get(cxt);
        if (invoke == null) {
            throw new RouteNotFoundException();
        }
        return invoke;
    }

    public String handle(HttpRequestMessage request) throws RouteNotFoundException, IOException {
        Invoke invoke = this.getInvoke(request.getUri());
        invoke.setR(request);
        ResultModel resultModel = null;
        try {
            resultModel = invoke.invoke();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            if (throwable instanceof NettyException) {
                resultModel = new ResultModel();
                resultModel.setStatus(((NettyException) throwable).getCode());
                resultModel.setMsg(((NettyException) throwable).getMes());
            } else {
                resultModel = new ResultModel();
                resultModel.setStatus(500);
                resultModel.setMsg("Internal Server Error");
            }
        }
        return ObjectMapper.objToString(resultModel);
    }

}
