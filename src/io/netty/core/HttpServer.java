package io.netty.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by Administrator on 2016/11/27.
 */
public class HttpServer {
    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(HttpServer.class);
    private final int port;
    public static boolean isSSL;
    private static ApplicationContext cfx;
    private static DefaultRoute route;

    private static final String[] configFiles = new String[]{
            "applicationContext.xml",
//            "spring/commserver-context.xml",
    };

    public HttpServer(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        // EventLoopGroup管理的线程数可以通过构造函数设置，如果没有设置，默认取-Dio.netty.eventLoopThreads，如果该系统参数也没有指定，则为可用的CPU内核数 × 2。
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new HttpServerInitializer(cfx, route));

            Channel ch = b.bind(port).sync().channel();
            logger.info("Server start finished on " + port);
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static void initSpring() throws Exception {
        cfx = new ClassPathXmlApplicationContext(configFiles);
        route = new DefaultRoute(cfx);
    }

    public static void main(String[] args) throws Exception {
        initSpring();
        int port = 8080;
        isSSL = false;
        new HttpServer(port).run();
    }
}
