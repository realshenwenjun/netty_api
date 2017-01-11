package test;

import huivo.core.thrift.storage.Storage;
import huivo.core.thrift.storage.StoreService;
import io.netty.Exception.InitRouteMappingException;
import io.netty.Exception.NettyException;
import io.netty.Exception.RouteNotFoundException;
import io.netty.Invoke.ResultModel;
import io.netty.annotation.NettyMapping;
import io.netty.core.HttpRequestMessage;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;

/**
 * Created by ASUS on 2016/11/18.
 */

@Service
@NettyMapping
public class UserController {
    Logger logger = Logger.getLogger(UserController.class);
    @Autowired
    private UserService userService;
    @Autowired
    StoreService.Iface storeService;

    @NettyMapping("/user/login")
    public ResultModel login(HttpRequestMessage request) throws Exception {
        userService.say();
        Object q = request.getParameter("e");
        Storage storage = null;
//        if (storage == null)
//            throw new Exception("");
        ResultModel resultModel = new ResultModel();
        resultModel.put("e", q);
        return resultModel;
    }
}
