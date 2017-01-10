package io.netty.Invoke;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by sunquan on 16/3/30.
 */
public class ResultModel implements Serializable {
    Map<String, Object> data = new HashMap<String, Object>();
    private int status = 0;
    private String msg = "OK";

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public void put(String key, Object value){
        data.put(key, value);
    }
}
