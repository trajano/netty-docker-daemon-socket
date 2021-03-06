package foo;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.codec.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

@SpringBootApplication
public class CheckDockerSocket {

    @Autowired
    private DiscoveryClient discoveryClient;

    public static void main(String[] args) {
        SpringApplication.run(CheckDockerSocket.class, args);
    }

    @Bean(destroyMethod = "shutdownGracefully")
    public EventLoopGroup eventLoopGroup() {
        return new EpollEventLoopGroup();
    }

    //    @Bean
//    public TcpClient tcpClient(final EventLoopGroup eventLoopGroup) {
//        return TcpClient.create(new ConnectionProvider() {
//            @Override
//            public Mono<? extends Connection> acquire(Bootstrap bootstrap) {
//                return null;
//            }
//        })
//    }
//    @Bean
//    public HttpClient httpClient(TcpClient tcpClient) {
//        return HttpClient.create(tcpClient);
//    }

    @Bean
    public TcpClient tcpClient(final EventLoopGroup eventLoopGroup) {
        return TcpClient.create(bootstrap -> {
            try {
                return Mono.just(
                    Connection.from(bootstrap
                        .group(eventLoopGroup)
                        .channel(EpollDomainSocketChannel.class)
                        .connect(new DomainSocketAddress("/var/run/docker.sock"))
                        .sync()
                        .channel()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
    }

    public void tcp() throws Exception {
        io.netty.bootstrap.Bootstrap bootstrap = new io.netty.bootstrap.Bootstrap();
//        Http2ClientInitializer initializer = new Http2ClientInitializer(sslCtx, Integer.MAX_VALUE);
//        bootstrap
//                .group(new EpollEventLoopGroup())
//                .channel(EpollDomainSocketChannel.class)
//                .handler(channel-> {
//
//                });
//        DefaultFullHttpRequest
        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        try {
            bootstrap
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
//                    .option(ChannelOption.SO_KEEPALIVE, true)
                .remoteAddress("daemon", 2375)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        System.out.println("INIT" + socketChannel);
                        socketChannel
                            .pipeline()
                            .addLast(new HttpClientCodec())
                            .addLast(new SimpleChannelInboundHandler<HttpObject>() {
                                private StringBuilder messageBuilder = new StringBuilder();

                                @Override
                                protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) throws Exception {
                                    System.out.println(channelHandlerContext + " " + httpObject);
                                }
                            });
                    }
                });
            final Channel channel = bootstrap.connect().sync().channel();

            final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/services", Unpooled.EMPTY_BUFFER);
            request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            request.headers().set(HttpHeaderNames.HOST, "daemon");
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
            channel.writeAndFlush(request);
            System.out.println("sent");
            channel.closeFuture().sync();
        } finally {
            eventLoopGroup.shutdownGracefully();
            System.out.println("DONE");
        }
    }

    public void xinit() throws Exception {
        io.netty.bootstrap.Bootstrap bootstrap = new io.netty.bootstrap.Bootstrap();
//        Http2ClientInitializer initializer = new Http2ClientInitializer(sslCtx, Integer.MAX_VALUE);
//        bootstrap
//                .group(new EpollEventLoopGroup())
//                .channel(EpollDomainSocketChannel.class)
//                .handler(channel-> {
//
//                });
//        DefaultFullHttpRequest
        bootstrap
            .group(new EpollEventLoopGroup())
            .channel(EpollDomainSocketChannel.class)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .remoteAddress(new DomainSocketAddress("/var/run/docker.sock"))
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) throws Exception {
                    socketChannel
                        .pipeline()
                        .addLast(new SimpleChannelInboundHandler<HttpObject>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) throws Exception {
                                System.out.println(httpObject);
                            }
                        });
                }
            });
        final Channel channel = bootstrap.connect().sync().channel();

        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/services", Unpooled.EMPTY_BUFFER);
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        channel.writeAndFlush(request);
        channel.closeFuture().sync();
        System.out.println("DONE");
    }

}
