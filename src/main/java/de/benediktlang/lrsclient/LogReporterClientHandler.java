package de.benediktlang.lrsclient;

import de.benediktlang.lrsproto.LrsProto;
import org.apache.log4j.Logger;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.timeout.ReadTimeoutException;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LogReporterClientHandler extends SimpleChannelUpstreamHandler implements LogMonitor.RPTMonitorListener {
    private static final Logger LOG = Logger.getLogger(LogReporterClientHandler.class);
    final ClientBootstrap bootstrap;
    private final Timer timer;
    private final String profile;
    private long startTime = -1;
    private Channel currentChannel;
    private LogMonitor monitor;

    public LogReporterClientHandler(ClientBootstrap bootstrap, Timer timer, LogMonitor monitor, String profile) {
        this.bootstrap = bootstrap;
        this.timer = timer;
        this.profile = profile;
    }

    public Channel getCurrentChannel() {
        return currentChannel;
    }

    private InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) bootstrap.getOption("remoteAddress");
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        LOG.debug("Channel disconnected");
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        LOG.debug("Channel closed, Sleeping for: " + LogReporterClient.RECONNECT_DELAY + "s");
        timer.newTimeout(new TimerTask() {
            public void run(Timeout timeout) throws Exception {
                LOG.debug("Reconnecting to: " + getRemoteAddress());
                bootstrap.connect();
            }
        }, LogReporterClient.RECONNECT_DELAY, TimeUnit.SECONDS);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        LOG.debug("Channel open");

    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (startTime < 0) {
            startTime = System.currentTimeMillis();
        }

        LOG.debug("Connected to: " + getRemoteAddress());
        currentChannel = ctx.getChannel();
        LrsProto.Msg.Builder builder = LrsProto.Msg.newBuilder();
        builder.setProfile("Profile");
        ctx.getChannel().write(builder.build());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        Throwable cause = e.getCause();
        if (cause instanceof ConnectException) {
            startTime = -1;
            LOG.debug("Failed to connect: " + cause.getMessage());
        }
        if (cause instanceof ReadTimeoutException) {
            // The connection was OK but there was no traffic for last period.
            LOG.debug("Disconnecting due to no inbound traffic");
        } else {
            cause.printStackTrace();
        }
        ctx.getChannel().close();
    }

    @Override
    public void changed(List<String> newLines) {
        LOG.debug("File changed");
        if (currentChannel != null && currentChannel.isOpen()) {
            LrsProto.Msg.Builder builder = LrsProto.Msg.newBuilder();
            builder.setProfile(profile);
            builder.addAllMessage(newLines);
            currentChannel.write(builder.build());
            LOG.debug("Notified");
        } else {
            LOG.debug("Channel not ready");
        }
    }
}
