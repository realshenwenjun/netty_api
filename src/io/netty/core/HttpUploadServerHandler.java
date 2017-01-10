/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.core;

import io.netty.Exception.RouteNotFoundException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.IncompatibleDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.CharsetUtil;
import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;

public class HttpUploadServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private ApplicationContext cfx;
    private DefaultRoute route;

    private static final Logger logger = org.apache.log4j.Logger.getLogger(HttpUploadServerHandler.class);

    private HttpRequest request;

    private boolean readingChunks;

    private HttpRequestMessage requestMessage;

    private static final HttpDataFactory factory =
            new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // Disk if size exceed

    private HttpPostRequestDecoder decoder;

    static {
        DiskFileUpload.deleteOnExitTemporaryFile = false; // should delete file
        // on exit (in normal
        // exit)
        DiskFileUpload.baseDirectory = null; // system temp directory
        DiskAttribute.deleteOnExitTemporaryFile = false; // should delete file on
        // exit (in normal exit)
        DiskAttribute.baseDirectory = null; // system temp directory
    }

    public HttpUploadServerHandler() {
    }

    public HttpUploadServerHandler(ApplicationContext applicationContext, DefaultRoute route) {
        this.cfx = applicationContext;
        this.route = route;
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (decoder != null) {
            decoder.cleanFiles();
        }
        if (requestMessage != null) {
            requestMessage = null;
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (HttpServer.isSSL) {
            logger.info("Your session is protected by " +
                    ctx.pipeline().get(SslHandler.class).engine().getSession().getCipherSuite() +
                    " cipher suite.\n");
        }
        if (msg instanceof HttpRequest) {
            HttpRequest request = this.request = (HttpRequest) msg;
            requestMessage = new HttpRequestMessage();
            requestMessage.setHttpVersion(request.getProtocolVersion().text());
            requestMessage.setUri(request.getUri().substring(0, request.getUri().indexOf("?")));
            requestMessage.setHeader(new HashMap<String, Object>());
            requestMessage.setParameters(new HashMap<String, Object>());
            for (Map.Entry<String, String> entry : request.headers()) {
                requestMessage.getHeader().put(entry.getKey(), entry.getValue());
            }
            Set<Cookie> cookies;
            String value = request.headers().get(COOKIE);
            if (value == null) {
                cookies = Collections.emptySet();
            } else {
                cookies = CookieDecoder.decode(value);
            }
            requestMessage.getHeader().put("Cookie", cookies);

            QueryStringDecoder decoderQuery = new QueryStringDecoder(request.getUri());
            Map<String, List<String>> uriAttributes = decoderQuery.parameters();
            for (Entry<String, List<String>> attr : uriAttributes.entrySet()) {
                for (String attrVal : attr.getValue()) {
                    requestMessage.getParameters().put(attr.getKey(), attrVal);
                }
            }
            requestMessage.setMethod(request.getMethod().name());
            if (request.getMethod().equals(HttpMethod.GET)) {
                // GET Method: should not try to create a HttpPostRequestDecoder
                // So stop here
                requestMessage.setMethod("GET");
                // Not now: LastHttpContent will be sent writeResponse(ctx.channel());
                return;
            }

            try {
                decoder = new HttpPostRequestDecoder(factory, request);
            } catch (ErrorDataDecoderException e1) {
                e1.printStackTrace();
                writeResponse(ctx.channel());
                ctx.channel().close();
                return;
            } catch (IncompatibleDataDecoderException e1) {
                writeResponse(ctx.channel());
                return;
            }

            readingChunks = HttpHeaders.isTransferEncodingChunked(request);
            if (readingChunks) {
                // Chunk version
                readingChunks = true;
            }
        }

        // check if the decoder was constructed before
        // if not it handles the form get
        if (decoder != null) {
            if (msg instanceof HttpContent) {
                // New chunk is received
                HttpContent chunk = (HttpContent) msg;
                try {
                    decoder.offer(chunk);
                } catch (ErrorDataDecoderException e1) {
                    e1.printStackTrace();
                    writeResponse(ctx.channel());
                    ctx.channel().close();
                    return;
                }
                readHttpDataChunkByChunk();
                // example of reading only if at the end
                if (chunk instanceof LastHttpContent) {
                    writeResponse(ctx.channel());
                    readingChunks = false;

                    reset();
                }
            }
        } else {
            writeResponse(ctx.channel());
        }
    }

    private void reset() {
        request = null;
        // destroy the decoder to release all resources
        decoder.destroy();
        decoder = null;
    }

    /**
     * Example of reading request by chunk and getting values from chunk to chunk
     */
    private void readHttpDataChunkByChunk() throws IOException {
        try {
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
        } catch (EndOfDataDecoderException e1) {
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

    private void writeResponse(Channel channel) throws RouteNotFoundException, IOException {
        ByteBuf buf = null;
        FullHttpResponse response = null;
        try {
            buf = copiedBuffer(route.handle(requestMessage), CharsetUtil.UTF_8);
        } catch (RouteNotFoundException r) {
            buf = copiedBuffer("", CharsetUtil.UTF_8);
            response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, buf);
        } catch (IOException i) {
            buf = copiedBuffer("", CharsetUtil.UTF_8);
            response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, buf);
        }
        // Decide whether to close the connection or not.
        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.headers().get(CONNECTION))
                || request.getProtocolVersion().equals(HttpVersion.HTTP_1_0)
                && !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.headers().get(CONNECTION));

        // Build the response object.
        if (response == null)
            response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.headers().set(CONTENT_LENGTH, buf.readableBytes());
        }

        Set<Cookie> cookies;
        String value = request.headers().get(COOKIE);
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
}
