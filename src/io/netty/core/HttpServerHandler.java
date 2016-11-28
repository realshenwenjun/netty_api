package io.netty.core;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;

import java.io.File;
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

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); //Disk

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    public void messageReceived(ChannelHandlerContext ctx, FullHttpRequest msg) {
        FullHttpRequest httpRequest = (FullHttpRequest) msg;

        if (HttpServer.isSSL) {
            logger.info("Your session is protected by " +
                    ctx.pipeline().get(SslHandler.class).engine().getSession().getCipherSuite() +
                    " cipher suite.\n");
        }

        HttpRequestMessage requestMessage = null;
        try {
            requestMessage = decode(httpRequest);
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.info("requestMessage : " + requestMessage.toString());
        writeResponse("",ctx.channel(), httpRequest);
        return;
    }

    private HttpRequestMessage decode(FullHttpRequest httpRequest) throws IOException {
        HttpRequestMessage requestMessage = new HttpRequestMessage();
        requestMessage.setHttpVersion(httpRequest.getProtocolVersion().text());
        requestMessage.setUri(httpRequest.getUri());
        requestMessage.setHeader(new HashMap<String, Object>());
        requestMessage.setParameters(new HashMap<String, Object>());
        for (Map.Entry<String, String> entry : httpRequest.headers()) {
            requestMessage.getHeader().put(entry.getKey(), entry.getValue());
        }
        if (httpRequest.getMethod().equals(HttpMethod.GET)) {
            //get请求
            requestMessage.setMethod("GET");
            QueryStringDecoder decoderQuery = new QueryStringDecoder(httpRequest.getUri());
            Map<String, List<String>> uriAttributes = decoderQuery.parameters();
            for (Map.Entry<String, List<String>> attr : uriAttributes.entrySet()) {
                for (String attrVal : attr.getValue()) {
                    requestMessage.getParameters().put(attr.getKey(), attrVal);
                }
            }
        } else if (httpRequest.getMethod().equals(HttpMethod.POST)) {
            //post请求
            requestMessage.setMethod("POST");
            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(factory, httpRequest);
            if (decoder.isMultipart()) {
                while (decoder.hasNext()) {
                    InterfaceHttpData data = decoder.next();
                    if (data != null) {
                        try {
                            writeHttpData(data, requestMessage);
                        } finally {
                            data.release();
                        }
                    }
                }

            } else {
                decoder.offer(httpRequest);
                List<InterfaceHttpData> parmList = decoder.getBodyHttpDatas();
                for (InterfaceHttpData parm : parmList) {
                    Attribute attribute = (Attribute) parm;
                    requestMessage.getParameters().put(attribute.getName(), attribute.getValue());
                }
            }
            decoder.destroy();
        } else {
        }
        return requestMessage;
    }

    private void writeHttpData(InterfaceHttpData data, HttpRequestMessage requestMessage) throws IOException {

        /**
         * HttpDataType有三种类型
         * Attribute, FileUpload, InternalAttribute
         */
        if (data.getHttpDataType() == HttpDataType.Attribute) {
            Attribute attribute = (Attribute) data;
            requestMessage.getParameters().put(attribute.getName(), attribute.getValue());
        } else if (data.getHttpDataType() == HttpDataType.FileUpload) {
            String uploadFileName = getUploadFileName(data);
            FileUpload fileUpload = (FileUpload) data;
            if (fileUpload.isCompleted()) {
                // fileUpload.isInMemory();// tells if the file is in Memory
                // or on File
                // fileUpload.renameTo(dest); // enable to move into another
                // File dest
                // decoder.removeFileUploadFromClean(fileUpload); //remove
                // the File of to delete file
                String tmp = System.getProperty("java.io.tmpdir");
                if (System.getProperties().getProperty("os.name").toLowerCase().contains("window")) {
                    tmp = tmp + "\\";
                } else
                    tmp = tmp + "/";
                File dir = new File(tmp + File.separator);
                if (!dir.exists()) {
                    dir.mkdir();
                }
                File dest = new File(dir, uploadFileName);
                fileUpload.renameTo(dest);
//                requestMessage.
                requestMessage.getParameters().put(fileUpload.getName(), dest.getAbsolutePath());
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
    private void writeResponse(String o,Channel channel, HttpRequest httpRequest) {
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
        messageReceived(ctx, (FullHttpRequest) msg);
    }
}
