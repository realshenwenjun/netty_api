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

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;

public class HttpUploadServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = Logger.getLogger(HttpUploadServerHandler.class.getName());

    private HttpRequest request;

    private boolean readingChunks;

    private HttpRequestMessage requestMessage;

    private static final HttpDataFactory factory =
            new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // Disk if size exceed

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
        logger.info("" + msg);
        if (HttpServer.isSSL) {
            logger.info("Your session is protected by " +
                    ctx.pipeline().get(SslHandler.class).engine().getSession().getCipherSuite() +
                    " cipher suite.\n");
        }
        if (msg instanceof HttpRequest) {
            HttpRequest request = this.request = (HttpRequest) msg;
            requestMessage = new HttpRequestMessage();
            requestMessage.setHttpVersion(request.getProtocolVersion().text());
            requestMessage.setUri(request.getUri());
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

    private void writeResponse(Channel channel) {
        // Convert the response content to a ChannelBuffer.
        ByteBuf buf = copiedBuffer(requestMessage.toString(), CharsetUtil.UTF_8);

        // Decide whether to close the connection or not.
        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.headers().get(CONNECTION))
                || request.getProtocolVersion().equals(HttpVersion.HTTP_1_0)
                && !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.headers().get(CONNECTION));

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
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

    private void writeMenu(ChannelHandlerContext ctx) {
        // print several HTML forms
        // Convert the response content to a ChannelBuffer.
//        responseContent.setLength(0);
//
//        // create Pseudo Menu
//        responseContent.append("<html>");
//        responseContent.append("<head>");
//        responseContent.append("<title>Netty Test Form</title>\r\n");
//        responseContent.append("</head>\r\n");
//        responseContent.append("<body bgcolor=white><style>td{font-size: 12pt;}</style>");
//
//        responseContent.append("<table border=\"0\">");
//        responseContent.append("<tr>");
//        responseContent.append("<td>");
//        responseContent.append("<h1>Netty Test Form</h1>");
//        responseContent.append("Choose one FORM");
//        responseContent.append("</td>");
//        responseContent.append("</tr>");
//        responseContent.append("</table>\r\n");
//
//        // GET
//        responseContent.append("<CENTER>GET FORM<HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");
//        responseContent.append("<FORM ACTION=\"/formget\" METHOD=\"GET\">");
//        responseContent.append("<input type=hidden name=getform value=\"GET\">");
//        responseContent.append("<table border=\"0\">");
//        responseContent.append("<tr><td>Fill with value: <br> <input type=text name=\"info\" size=10></td></tr>");
//        responseContent.append("<tr><td>Fill with value: <br> <input type=text name=\"secondinfo\" size=20>");
//        responseContent
//                .append("<tr><td>Fill with value: <br> <textarea name=\"thirdinfo\" cols=40 rows=10></textarea>");
//        responseContent.append("</td></tr>");
//        responseContent.append("<tr><td><INPUT TYPE=\"submit\" NAME=\"Send\" VALUE=\"Send\"></INPUT></td>");
//        responseContent.append("<td><INPUT TYPE=\"reset\" NAME=\"Clear\" VALUE=\"Clear\" ></INPUT></td></tr>");
//        responseContent.append("</table></FORM>\r\n");
//        responseContent.append("<CENTER><HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");
//
//        // POST
//        responseContent.append("<CENTER>POST FORM<HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");
//        responseContent.append("<FORM ACTION=\"/formpost\" METHOD=\"POST\">");
//        responseContent.append("<input type=hidden name=getform value=\"POST\">");
//        responseContent.append("<table border=\"0\">");
//        responseContent.append("<tr><td>Fill with value: <br> <input type=text name=\"info\" size=10></td></tr>");
//        responseContent.append("<tr><td>Fill with value: <br> <input type=text name=\"secondinfo\" size=20>");
//        responseContent
//                .append("<tr><td>Fill with value: <br> <textarea name=\"thirdinfo\" cols=40 rows=10></textarea>");
//        responseContent.append("<tr><td>Fill with file (only file name will be transmitted): <br> "
//                + "<input type=file name=\"myfile\">");
//        responseContent.append("</td></tr>");
//        responseContent.append("<tr><td><INPUT TYPE=\"submit\" NAME=\"Send\" VALUE=\"Send\"></INPUT></td>");
//        responseContent.append("<td><INPUT TYPE=\"reset\" NAME=\"Clear\" VALUE=\"Clear\" ></INPUT></td></tr>");
//        responseContent.append("</table></FORM>\r\n");
//        responseContent.append("<CENTER><HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");
//
//        // POST with enctype="multipart/form-data"
//        responseContent.append("<CENTER>POST MULTIPART FORM<HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");
//        responseContent.append("<FORM ACTION=\"/formpostmultipart\" ENCTYPE=\"multipart/form-data\" METHOD=\"POST\">");
//        responseContent.append("<input type=hidden name=getform value=\"POST\">");
//        responseContent.append("<table border=\"0\">");
//        responseContent.append("<tr><td>Fill with value: <br> <input type=text name=\"info\" size=10></td></tr>");
//        responseContent.append("<tr><td>Fill with value: <br> <input type=text name=\"secondinfo\" size=20>");
//        responseContent
//                .append("<tr><td>Fill with value: <br> <textarea name=\"thirdinfo\" cols=40 rows=10></textarea>");
//        responseContent.append("<tr><td>Fill with file: <br> <input type=file name=\"myfile\">");
//        responseContent.append("</td></tr>");
//        responseContent.append("<tr><td><INPUT TYPE=\"submit\" NAME=\"Send\" VALUE=\"Send\"></INPUT></td>");
//        responseContent.append("<td><INPUT TYPE=\"reset\" NAME=\"Clear\" VALUE=\"Clear\" ></INPUT></td></tr>");
//        responseContent.append("</table></FORM>\r\n");
//        responseContent.append("<CENTER><HR WIDTH=\"75%\" NOSHADE color=\"blue\"></CENTER>");
//
//        responseContent.append("</body>");
//        responseContent.append("</html>");

        ByteBuf buf = copiedBuffer(requestMessage.toString(), CharsetUtil.UTF_8);
        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);

        response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().set(CONTENT_LENGTH, buf.readableBytes());

        // Write the response.
        ctx.channel().writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.log(Level.WARNING, requestMessage.toString(), cause);
        ctx.channel().close();
    }
}
