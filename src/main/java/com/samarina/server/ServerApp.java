package com.samarina.server;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.util.*;
import java.io.IOException;

import com.fasterxml.jackson.databind.SerializationFeature;
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

public class ServerApp {
    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);
    private static final int PORT = 8080;
    @Getter
    private static final Map<String, Topic> topics = new HashMap<>();
    @Getter
    private static final Set<String> activeUsers = new HashSet<>();

    public static void main(String[] args) {
        //файл для записи логов
        new File("logs").mkdirs();
        //обработка подключений
        EventLoopGroup connectGroup = new NioEventLoopGroup();

        //обработка данных
        EventLoopGroup dataGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            //принимающий родительский и дочерний потоки сервера и клиента
            bootstrap.group(connectGroup, dataGroup)
                    //тип канала для TCP/IP подключений
                    .channel(NioServerSocketChannel.class)
                    //новое подключение для каждого клиента
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch){
                            ch.pipeline().addLast(
                                    new StringDecoder(), //раскодировка входящих строк
                                    new StringEncoder(), //кодировка исходящих
                                    new ServerHandler());
                            logger.info("Новое подключение: {}", ch.remoteAddress());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            //запуск сервера
            ChannelFuture future = bootstrap.bind(PORT).sync();
            logger.info("Сервер запущен на порту {}", PORT);
            System.out.println("Сервер запущен на порту " + PORT);

            //ожидание завершения работы сервера
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("Прерывание работы сервера: {}", e.getMessage(), e);
            throw new RuntimeException("Соединение было разорвано");
        } finally {
            dataGroup.shutdownGracefully();
            connectGroup.shutdownGracefully();
            logger.info("Сервер остановлен");
        }
    }

    //регистрация нового пользователя
    public static synchronized boolean loginNewUser(String name) {
        if (activeUsers.contains(name)) {
            return false;
        }
        activeUsers.add(name);
        logger.info("Пользователь {} зарегистрирован", name);
        logger.info("Активных пользователей: {}", activeUsers.size());
        return true;
    }

    //выход пользователя из системы
    public static synchronized void logoutUser(String name) {
        logger.info("Пользователь {} вышел из системы", name);
        logger.info("Активных пользователей: {}", activeUsers.size());
        activeUsers.remove(name);
    }

    public static void exit() {
        logger.info("Завершение работы сервера");
        System.exit(0);
    }

    //сохранение данных в файл
    public static synchronized void save(String filename){
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT); //форматирование json

        try{
            File dataDirectory = new File("data");
            if (!dataDirectory.exists()){
                dataDirectory.mkdir();
                logger.info("Создана папка data");
            }
            File file = new File(dataDirectory, filename);

            Map<String, Object> data = new HashMap<>(); // где храним все объекты
            data.put("topics", getTopics().values()); // помещаем все разделы

            mapper.writeValue(file, data);
            logger.info("Данные сохранены в {}", filename);
        }catch (IOException e){
            logger.error("Ошибка при попытке сохранения данных: {}", e.getMessage(), e);
        }
    }

    //загрузка из файла
    public static synchronized void load(String filename){
        ObjectMapper mapper = new ObjectMapper();

        try{
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                logger.error("Папка data не найдена");
                throw new RuntimeException("Папка data не найдена");
            }

            File file = new File(dataDir, filename);
            if(!file.exists()){
                logger.error("Файл {} не найден", filename);
                throw new RuntimeException("Файл " + filename + " не найден");
            }

            Map<String, Object> data = mapper.readValue(file, new TypeReference<Map<String, Object>>() {});
            topics.clear();//очистка данных после передачи обьектов

            List<Map<String, Object>> topicsData = (List<Map<String, Object>>) data.get("topics");
            for(Map<String, Object> topicData : topicsData){
                String topicName = (String) topicData.get("name");
                Topic topic = new Topic(topicName);
                topics.put(topicName, topic); // помещаем объекты Topic

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
            logger.error("Ошибка при попытке загрузки данных из файла: {}", e.getMessage(), e);
        }
    }
}