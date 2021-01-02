package com.tests.undertow;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.util.StringReadChannelListener;

public class ServerSentEventsServer {
    private static final Logger logger = LoggerFactory.getLogger(ServerSentEventsServer.class);
    private static final int PORT = Integer.parseInt(System.getProperty("port", "8776"));

    public static void main(final String[] args) {
        final ServerSentEventHandler sseHandler = Handlers.serverSentEvents();
        
        Undertow server = Undertow.builder()
                .addHttpListener(PORT, "localhost")
                .setHandler(Handlers.path()
                        .addPrefixPath("/sseHandler", sseHandler)
                        .addPrefixPath("/sendChatMsg", chatHandler(sseHandler))
                        .addPrefixPath("/", staticResourceHandler().addWelcomeFiles("index.html")))
                .build();
        server.start();
        logger.info("Undertow SSE server started : http://localhost:{}/", PORT);
    }

    private static ResourceHandler staticResourceHandler() {
        return Handlers.resource(
            new ClassPathResourceManager(
                ServerSentEventsServer.class.getClassLoader(),
                ServerSentEventsServer.class.getPackage()));
    }
    
    private static HttpHandler chatHandler(ServerSentEventHandler sseHandler){
        return new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                new StringReadChannelListener(exchange.getConnection().getByteBufferPool()) {

                    @Override
                    protected void stringDone(String string) {
                        for(ServerSentEventConnection h : sseHandler.getConnections()) {
                            h.send(string);
                        }
                    }

                    @Override
                    protected void error(IOException e) {
                        logger.error("Some error.", e);
                    }
                }.setup(exchange.getRequestChannel());
            }
        };
    }
}
