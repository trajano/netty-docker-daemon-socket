package foo;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.codec.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class DockerSwarmDiscoveryClient implements DiscoveryClient {

    @Autowired
    private EventLoopGroup theEventLoopGroup;
//
//    public void uds2() {
//        /*
//         * Does not work because WebFlux does not allow creation of non-TCP connections to HTTP
//         */
//        System.out.println(WebClient.builder()
//            .clientConnector(new ReactorClientHttpConnector(theHttpClient))
//            .build()
//            .get()
//            .uri("http://daemon/services")
//            .exchange()
//            .block()
//            .bodyToMono(String.class)
//            .block());
//    }

    @PostConstruct
    public void uds() throws Exception {
        io.netty.bootstrap.Bootstrap bootstrap = new io.netty.bootstrap.Bootstrap();
        bootstrap
            .group(theEventLoopGroup)
            .channel(EpollDomainSocketChannel.class)
            .handler(new ChannelInitializer<>() {
                @Override
                public void initChannel(final Channel ch) throws Exception {

                    ch
                        .pipeline()
                        .addLast(new HttpClientCodec())
                        .addLast(new HttpContentDecompressor())
                        .addLast(new SimpleChannelInboundHandler<HttpObject>() {
                            private StringBuilder messageBuilder = new StringBuilder();

                            @Override
                            public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
                                if (msg instanceof HttpContent) {
                                    HttpContent content = (HttpContent) msg;
                                    messageBuilder.append(content.content().toString(StandardCharsets.UTF_8));
                                    if (msg instanceof LastHttpContent) {
                                        System.out.println(messageBuilder);
                                    }
                                } else {
                                    System.out.println(msg.getClass());
                                }
                            }
                        });
                }
            });
        final Channel channel = bootstrap.connect(new DomainSocketAddress("/var/run/docker.sock")).sync().channel();

        final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/services", Unpooled.EMPTY_BUFFER);
        request.headers().set(HttpHeaderNames.HOST, "daemon");
        channel.writeAndFlush(request);
        channel.closeFuture().sync();
    }

    @Override
    public String description() {
        return getClass().toString();
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return serviceInstanceMap.computeIfAbsent(serviceId, s -> List.of());
    }

    private ConcurrentMap<String, List<ServiceInstance>> serviceInstanceMap = new ConcurrentHashMap<>();

    @Override
    public List<String> getServices() {
        return List.copyOf(serviceInstanceMap.keySet());
    }

}
