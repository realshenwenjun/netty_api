package io.netty.core;

import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * Created by ASUS on 2016/11/28.
 */
public class HttpRequestMessage {
    private ApplicationContext context;
    private String httpVersion;
    private String uri;
    private String method;
    private Map<String, Object> header;

    private Map<String, Object> parameters;

    public ApplicationContext getContext() {
        return context;
    }

    public void setContext(ApplicationContext context) {
        this.context = context;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getHeader() {
        return header;
    }

    public void setHeader(Map<String, Object> header) {
        this.header = header;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public Object getParameter(String name) {
        return this.getParameters().get(name);
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("VERSION : " + this.getHttpVersion() + "\r\n")
                .append("URI : " + this.getUri() + "\r\n")
                .append("METHOD : " + this.getMethod() + "\r\n");
        for (Map.Entry<String, Object> entry : this.header.entrySet()) {
            sb.append(entry.getKey() + " : " + entry.getValue() + "\r\n");
        }
        for (Map.Entry<String, Object> entry : this.parameters.entrySet()) {
            sb.append(entry.getKey() + " : " + entry.getValue() + "\r\n");
        }
        return sb.toString();
    }
}
