package test;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * Created by ASUS on 2016/11/18.
 */
@Service
public class UserService {
    Logger logger = Logger.getLogger(UserService.class);
    public void say(){
        logger.warn("ask say****************************ask finished.");
        System.out.println("********************");
    }
}
