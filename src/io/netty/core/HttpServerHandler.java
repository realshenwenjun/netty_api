package io.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;

/**
 * Created by Administrator on 2016/11/27.
 */
public class HttpServerHandler extends SimpleChannelInboundHandler<HttpObject> {
    private static final Logger logger = Logger.getLogger(HttpServerHandler.class.getName());
    private HttpRequestMessage requestMessage;
    private HttpRequest httpRequest;
    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); //Disk
    private HttpPostRequestDecoder decoder;

    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true; // should delete file
        // on exit (in normal
        // exit)
        DiskFileUpload.baseDirectory = null; // system temp directory
        DiskAttribute.deleteOnExitTemporaryFile = true; // should delete file on
        // exit (in normal exit)
        DiskAttribute.baseDirectory = null; // system temp directory
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (decoder != null) {
            decoder.cleanFiles();
        }
    }

    public void messageReceived(ChannelHandlerContext ctx, HttpObject object) {
        if (HttpServer.isSSL) {
            logger.info("Your session is protected by " +
                    ctx.pipeline().get(SslHandler.class).engine().getSession().getCipherSuite() +
                    " cipher suite.\n");
        }
        HttpRequestMessage requestMessage = null;
        try {
            requestMessage = decode(object);
            logger.info("requestMessage : \r\n" + requestMessage.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 把处理后的结果发送到下一个ChannelHandler
//        ctx.fireChannelRead("");
        writeResponse("", ctx.channel(), httpRequest);
        return;
    }

    private HttpRequestMessage decode(HttpObject obj) throws IOException {
        if (obj instanceof HttpRequest) {
            this.httpRequest = (HttpRequest) obj;
            FullHttpRequest fullHttpRequest = (FullHttpRequest) obj;
            this.requestMessage = new HttpRequestMessage();
            requestMessage.setHttpVersion(fullHttpRequest.getProtocolVersion().text());
            requestMessage.setUri(fullHttpRequest.getUri());
            requestMessage.setHeader(new HashMap<String, Object>());
            requestMessage.setParameters(new HashMap<String, Object>());
            for (Map.Entry<String, String> entry : fullHttpRequest.headers()) {
                requestMessage.getHeader().put(entry.getKey(), entry.getValue());
            }
            Set<Cookie> cookies;
            String value = fullHttpRequest.headers().get(COOKIE);
            if (value == null) {
                cookies = Collections.emptySet();
            } else {
                cookies = CookieDecoder.decode(value);
            }
            requestMessage.getHeader().put("cookie", cookies);

            if (fullHttpRequest.getMethod().equals(HttpMethod.GET)) {
                //get请求
                requestMessage.setMethod("GET");
                QueryStringDecoder decoderQuery = new QueryStringDecoder(fullHttpRequest.getUri());
                Map<String, List<String>> uriAttributes = decoderQuery.parameters();
                for (Map.Entry<String, List<String>> attr : uriAttributes.entrySet()) {
                    for (String attrVal : attr.getValue()) {
                        requestMessage.getParameters().put(attr.getKey(), attrVal);
                    }
                }
            } else if (fullHttpRequest.getMethod().equals(HttpMethod.POST)) {
                //post请求
                requestMessage.setMethod("POST");
                decoder = new HttpPostRequestDecoder(factory, fullHttpRequest);
            } else {
            }
        }
        if (decoder != null) {
            if (obj instanceof HttpContent) {
                HttpContent chunk = (HttpContent) obj;
                decoder.offer(chunk);
                if (decoder.isMultipart()) {
                    readHttpDataChunkByChunk(decoder);
                } else { //普通post请求
                    List<InterfaceHttpData> parmList = decoder.getBodyHttpDatas();
                    for (InterfaceHttpData parm : parmList) {
                        Attribute attribute = (Attribute) parm;
                        requestMessage.getParameters().put(attribute.getName(), attribute.getValue());
                    }
                }

                if (chunk instanceof LastHttpContent) {
                    reset(decoder);
                }
            }

        }

        return requestMessage;
    }

    private void reset(HttpPostRequestDecoder decoder) {
        requestMessage = null;
        this.decoder.destroy();
        this.decoder = null;
    }

    /**
     * Example of reading request by chunk and getting values from chunk to chunk
     */
    private void readHttpDataChunkByChunk(HttpPostRequestDecoder decoder) throws IOException {
        while (decoder.hasNext()) {
            InterfaceHttpData data = decoder.next();
            if (data != null) {
                try {
                    // new value
                    writeHttpData(data);
                } finally {
                    data.release();
                }
            }
        }
    }

    private void writeHttpData(InterfaceHttpData data) throws IOException {

        /**
         * HttpDataType有三种类型
         * Attribute, FileUpload, InternalAttribute
         */
        if (data.getHttpDataType() == HttpDataType.Attribute) {
            Attribute attribute = (Attribute) data;
            requestMessage.getParameters().put(attribute.getName(), attribute.getValue());
        } else if (data.getHttpDataType() == HttpDataType.FileUpload) {
            FileUpload fileUpload = (FileUpload) data;
            if (fileUpload.isCompleted()) {
                requestMessage.getParameters().put(fileUpload.getName(), fileUpload.getFile());
            }
        }
    }

    private String getUploadFileName(InterfaceHttpData data) {
        String content = data.toString();
        String temp = content.substring(0, content.indexOf("\n"));
        content = temp.substring(temp.lastIndexOf("=") + 2, temp.lastIndexOf("\""));
        return content;
    }

    /**
     * http返回响应数据
     *
     * @param channel
     */
    private void writeResponse(String o, Channel channel, HttpRequest httpRequest) {
        // Convert the response content to a ChannelBuffer.
        ByteBuf buf = copiedBuffer(o, CharsetUtil.UTF_8);

        // Decide whether to close the connection or not.
        boolean close = httpRequest.headers().contains(CONNECTION, HttpHeaders.Values.CLOSE, true)
                || httpRequest.getProtocolVersion().equals(HttpVersion.HTTP_1_0)
                && !httpRequest.headers().contains(CONNECTION, HttpHeaders.Values.KEEP_ALIVE, true);

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.headers().set(CONTENT_LENGTH, buf.readableBytes());
        }

        Set<Cookie> cookies;
        String value = httpRequest.headers().get(COOKIE);
        if (value == null) {
            cookies = Collections.emptySet();
        } else {
            cookies = CookieDecoder.decode(value);
        }
        if (!cookies.isEmpty()) {
            // Reset the cookies if necessary.
            for (Cookie cookie : cookies) {
                response.headers().add(SET_COOKIE, ServerCookieEncoder.encode(cookie));
            }
        }
        // Write the response.
        ChannelFuture future = channel.writeAndFlush(response);
        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        messageReceived(ctx, msg);
    }
}
