package com.tests.netty;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.CharsetUtil;

public class Http2Server {
    private static final Logger logger = LoggerFactory.getLogger(Http2Server.class);
    private static final int PORT = Integer.parseInt(System.getProperty("port", "8778"));
    private final Thread jvmShutdownHook = new Thread(this::stop, "Netty-Http2Server-JVM-shutdown-hook");
    private boolean stopped = false;
    private ExecutorService executor = Executors.newCachedThreadPool();
    EventLoopGroup parentGroup;
    EventLoopGroup childGroup;

    public Http2Server() {
        // The parentGroup channels handles the I/O for the acceptor channel, i.e. the
        // channel which is bound to the port where our server is accepting new
        // requests.
        // The acceptor channel should be handled by one and only thread
        parentGroup = new NioEventLoopGroup(1, executor);
        // The childGroup channels will be handling the I/O for the accepted connections
        // i.e. client connections.
        childGroup = new NioEventLoopGroup(200, executor);
        Runtime.getRuntime().addShutdownHook(jvmShutdownHook);
    }

    public void start() {
        SslContext sslCtx = setupSSL();
        try {
            ServerBootstrap b = new ServerBootstrap();

            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.option(ChannelOption.SO_LINGER, -1);
            b.option(ChannelOption.TCP_NODELAY, true);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.option(ChannelOption.SO_BACKLOG, 1024);

            b.group(parentGroup, childGroup).channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO)).childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            if (sslCtx != null) {
                                ch.pipeline().addLast(sslCtx.newHandler(ch.alloc()),
                                        new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_2) {
                                            @Override
                                            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
                                                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                                                    ctx.pipeline().addLast(Http2FrameCodecBuilder.forServer().build(),
                                                            new ChannelDuplexHandler() {
                                                                @Override
                                                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                                                    super.exceptionCaught(ctx, cause);
                                                                    logger.error("Exception while handling request.", cause);
                                                                    ctx.close();
                                                                }

                                                                @Override
                                                                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                                                    if (msg instanceof Http2HeadersFrame) {
                                                                        Http2HeadersFrame http2HeadersFrame = (Http2HeadersFrame) msg;
                                                                        if (http2HeadersFrame.isEndStream()) {
                                                                            ByteBuf responseData = ctx.alloc().buffer();
                                                                            String s = Files.readString(Paths.get(getClass().getClassLoader().getResource("logback.xml").toURI()));
                                                                            responseData.writeBytes(Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(s,CharsetUtil.UTF_8)));
                                                                            Http2Headers http2Headers = new DefaultHttp2Headers().status(HttpResponseStatus.OK.codeAsText());
                                                                            ctx.write(new DefaultHttp2HeadersFrame(http2Headers).stream(http2HeadersFrame.stream()));
                                                                            ctx.write(new DefaultHttp2DataFrame(responseData, true).stream(http2HeadersFrame.stream()));
                                                                        }
                                                                    } else {
                                                                        super.channelRead(ctx, msg);
                                                                    }
                                                                }

                                                                @Override
                                                                public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                                                                    ctx.flush();
                                                                }
                                                            });
                                                    return;
                                                }
                                                throw new RuntimeException("Only HTTP2 protocol supported. This protocol isnot supported - " + protocol);
                                            }
                                        });
                            }
                        }

                    });

            // Validate child handler and event loop groups are set.
            b.validate();

            Channel ch = b.bind(PORT).sync().channel();
            logger.info("Netty HTTP2 server started - https://localhost:{}/", PORT);
            ChannelFuture cf = ch.closeFuture();
            try {
                cf.sync();
            } catch (Exception e) {
                logger.error("Failed to bind port {}. Check if it is already occupied.", PORT);
                throw e;
            }
        } catch (Exception e) {
            logger.error("Failed to start Netty HTTP2 server.", e);
        }
    }

    public void stop() {
        logger.info("Shutting down Netty Http2Server.");
        if (stopped) {
            logger.info("Already stopped");
            return;
        }
        logger.info("Shutting down event loops");
        parentGroup.shutdownGracefully();
        childGroup.shutdownGracefully();
        try {
            parentGroup.awaitTermination(30, TimeUnit.SECONDS);
            childGroup.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("Shutting down event loops interrupted.", e);
        }
        try {
            Runtime.getRuntime().removeShutdownHook(jvmShutdownHook);
        } catch (IllegalStateException e) {
            logger.warn("Failed to remove shutdown hook", e);
        }
        stopped = true;
        logger.info("Completed Netty Http2Server shutdown.");
    }

    private SslContext setupSSL() {
        try {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            SslProvider provider = chooseSslProvider();
            logger.info("Using SSL Provider - {}",
                    provider != null ? provider.name() : "Could not recognize SSL Provider.");
            return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).sslProvider(provider)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(Protocol.ALPN,
                            SelectorFailureBehavior.NO_ADVERTISE, SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2, ApplicationProtocolNames.HTTP_1_1))
                    .build();
        } catch (CertificateException | SSLException e) {
            throw new RuntimeException("Failed to setup SSL Context.", e);
        }
    }

    private SslProvider chooseSslProvider() {
        // OpenSSL supports ALPN since version 1.0.2 released in January 2015
        SslProvider sslProvider;
        if (OpenSsl.isAvailable() && SslProvider.isAlpnSupported(SslProvider.OPENSSL)) {
            sslProvider = SslProvider.OPENSSL;
        } else {
            sslProvider = SslProvider.JDK;
        }
        return sslProvider;
    }

    public static void main(String[] args) {
        Http2Server http2Server = new Http2Server();
        http2Server.start();
    }
}