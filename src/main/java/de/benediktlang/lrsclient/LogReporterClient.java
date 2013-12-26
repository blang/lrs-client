package de.benediktlang.lrsclient;

import de.benediktlang.lrsproto.LrsProto;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class LogReporterClient {
    static final int RECONNECT_DELAY = 5;
    private static final int READ_TIMEOUT = 10;
    private final String host;
    private final int port;
    private final String profile;
    private File monitorFile;
    private LogMonitor monitor;
    private LogReporterClientHandler clientHandler;

    public LogReporterClient(File monitorFile, String profile, String host, int port) {
        this.profile = profile;
        this.host = host;
        this.port = port;
        this.monitorFile = monitorFile;
    }

    public static void main(String[] args) throws IOException {
        String username = "unkown";
        if (args.length > 0) {
                username = args[0];
        }
        new LogReporterClient(new File("./testfile"), username, "127.0.0.1", 8003).start();
    }

    public void start() throws IOException {
        monitor = new LogMonitor(monitorFile);
        monitor.startMonitor();
        final Timer timer = new HashedWheelTimer();
        final ClientBootstrap bootstrap = new ClientBootstrap(
                new NioClientSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool())
        );
        clientHandler = new LogReporterClientHandler(bootstrap, timer, monitor, profile);
        monitor.addListener(clientHandler);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            //private final ChannelHandler timeoutHandler = new ReadTimeoutHandler(timer, READ_TIMEOUT);
            private final ChannelHandler frameDecoder = new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4);
            private final ChannelHandler protobufDecoder = new ProtobufDecoder(LrsProto.Msg.getDefaultInstance());
            private final ChannelHandler frameEncoder = new ProtobufVarint32LengthFieldPrepender();
            private final ChannelHandler protobufEncoder = new ProtobufEncoder();
            private final ChannelHandler handler = clientHandler;

            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(
                        //timeoutHandler,
                        frameDecoder, protobufDecoder, frameEncoder, protobufEncoder, handler);
            }
        });
        bootstrap.setOption("remoteAddress", new InetSocketAddress(host, port));
        bootstrap.connect();
    }
}
