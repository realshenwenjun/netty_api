package io.netty.core;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

import javax.net.ssl.SSLEngine;

/**
 * Created by Administrator on 2016/11/27.
 */
public class HttpServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = ch.pipeline();

        if (HttpServer.isSSL) {
            SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
            engine.setNeedClientAuth(true); //ssl双向认证
            engine.setUseClientMode(false);
            engine.setWantClientAuth(true);
            engine.setEnabledProtocols(new String[]{"SSLv3"});
            pipeline.addLast("ssl", new SslHandler(engine));
        }

        /**
         * http-request解码器
         * http服务器端对request解码
         */
        pipeline.addLast("decoder", new HttpRequestDecoder());
        /**
         * 这个放在decode之后
         */
        pipeline.addLast("aggregator", new HttpObjectAggregator(1048576));
        /**
         * http-response编码器
         * http服务器端对response编码
         */
        pipeline.addLast("encoder", new HttpResponseEncoder());

        pipeline.addLast("chunked", new ChunkedWriteHandler());

        /**
         * 压缩
         * Compresses an HttpMessage and an HttpContent in gzip or deflate encoding
         * while respecting the "Accept-Encoding" header.
         * If there is no matching encoding, no compression is done.
         */
        pipeline.addLast("deflater", new HttpContentCompressor());

        pipeline.addLast("multipart", new HttpUploadServerHandler());
//        pipeline.addLast("handler", new HttpServerHandler());
        /**
         * 具体查看配置 http://www.tuicool.com/articles/yuEbiur
         */
    }
}
