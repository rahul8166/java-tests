package com.tests.undertow;

import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

public class ReverseProxyServer {
    private static final Logger logger = LoggerFactory.getLogger(ReverseProxyServer.class);
    
    public static void main(final String[] args) {
        List<Integer> hosts = List.of(8091, 8092, 8093, 8094, 8095, 8096, 8097, 8098, 8099, 8100);
        hosts.forEach(port -> newServer(port, "Host-Server-On-Port-" + port).start());

        LoadBalancingProxyClient lb = new LoadBalancingProxyClient();
        hosts.forEach(port -> lb.addHost(URI.create("http://localhost:" + port)));
        lb.setConnectionsPerThread(20);

        Undertow reverseProxy = Undertow.builder()
                                    .addHttpListener(8080, "localhost")
                                    .setIoThreads(6)
                                    .setHandler(ProxyHandler.builder().setProxyClient(lb)
                                    .setMaxRequestTime(30000).build())
                                    .build();
        reverseProxy.start();
        logger.info("Undertow Reverse Proxy server started : http://localhost:8080/");
    }

    private static Undertow newServer(int port, String hostName) {
        return Undertow.builder().addHttpListener(port, "localhost").setHandler(new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                logger.info("Request Headers received on Host-Server-On-Port-{} \n {}", port, exchange.getRequestHeaders());
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/html");
                StringBuilder sb = new StringBuilder("<h1>"+hostName+"</h1><h4>Request Headers</h4>");
                HeaderMap hm = exchange.getRequestHeaders();
                for(HttpString name : hm.getHeaderNames()) {
                    if(name.toString().startsWith("X-Forwarded-")){
                        sb.append("<p>").append(name).append(" : ").append(hm.get(name)).append("</p>");
                    }
                }
                exchange.getResponseSender().send(sb.toString());
            }
        }).build();
    }
}
