package github.com.azubkov.ptp_proxy;

import github.com.azubkov.ptp_proxy.hex.HexDumpProxyInboundHandler;
import github.com.azubkov.ptp_proxy.hex.HexDumpProxyPipelineFactory;
import github.com.azubkov.ptp_proxy.http.HttpProxyPipelineFactory;
import org.jboss.netty.buffer.BigEndianHeapChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@ChannelHandler.Sharable
class ProtocolDetector extends FrameDecoder {

    final HttpProxyPipelineFactory httpProxyPipelineFactory;
    final HexDumpProxyPipelineFactory hexDumpProxyPipelineFactory;

    ProtocolDetector(HttpProxyPipelineFactory httpProxyPipelineFactory, HexDumpProxyPipelineFactory hexDumpProxyPipelineFactory) {
        this.httpProxyPipelineFactory = httpProxyPipelineFactory;
        this.hexDumpProxyPipelineFactory = hexDumpProxyPipelineFactory;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, final Channel channel, ChannelBuffer buffer) throws Exception {
        // Will use the first two bytes to detect a protocol.
        if (buffer.readableBytes() < 2) {
            return null;
        }

        final int magic1 = buffer.getUnsignedByte(buffer.readerIndex());
        final int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);

        /*binary transmitting by default*/
        ChannelPipeline donorPipeline = null;
        if (isHttp(magic1, magic2)) {
            donorPipeline = httpProxyPipelineFactory.getPipeline();
        } else {
            donorPipeline = hexDumpProxyPipelineFactory.getPipeline();
        }
        ChannelPipeline p = ctx.getPipeline();
        for (String s : donorPipeline.getNames()) {
            ChannelHandler channelHandler = donorPipeline.get(s);
            p.addLast(s, channelHandler);
        }

        ctx.getPipeline().remove(this);
        // Forward the current read buffer as is to the new handlers.
        final ChannelBuffer channelBuffer = buffer.readBytes(buffer.readableBytes());

        for (String s : p.getNames()) {
            ChannelHandler channelHandler = p.get(s);
            if (channelHandler instanceof HexDumpProxyInboundHandler) {
                final AtomicInteger counter = new AtomicInteger(3);
                final Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (counter.get() == 0) {
                                byte[] array = channelBuffer.array();
                                Channels.fireMessageReceived(channel, new BigEndianHeapChannelBuffer(array));
                            }
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                };

                Observer observer = new Observer() {
                    @Override
                    public void update(Observable o, Object arg) {
                        counter.decrementAndGet();
                        r.run();
                    }
                };
                try {
                    ((HexDumpProxyInboundHandler) channelHandler).channelOpen(channel, observer);
                    ((HexDumpProxyInboundHandler) channelHandler).channelConnected(channel, observer);
                    ((HexDumpProxyInboundHandler) channelHandler).channelInterestChanged(channel, observer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private boolean isSsl(int magic1) {
        switch (magic1) {
            case 20:
            case 21:
            case 22:
            case 23:
            case 255:
                return true;
            default:
                return magic1 >= 128;
        }
    }

    private boolean isHttp(int magic1, int magic2) {
        return magic1 == 'G' && magic2 == 'E' || // GET
                magic1 == 'P' && magic2 == 'O' || // POST
                magic1 == 'P' && magic2 == 'U' || // PUT
                magic1 == 'H' && magic2 == 'E' || // HEAD
                magic1 == 'O' && magic2 == 'P' || // OPTIONS
                magic1 == 'P' && magic2 == 'A' || // PATCH
                magic1 == 'D' && magic2 == 'E' || // DELETE
                magic1 == 'T' && magic2 == 'R' || // TRACE
                magic1 == 'C' && magic2 == 'O';   // CONNECT
    }
}