package com.tests.undertow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;

public class WebsocketServer {
    private static final Logger logger = LoggerFactory.getLogger(WebsocketServer.class);
    private static final int PORT = Integer.parseInt(System.getProperty("port", "8779"));

    public static void main(String[] args) {
        Undertow server = Undertow.builder().addHttpListener(PORT, "localhost")
                .setHandler(Handlers.path().addPrefixPath("/myapp", Handlers.websocket(new WSCallback()))
                        .addPrefixPath("/", staticResourceHandler().addWelcomeFiles("index.html")))
                .build();
        server.start();
        logger.info("Undertow Websocket server started : http://localhost:{}/", PORT);
    }

    private static ResourceHandler staticResourceHandler() {
        return Handlers.resource(
            new ClassPathResourceManager(
                WebsocketServer.class.getClassLoader(),
                WebsocketServer.class.getPackage()));
    }
}

class WSCallback implements WebSocketConnectionCallback {

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        channel.getReceiveSetter().set(new AbstractReceiveListener() {

            @Override
            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) {
                WebSockets.sendText(message.getData(), channel, null);
            }
        });
        channel.resumeReceives();
    }
}