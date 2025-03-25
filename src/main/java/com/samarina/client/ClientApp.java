package com.samarina.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.util.Scanner;

public class ClientApp {
    public static void main(String[] args) {
        EventLoopGroup group = new NioEventLoopGroup(); // поток для передачи данных

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new StringDecoder(), new StringEncoder(), new ClientHandler());
                        }
                    });

            ChannelFuture future = bootstrap.connect("localhost", 11111).sync(); // настраиваем подключение к серверу
            Channel channel = future.channel();
            System.out.println("Подключение успешно");
            System.out.println("Для просмотра команд введите help");

            Scanner scanner = new Scanner(System.in);
            while(true){
                String prompt = scanner.nextLine();
                channel.writeAndFlush(prompt);
            }

        }catch (InterruptedException e) {
            throw new RuntimeException("Соединение было разорвано");
        }finally {
            group.shutdownGracefully();
        }
    }
}
