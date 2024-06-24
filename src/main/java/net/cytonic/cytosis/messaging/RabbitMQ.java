package net.cytonic.cytosis.messaging;

import com.rabbitmq.client.*;
import net.cytonic.cytosis.Cytosis;
import net.cytonic.cytosis.config.CytosisSettings;
import net.cytonic.cytosis.data.enums.ChatChannel;
import net.cytonic.cytosis.data.enums.KickReason;
import net.cytonic.cytosis.logging.Logger;
import net.cytonic.cytosis.utils.OfflinePlayer;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.minestom.server.entity.Player;
import net.minestom.server.sound.SoundEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * A class holding a connection to the RabbitMQ server.
 * <p>
 * It serves as a wrapper around the RabbitMQ Java API,
 * facilitating the communication between other instances of Cytosis and Cynturion.
 */
@SuppressWarnings("unused")
public class RabbitMQ {

    /**
     * Creates a new RabbitMQ instance
     */
    public RabbitMQ() {
        // do nothing
    }

    private static final String CHAT_CHANNEL_QUEUE = STR."chat-channel-\{Cytosis.SERVER_ID}";
    private static final String PLAYER_KICK_QUEUE = "player-kick";
    private static final String CHAT_CHANNEL_EXCHANGE = "chat-exchange";
    private Connection connection;
    private Channel channel;

    /**
     * Initializes a connection to the RabbitMQ server.
     */
    public void initializeConnection() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(CytosisSettings.RABBITMQ_HOST);
        factory.setPassword(CytosisSettings.RABBITMQ_PASSWORD);
        factory.setUsername(CytosisSettings.RABBITMQ_USERNAME);
        factory.setPort(CytosisSettings.RABBITMQ_PORT);
        try {
            connection = factory.newConnection();
        } catch (IOException | TimeoutException e) {
            Logger.error("An error occurred whilst connecting to RabbitMQ!", e);
        }

