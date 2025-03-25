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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class ServerApp {
    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);
    private static final int PORT = 11111;
    private static final Map<String, Topic> topics = new HashMap<>();
    private static final Set<String> activeUsers = new HashSet<>();

    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(); // поток для обработки подключений
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // поток для обработки данных

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup) // указываем принимающий родительский поток сервера и дочерний поток клиента
                    .channel(NioServerSocketChannel.class)// указываем тип канала для принятия новых TCP/IP подключений
                    .childHandler(new ChannelInitializer<SocketChannel>() { // создаем новое подключение для каждого клиент-соединения
                        @Override
                        protected void initChannel(SocketChannel ch){
                            ch.pipeline().addLast( new StringDecoder(), new StringEncoder(), new ServerHandler()); // добавляем в очередь по порядку: раскодирование входящих строк, кодирование исходящих, наш обработчик
                            logger.info("Новое подключение: {}", ch.remoteAddress());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128) // макс. количество подключений
                    .childOption(ChannelOption.SO_KEEPALIVE, true); // поддержка соединения

            ChannelFuture future = bootstrap.bind(PORT).sync();
            logger.info("Сервер запущен на порту {}", PORT);
            System.out.println("Порт " + PORT + " запущен");
            future.channel().closeFuture().sync(); // ожидание завершения работы канала
        } catch (InterruptedException e) {
            logger.error("Сервер был прерван: {}", e.getMessage(), e);
            throw new RuntimeException("Соединение было разорвано");
        } finally { // закрываем потоки, дождавшись обработки всех сообщений
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            logger.info("Работа сервера была остановлена");
        }
    }

    public static synchronized boolean loginUser(String username) {
        if (activeUsers.contains(username)) {
            return false;
        }
        activeUsers.add(username);
        logger.info("Активных пользователей: {}", activeUsers.size());
        return true;
    }

    public static synchronized void logoutUser(String username) {
        logger.info("Активных пользователей: {}", activeUsers.size());
        activeUsers.remove(username);
    }

    public static Map<String, Topic> getTopics(){
        return topics;
    }

    public static Set<String> getActiveUsers(){
        return activeUsers;
    }

    public static void exit() {
        logger.info("Завершение работы сервера...");
        System.exit(0);
    }

    public static synchronized void save(String filename){
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT); // форматирование json

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
            logger.info("Данные были успешно сохранены в файл {}", filename);
        }catch (IOException e){
            logger.error("Ошибка при попытке сохранения данных в файл {}: {}", filename, e.getMessage(), e);
        }
    }

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
            topics.clear(); // очищаем данные после передачи объектов Object

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
                    topic.addVote(vote); // помещаем объекты Vote
                }
            }
        }catch (IOException e){
            logger.error("Ошибка при попытке загрузки данных из файла {}: {}", filename, e.getMessage(), e);
        }
    }
}
