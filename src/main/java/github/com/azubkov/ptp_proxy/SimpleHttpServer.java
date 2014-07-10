package github.com.azubkov.ptp_proxy;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.BigEndianHeapChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleHttpServer {
    private static final AtomicInteger GLOBAL_REQUESTS_COUNTER = new AtomicInteger(0);

    // Each run arg - is port of each server instance
    // 40050 40051 - will run two instances on 40050 and 40051
    // no args - will run 1 instance at 40050
    public static void main(String[] args) {
        Executor executor = Executors.newFixedThreadPool(16);
        ChannelFactory serverChannelFactory = new NioServerSocketChannelFactory(executor, executor);

        if (args.length == 0) {
            args = new String[]{"50050"};
        }

        for (String arg : args) {
            try {
                int port = Integer.parseInt(arg);
                startNewServer(port, serverChannelFactory);
                System.out.println(String.format("Instance on port %5d started!", port));
            } catch (Exception e) {
                System.out.println(String.format("Instance on port %s failed: %s", arg, e.getMessage()));
            }
        }
    }

    private static void startNewServer(final int port, ChannelFactory channelFactory) {
        final AtomicInteger requestsCounter = new AtomicInteger(0);
        final DefaultHttpResponse okResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        String responseString = "Hallo!";
        okResponse.setContent(new BigEndianHeapChannelBuffer(responseString.getBytes()));
        okResponse.setHeader("Content-Length", responseString.getBytes().length);

        ServerBootstrap bootstrap = new ServerBootstrap(channelFactory);
        bootstrap.setOption("keepAlive", true);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("aggregator", new HttpChunkAggregator(512 * 1024));
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("handler", new SimpleChannelUpstreamHandler() {

                    @Override
                    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                        DefaultHttpRequest request = (DefaultHttpRequest) e.getMessage();
                        System.out.println(String.format(
                                "Instance port 5%d, Instance requests: #%10d, Total requests: #%10d: [%s] %s",
                                port,
                                requestsCounter.getAndIncrement(),
                                GLOBAL_REQUESTS_COUNTER.getAndIncrement(),
                                request.getMethod(),
                                request.getUri()
                        ));
                        e.getChannel().write(okResponse);
                    }
                });
                return pipeline;
            }
        });
        bootstrap.bind(new InetSocketAddress(port));
    }
}