        try {
            channel = connection.createChannel();
        } catch (IOException e) {
            Logger.error("An error occurred whilst connecting to RabbitMQ!", e);
        }
        Logger.info("Connected to RabbitMQ!");
    }

    /**
     * Creates the queues required for Cytosis to link with proxy servers
     */
    public void initializeQueues() {
        Logger.info("Initializing RabbitMQ queues...");
        try {
            channel.exchangeDeclare(CHAT_CHANNEL_EXCHANGE, BuiltinExchangeType.FANOUT);
            channel.queueDeclare(CHAT_CHANNEL_QUEUE, false, false, true, null);
            channel.queueBind(CHAT_CHANNEL_QUEUE, CHAT_CHANNEL_EXCHANGE, "");
        } catch (IOException e) {
            Logger.error("An error occurred whilst initializing the 'CHAT_CHANNEL_QUEUE'.", e);
        }
        try {
            channel.queueDeclare(PLAYER_KICK_QUEUE, false, false, false, null);
        } catch (IOException e) {
            Logger.error("An error occurred whilst initializing the 'PLAYER_KICK_QUEUE'.", e);
        }
    }

    /**
     * Closes the RabbitMQ connection
     */
    public void shutdown() {
        try {
            connection.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends a message to RabbitMQ to kick a player.
     * <p>
     * Formatting: {@code {uuid}|:|{reason}|:|{name}|:|{message}|:|{rescuable}}
     *
     * @param player  The player to kick, on this server
     * @param reason  The reason for kicking the player
     * @param message The kick message displayed
     */
    public void kickPlayer(Player player, KickReason reason, Component message) {
        // FORMAT: {uuid}|:|{reason}|:|{name}|:|{message}|:|{rescuable}
        String rawMessage = STR."\{player.getUuid()}|:|\{reason}|:|\{player.getUsername()}|:|\{JSONComponentSerializer.json().serialize(message)}|:|\{reason.isRescuable()}";
        try {
            channel.basicPublish("", PLAYER_KICK_QUEUE, null, rawMessage.getBytes());
        } catch (IOException e) {
            Logger.error(STR."An error occoured whilst attempting to kick the player \{player.getName()}.", e);
        }
    }

    /**
     * Sends a message to RabbitMQ to kick a player.
     * <p>
     * Formatting: {@code {uuid}|:|{reason}|:|{name}|:|{message}|:|{rescuable}}
     *
     * @param player  The player to kick, on another server
     * @param reason  The reason for kicking the player
     * @param message The kick message displayed
     */
    public void kickPlayer(OfflinePlayer player, KickReason reason, Component message) {
        // FORMAT: {uuid}|:|{reason}|:|{name}|:|{message}|:|{rescuable}
        String rawMessage = STR."\{player.uuid()}|:|\{reason}|:|\{player.name()}|:|\{JSONComponentSerializer.json().serialize(message)}|:|\{reason.isRescuable()}";
        try {
            channel.basicPublish("", PLAYER_KICK_QUEUE, null, rawMessage.getBytes());
        } catch (IOException e) {
            Logger.error(STR."An error occoured whilst attempting to kick the player \{player.name()}.", e);
        }
    }

    /**
     * Sends a chat message to RabbitMQ, for other servers to broadcast to the appropriate chat channel
     *
     * @param chatMessage The message to send, in component form
     * @param chatChannel The channel the message is to be sent on
     */
    public void sendChatMessage(Component chatMessage, ChatChannel chatChannel) {
        //formatting: {chat-message}|{chat-channel}
        String message = STR."\{JSONComponentSerializer.json().serialize(chatMessage)}|\{chatChannel.name()}";
        try {
            channel.basicPublish(CHAT_CHANNEL_EXCHANGE, CHAT_CHANNEL_QUEUE, null, message.getBytes());
        } catch (IOException e) {
            Logger.error("An error occurred whilst attempting to send a chat message!", e);
        }
    }

    /**
     * Initializes the consumers for receiving chat messages
     */
    public void receiveChatMessages() {
        try {
            DeliverCallback deliverCallback = (_, delivery) -> {
                String[] thing = new String(delivery.getBody(), StandardCharsets.UTF_8).split("\\|");
                Component chatMessage = JSONComponentSerializer.json().deserialize(thing[0]);
                ChatChannel chatChannel = ChatChannel.valueOf(thing[1]);
                switch (chatChannel) {
                    case MOD -> // send a message to all players with cytonic.chat.mod permission
                            Cytosis.getOnlinePlayers().forEach(player -> {
                                if (player.hasPermission("cytonic.chat.mod")) {
                                    player.playSound(Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.PLAYER, .7f, 1.0F));
                                    player.sendMessage(chatMessage);
                                }
                            });

                    case STAFF -> // send a message to all players with cytonic.chat.staff permission
                            Cytosis.getOnlinePlayers().forEach(player -> {
                                if (player.hasPermission("cytonic.chat.staff")) {
                                    player.playSound(Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.PLAYER, .7f, 1.0F));
                                    player.sendMessage(chatMessage);
                                }
                            });
                    case ADMIN -> // send a message to all players with cytonic.chat.admin permission
                            Cytosis.getOnlinePlayers().forEach(player -> {
                                if (player.hasPermission("cytonic.chat.admin")) {
                                    player.playSound(Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.PLAYER, .7f, 1.0F));
                                    player.sendMessage(chatMessage);
                                }
                            });
                    case LEAGUE -> {// leagues..
                    }
                    case PARTY -> {// parties..
                    }
                }
            };
            channel.basicConsume(CHAT_CHANNEL_QUEUE, true, deliverCallback, _ -> {
            });
        } catch (IOException e) {
            Logger.error("error", e);
        }
    }

    /*
     * RabbitMQ via the Plugins API
     */

    /**
     * Registers a queue with RabbitMQ
     *
     * @param queue the queue to register
     */
    public void registerQueue(String queue) {
        try {
            channel.queueDeclare(queue, false, false, false, null);
        } catch (IOException e) {
            Logger.error(STR."An error occurred whilst attempting to register the queue '\{queue}'", e);
        }
    }

    /**
     * Registers an exchange with RabbitMQ
     *
     * @param exchange The name of the exchange
     * @param type     The type of the exchange
     */
    public void registerExchange(String exchange, String type) {
        try {
            channel.exchangeDeclare(exchange, type);
        } catch (IOException e) {
            Logger.error(STR."An error occurred whilst attempting to register the exchange '\{exchange}'", e);
        }
    }

    /**
     * Binds a queue to an exchange
     *
     * @param queue      The queue to bind
     * @param exchange   The exchange to bind the queue to
     * @param routingKey The routing key
     */
    public void bindQueue(String queue, String exchange, String routingKey) {
        try {
            channel.queueBind(queue, exchange, routingKey);
        } catch (IOException e) {
            Logger.error(STR."An error occurred whilst attempting to bind the queue '\{queue}'", e);
        }
    }

    /**
     * Creates a callback for listening to messages on the specified queue
     *
     * @param queue           The queue to listen on
     * @param deliverCallback The callback for message delivery
     */
    public void consumeQueue(String queue, DeliverCallback deliverCallback) {
        try {
            channel.basicConsume(queue, true, deliverCallback, _ -> {
            });
        } catch (IOException e) {
            Logger.error(STR."An error occurred whilst attempting to consume the queue! '\{queue}'", e);
        }
    }

    /**
     * Sets a callback to consume messages on a queue
     *
     * @param queue           The queue to consume
     * @param deliverCallback The callback for message delivery
     * @param cancelCallback  The callback for consumer cancellation
     */
    public void consumeQueue(String queue, DeliverCallback deliverCallback, CancelCallback cancelCallback) {
        try {
            channel.basicConsume(queue, true, deliverCallback, cancelCallback);
        } catch (IOException e) {
            Logger.error(STR."An error occurred whilst attempting to consume the queue! '\{queue}'", e);
        }
    }

    /**
     * Sends a message to a queue
     *
     * @param queue   The queue to send to
     * @param message The message to send
     */
    public void sendMessage(String queue, String message) {
        try {
            channel.basicPublish("", queue, null, message.getBytes());
        } catch (IOException e) {
            Logger.error(STR."An error occurred whilst attempting to send a message to the queue! '\{queue}'", e);
        }
    }

    /**
     * Sends a message to an exchange and queue
     *
     * @param queue    The queue to send the message to
     * @param exchange The exchange to send the message on
     * @param message  The message to send
     */
    public void sendMessage(String queue, String exchange, String message) {
        try {
            channel.basicPublish(exchange, queue, null, message.getBytes());
        } catch (IOException e) {
            Logger.error(STR."An error occurred whilst attempting to send a message to the queue! '\{queue}' on exchange '\{exchange}'", e);
        }
    }
}