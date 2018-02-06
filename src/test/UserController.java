package test;

import io.netty.Invoke.ResultModel;
import io.netty.annotation.NettyMapping;
import io.netty.core.HttpRequestMessage;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by ASUS on 2016/11/18.
 */

@Service
@NettyMapping("/admin")
public class UserController {
    Logger logger = Logger.getLogger(UserController.class);
    @Autowired
    private UserService userService;

    @NettyMapping("/user/login")
    public ResultModel login(HttpRequestMessage request) throws Exception {
        userService.say();
        Object q = request.getParameter("e");

        ResultModel resultModel = new ResultModel();
        resultModel.put("e", q);
        return resultModel;
    }

    @NettyMapping("/user/login/out")
    public ResultModel loginOut(HttpRequestMessage request) throws Exception {
        userService.say();
        Object q = request.getParameter("e");

        ResultModel resultModel = new ResultModel();
        resultModel.put("e", q);
        return resultModel;
    }
}
