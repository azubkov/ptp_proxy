/*
* Copyright 2009 Red Hat, Inc.
*
* Red Hat licenses this file to you under the Apache License, version 2.0
* (the "License"); you may not use this file except in compliance with the
* License.  You may obtain a copy of the License at:
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
* License for the specific language governing permissions and limitations
* under the License.
*/
package github.com.azubkov.ptp_proxy.hex;

import github.com.azubkov.ptp_proxy.http.Utils;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;

import java.net.InetSocketAddress;
import java.util.Observer;

/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2376 $, $Date: 2010-10-25 03:24:20 +0900 (Mon, 25 Oct 2010) $
 */
public class HexDumpProxyInboundHandler extends SimpleChannelUpstreamHandler {
    private static final Logger LOG = Logger.getLogger(HexDumpProxyInboundHandler.class);

    protected final int uid = Utils.connectionCounter.getAndIncrement();
    private final ClientSocketChannelFactory cf;
    protected final String remoteHost;
    protected final int remotePort;

    // This lock guards against the race condition that overrides the
    // OP_READ flag incorrectly.
    // See the related discussion: http://markmail.org/message/x7jc6mqx6ripynqf
    protected final Object trafficLock = new Object();

    protected volatile Channel outboundChannel;

    public HexDumpProxyInboundHandler(ClientSocketChannelFactory cf, String remoteHost, int remotePort) {
        this.cf = cf;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        channelOpen(e.getChannel(), null);
    }

    public void channelOpen(final Channel inboundChannel, final Observer observer) throws Exception {
        synchronized (trafficLock) {
            // Suspend incoming traffic until connected to the remote host.
            inboundChannel.setReadable(false);
            // Start the connection attempt.
            ClientBootstrap cb = new ClientBootstrap(cf);
            ChannelPipeline pipeline = cb.getPipeline();
            updateOutboundPipeline(pipeline, inboundChannel);

            ChannelFuture f = cb.connect(new InetSocketAddress(remoteHost, remotePort));

            outboundChannel = f.getChannel();
            f.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // Connection attempt succeeded:
                        // Begin to accept incoming traffic.
                        LOG.info(String.format("[" + uid + "]" + "[" + System.currentTimeMillis() + "]" + "Successful connection to remote ip [%s] : %s", remoteHost + ":" + remotePort, future.getChannel()));
                        inboundChannel.setReadable(true);
                    } else {
                        // Close the connection if the connection attempt has failed.
                        LOG.info(String.format("[" + uid + "]" + "[" + System.currentTimeMillis() + "]" + "No connection to remote ip [%s]. Closing of client connection... : %s", remoteHost + ":" + remotePort, future.getChannel()));
                        inboundChannel.close();
                    }
                    if (observer != null) {
                        observer.update(null, future);
                    }
                }
            });
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        channelConnected(ctx.getChannel(), null);
    }

    public void channelConnected(final Channel channel, final Observer observer) throws Exception {
        LOG.info(String.format("[" + uid + "]" + "[" + System.currentTimeMillis() + "]" + "connection Accepted : %s", channel));
        if (observer != null) {
            observer.update(null, null);
        }
    }

    @Override
    public void channelInterestChanged(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        channelInterestChanged(e.getChannel(), null);
    }

    public void channelInterestChanged(final Channel channel, final Observer observer) throws Exception {
        // If inboundChannel is not saturated anymore, continue accepting
        // the incoming traffic from the outboundChannel.
        synchronized (trafficLock) {
            if (channel.isWritable() && outboundChannel != null) {
                outboundChannel.setReadable(true);
            }
        }
        if (observer != null) {
            observer.update(null, null);
        }
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        e.getCause().printStackTrace();
        closeOnFlush(e.getChannel());
    }

    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    void closeOnFlush(Channel channel) {
        if (channel.isConnected() && channel.isOpen()) {
            channel.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
        LOG.info("[" + uid + "]" + "[" + System.currentTimeMillis() + "]" + "Channel was closed : " + channel);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e)
            throws Exception {
        ChannelBuffer msg = (ChannelBuffer) e.getMessage();
//        LOG.info(">>> " + ChannelBuffers.hexDump(msg));
//        LOG.info("msg >>> " + msg);
        LOG.info("txt>>>> " + new String(msg.array()));
        synchronized (trafficLock) {
            outboundChannel.write(msg);
            // If outboundChannel is saturated, do not read until notified in
            // OutboundHandler.channelInterestChanged().
            if (!outboundChannel.isWritable()) {
                e.getChannel().setReadable(false);
            }
        }
    }

    protected void updateOutboundPipeline(ChannelPipeline p, final Channel inboundChannel) {
        p.addLast("handler", new OutboundHandler(inboundChannel));
    }

    protected class OutboundHandler extends SimpleChannelUpstreamHandler {

        protected final Channel inboundChannel;

        public OutboundHandler(Channel inboundChannel) {
            this.inboundChannel = inboundChannel;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e)
                throws Exception {
            ChannelBuffer msg = (ChannelBuffer) e.getMessage();
//            LOG.info("<<< " + ChannelBuffers.hexDump(msg));
            LOG.info("txt<<< " + new String(msg.array()));

            synchronized (trafficLock) {
                inboundChannel.write(msg);
                // If inboundChannel is saturated, do not read until notified in
                // HexDumpProxyInboundHandler.channelInterestChanged().
                if (!inboundChannel.isWritable()) {
                    e.getChannel().setReadable(false);
                }
            }
        }

        @Override
        public void channelInterestChanged(ChannelHandlerContext ctx,
                                           ChannelStateEvent e) throws Exception {
            // If outboundChannel is not saturated anymore, continue accepting
            // the incoming traffic from the inboundChannel.
            synchronized (trafficLock) {
                if (e.getChannel().isWritable()) {
                    inboundChannel.setReadable(true);
                }
            }
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            closeOnFlush(inboundChannel);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            e.getCause().printStackTrace();
            closeOnFlush(e.getChannel());
        }
    }
}
