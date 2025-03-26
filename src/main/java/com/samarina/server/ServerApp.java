package com.samarina.server;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.*;
import java.io.File;
import java.io.IOException;

import com.samarina.model.Topic;
import com.samarina.model.Vote;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ServerApp {
    private static final Logger log = LoggerFactory.getLogger(ServerApp.class);
    private static final int PORT = 8080;
    @Getter
    private static final Map<String, Topic> topics = new HashMap<>();
    @Getter
    private static final Set<String> activeUsers = new HashSet<>();

    public static void main(String[] args) {
        //обработка подключений
        EventLoopGroup connectionGroup = new NioEventLoopGroup();

        // обработка входящих данных
        EventLoopGroup dataGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(connectionGroup, dataGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch){
                            ch.pipeline().addLast(
                                    new StringDecoder(),
                                    new StringEncoder(),
                                    new ServerHandler());
                            log.info("Новое подключение: {}", ch.remoteAddress());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(PORT).sync();
            log.info("Сервер запущен на порту {}", PORT);
            System.out.println("Сервер запущен на порту " +  PORT);
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("Сервер был прерван: {}", e.getMessage(), e);
            throw new RuntimeException("Соединение разорвано");
        } finally {
            dataGroup.shutdownGracefully();
            connectionGroup.shutdownGracefully();
            log.info("Сервер остановлен");
        }
    }

    public static synchronized boolean loginUser(String username) {
        if (activeUsers.contains(username)) {
            return false;
        }
        activeUsers.add(username);
        log.info("Активных пользователей: {}", activeUsers.size());
        return true;
    }

    public static synchronized void logoutUser(String username) {
        log.info("Активных пользователей: {}", activeUsers.size());
        activeUsers.remove(username);
    }

    public static void exit() {
        log.info("Завершение работы сервера");
        System.exit(0);
    }

    public static synchronized void save(String filename){
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try{
            File dataDirectory = new File("data");
            if (!dataDirectory.exists()){
                dataDirectory.mkdir();
                log.info("Создана папка data");
            }
            File file = new File(dataDirectory, filename);

            Map<String, Object> data = new HashMap<>();
            data.put("topics", getTopics().values());

            mapper.writeValue(file, data);
            log.info("Данные сохранены в файл {}", filename);
        }catch (IOException e){
            log.error("При попытке сохранения данных произошла ошибка: {}", e.getMessage(), e);
        }
    }

    public static synchronized void load(String filename){
        ObjectMapper mapper = new ObjectMapper();

        try{
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                log.error("Папка data не найдена");
                throw new RuntimeException("Папка data не найдена");
            }

            File file = new File(dataDir, filename);
            if(!file.exists()){
                log.error("Файл {} не найден", filename);
                throw new RuntimeException("Файл " + filename + " не найден");
            }

            Map<String, Object> data = mapper.readValue(file, new TypeReference<Map<String, Object>>() {});
            topics.clear();

            List<Map<String, Object>> topicsData = (List<Map<String, Object>>) data.get("topics");
            for(Map<String, Object> topicData : topicsData){
                String topicName = (String) topicData.get("name");
                Topic topic = new Topic(topicName);
                topics.put(topicName, topic);

                Map<String, Object> votesData = (Map<String, Object>) topicData.get("allVotes");
                for(Map.Entry<String, Object> voteEntry : votesData.entrySet()){
                    String voteName = voteEntry.getKey();
                    Map<String, Object> voteData = (Map<String, Object>) voteEntry.getValue();
                    String description = (String) voteData.get("description");
                    Map<String, List<String>> options = (Map<String, List<String>>) voteData.get("options");
                    String creator = (String) voteData.get("creator");

                    Vote vote = new Vote(voteName, description, options, creator);
                    topic.addVote(vote);
                }
            }
        }catch (IOException e){
            log.error("При попытке загрузки данных произошла ошибка: {}",e.getMessage(), e);
        }
    }
}
