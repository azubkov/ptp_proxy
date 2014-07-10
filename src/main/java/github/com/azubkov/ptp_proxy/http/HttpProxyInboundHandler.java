package github.com.azubkov.ptp_proxy.http;

import github.com.azubkov.ptp_proxy.hex.HexDumpProxyInboundHandler;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;

public class HttpProxyInboundHandler extends HexDumpProxyInboundHandler {
    private static final Logger LOG = Logger.getLogger(HttpProxyInboundHandler.class);

    public HttpProxyInboundHandler(ClientSocketChannelFactory cf, String remoteHost, int remotePort) {
        super(cf, remoteHost, remotePort);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        DefaultHttpRequest msg = (DefaultHttpRequest) e.getMessage();

        msg.setHeader("Host", remoteHost + ":" + remotePort);

//        if (Utils.isJsonString(msg.getContent().array())) {
//            LOG.info("[" + uid + "]" + "JSON Request: " + Utils.httpMessageToJson(msg));
//            LOG.info("[" + uid + "]" + "JSON Request: " + Utils.httpRequestToString(msg));
//        }
        LOG.info("[" + uid + "]" + "[" + System.currentTimeMillis() + "]" + "Request: \n" + Utils.httpRequestToString(msg));


        synchronized (trafficLock) {
            outboundChannel.write(msg);
            // If outboundChannel is saturated, do not read until notified in
            // OutboundHandler.channelInterestChanged().
            if (!outboundChannel.isWritable()) {
                e.getChannel().setReadable(false);
            }
        }
    }


    @Override
    protected void updateOutboundPipeline(ChannelPipeline p, final Channel inboundChannel) {
        p.addLast("decoder", new HttpResponseDecoder());
        p.addLast("aggregator", new HttpChunkAggregator(512 * 1024));
        p.addLast("encoder", new HttpRequestEncoder());
        p.addLast("handler", new OutboundHandler(inboundChannel));
    }

    protected class OutboundHandler extends HexDumpProxyInboundHandler.OutboundHandler {

        OutboundHandler(Channel inboundChannel) {
            super(inboundChannel);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
            DefaultHttpResponse msg = (DefaultHttpResponse) e.getMessage();

            LOG.info("[" + uid + "]" + "[" + System.currentTimeMillis() + "]" + "Response: \n" + Utils.httpResponseToString(msg));

            synchronized (trafficLock) {
                inboundChannel.write(msg);
                // If inboundChannel is saturated, do not read until notified in
                // HexDumpProxyInboundHandler.channelInterestChanged().
                if (!inboundChannel.isWritable()) {
                    e.getChannel().setReadable(false);
                }
            }
        }
    }
}
