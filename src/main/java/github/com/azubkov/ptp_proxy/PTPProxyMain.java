package github.com.azubkov.ptp_proxy;

import github.com.azubkov.ptp_proxy.hex.HexDumpProxyPipelineFactory;
import github.com.azubkov.ptp_proxy.http.HttpProxyPipelineFactory;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/*run examples :
7777 192.168.201.110 8090
9028 google.com 9028
* */
public class PTPProxyMain {
    private static final Logger LOG = Logger.getLogger(PTPProxyMain.class);

    public static void main(String[] args) throws Exception {
        // Validate command line options.
        if (args.length != 3) {
            LOG.error("Usage: " + PTPProxyMain.class.getSimpleName() +
                            " <local port> <remote host> <remote port>");
            return;
        }
        // Parse command line options.
        int localPort = Integer.parseInt(args[0]);
        String remoteHost = args[1];
        int remotePort = Integer.parseInt(args[2]);
        LOG.info("[connection uid][current time mills]");
        LOG.info(String.format("Proxying *:%d to %s:%d ...", localPort, remoteHost, remotePort));
        // Configure the bootstrap.
        Executor executor = Executors.newCachedThreadPool();
        ServerBootstrap sb = new ServerBootstrap(
                new NioServerSocketChannelFactory(executor, executor));
        // Set up the event pipeline factory.
        ClientSocketChannelFactory cf =
                new NioClientSocketChannelFactory(executor, executor);
//        sb.setPipelineFactory(
//                new HexDumpProxyPipelineFactory(cf, remoteHost, remotePort));
//        sb.setPipelineFactory(
//                new HttpProxyPipelineFactory(cf, remoteHost, remotePort));
        final ProtocolDetector protocolDetector = new ProtocolDetector(
                new HttpProxyPipelineFactory(cf, remoteHost, remotePort),
                new HexDumpProxyPipelineFactory(cf, remoteHost, remotePort)
        );
        sb.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("detector", protocolDetector);
                return pipeline;
            }
        });

        // Start up the server.
        sb.bind(new InetSocketAddress(localPort));
    }
}