package net.cytonic.cytosis;

import lombok.Getter;
import net.cytonic.cytosis.commands.CommandHandler;
import net.cytonic.cytosis.config.CytosisSettings;
import net.cytonic.cytosis.data.DatabaseManager;
import net.cytonic.cytosis.events.EventHandler;
import net.cytonic.cytosis.events.ServerEventListeners;
import net.cytonic.cytosis.files.FileManager;
import net.cytonic.cytosis.logging.Logger;
import net.cytonic.cytosis.managers.ChatManager;
import net.cytonic.cytosis.managers.NPCManager;
import net.cytonic.cytosis.managers.PlayerListManager;
import net.cytonic.cytosis.managers.SideboardManager;
import net.cytonic.cytosis.messaging.MessagingManager;
import net.cytonic.cytosis.playerlist.PlayerListCategory;
import net.cytonic.cytosis.playerlist.PlayerListEntry;
import net.cytonic.cytosis.playerlist.PlayerListFavicon;
import net.cytonic.cytosis.plugins.PluginManager;
import net.cytonic.cytosis.ranks.RankManager;
import net.cytonic.cytosis.utils.Utils;
import net.hollowcube.polar.PolarLoader;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.entity.Player;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.extras.velocity.VelocityProxy;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.ConnectionManager;
import net.minestom.server.permission.Permission;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static net.cytonic.cytosis.utils.MiniMessageTemplate.MM;

/**
 * The main class for Cytosis
 */
@Getter
public final class Cytosis {

    /**
     * the instance ID is used to identify the server
     */
    public static final String SERVER_ID = generateID();
    /**
     * The version of Cytosis
     */
    public static final String VERSION = "0.1";
    // manager stuff
    @Getter
    private static MinecraftServer minecraftServer;
    @Getter
    private static InstanceManager instanceManager;
    @Getter
    private static InstanceContainer defaultInstance;
    @Getter
    private static EventHandler eventHandler;
    @Getter
    private static ConnectionManager connectionManager;
    @Getter
    private static CommandManager commandManager;
    @Getter
    private static CommandHandler commandHandler;
    @Getter
    private static FileManager fileManager;
    @Getter
    private static DatabaseManager databaseManager;
    @Getter
    private static MessagingManager messagingManager;
    @Getter
    private static ConsoleSender consoleSender;
    @Getter
    private static RankManager rankManager;
    @Getter
    private static ChatManager chatManager;
    @Getter
    private static PlayerListManager playerListManager;
    @Nullable
    @Getter
    private static CytonicNetwork cytonicNetwork;
    @Getter
    private static PluginManager pluginManager;
    @Getter
    private static SideboardManager sideboardManager;
    @Getter
    private static NPCManager npcManager;
    private static List<String> FLAGS;

    private Cytosis() {
    }

    /**
     * The entry point for the Minecraft Server
     *
     * @param args Runtime flags
     */
    public static void main(String[] args) {
        FLAGS = List.of(args);
        long start = System.currentTimeMillis();
        // Initialize the server
        Logger.info("Starting server.");
        minecraftServer = MinecraftServer.init();
        MinecraftServer.setBrandName("Cytosis");

        Logger.info("Starting instance manager.");
        instanceManager = MinecraftServer.getInstanceManager();

        Logger.info("Starting connection manager.");
        connectionManager = MinecraftServer.getConnectionManager();

        // Commands
        Logger.info("Starting command manager.");
        commandManager = MinecraftServer.getCommandManager();

        Logger.info("Setting console command sender.");
        consoleSender = commandManager.getConsoleSender();
        consoleSender.addPermission(new Permission("*"));

        //chat manager
        Logger.info("Starting chat manager.");
        chatManager = new ChatManager();

        // instances
        Logger.info("Creating instance container");
        defaultInstance = instanceManager.createInstanceContainer();

        Logger.info("Creating file manager");
        fileManager = new FileManager();

        // Everything after this point depends on config contents
        Logger.info("Initializing file manager");
        fileManager.init().whenComplete((_, throwable) -> {
            if (throwable != null) {
                Logger.error("An error occurred whilst initializing the file manager!", throwable);
            } else {
                Logger.info("File manager initialized!");
                CytosisSettings.loadEnvironmentVariables();
                CytosisSettings.loadCommandArgs();
                if (CytosisSettings.SERVER_PROXY_MODE) {
                    Logger.info("Enabling velocity!");
                    VelocityProxy.enable(CytosisSettings.SERVER_SECRET);
                } else mojangAuth();
                Logger.info("Completing nonessential startup tasks.");

                completeNonEssentialTasks(start);
            }
        });
    }

    /**
     * Gets the players currently on THIS instance
     *
     * @return a set of players
     */
    public static Set<Player> getOnlinePlayers() {
        Set<Player> players = new HashSet<>();
        instanceManager.getInstances().forEach(instance -> players.addAll(instance.getPlayers()));
        return players;
    }

    /**
     * Gets the player if they are on THIS instance, by USERNAME
     *
     * @param username The name to fetch the player by
     * @return The optional holding the player if they exist
     */
    public static Optional<Player> getPlayer(String username) {
        Player target = null;
        for (Player onlinePlayer : getOnlinePlayers())
            if (onlinePlayer.getUsername().equals(username)) target = onlinePlayer;
        return Optional.ofNullable(target);
    }

    /**
     * Gets the player if they are on THIS instance, by UUID
     *
     * @param uuid The uuid to fetch the player by
     * @return The optional holding the player if they exist
     */
    public static Optional<Player> getPlayer(UUID uuid) {
        Player target = null;
        for (Player onlinePlayer : getOnlinePlayers()) {
            if (onlinePlayer.getUuid() == uuid) target = onlinePlayer;
        }
        return Optional.ofNullable(target);
    }

    /**
     * Gives a player all permissions
     *
     * @param player to grant all permissions to
     */
    public static void opPlayer(Player player) {
        player.addPermission(new Permission("*")); // give them every permission
    }

    /**
     * Removes the '*' permission from a player
     *
     * @param player The player to remove the '*' permission from
     */
    public static void deopPlayer(Player player) {
        player.removePermission("*"); // remove every permission
    }

    /**
     * Sets up Mojang Authentication
     */
    public static void mojangAuth() {
        Logger.info("Initializing Mojang Authentication");
        MojangAuth.init(); //VERY IMPORTANT! (This is online mode!)
    }

    /**
     * Loads the world based on the settings
     */
    public static void loadWorld() {
        if (CytosisSettings.SERVER_WORLD_NAME.isEmpty()) {
            Logger.info("Generating basic world");
            defaultInstance.setGenerator(unit -> unit.modifier().fillHeight(0, 1, Block.WHITE_STAINED_GLASS));
            defaultInstance.setChunkSupplier(LightingChunk::new);
            Logger.info("Basic world loaded!");
            return;
        }
        Logger.info(STR."Loading world '\{CytosisSettings.SERVER_WORLD_NAME}'");
        databaseManager.getMysqlDatabase().getWorld(CytosisSettings.SERVER_WORLD_NAME).whenComplete((polarWorld, throwable) -> {
            if (throwable != null) {
                Logger.error("An error occurred whilst initializing the world!", throwable);
            } else {
                defaultInstance.setChunkLoader(new PolarLoader(polarWorld));
                defaultInstance.setChunkSupplier(LightingChunk::new);
                Logger.info("World loaded!");
            }
        });
    }

    /**
     * Completes nonessential startup tasks for the server
     *
     * @param start The time the server started
     */
    public static void completeNonEssentialTasks(long start) {
        Logger.info("Initializing database");
        databaseManager = new DatabaseManager();
        databaseManager.setupDatabases().whenComplete((_, throwable) -> {
            if (throwable != null) {
                Logger.error("An error occurred whilst initializing the database!", throwable);
                return;
            }
            Logger.info("Database initialized!");
            Logger.info("Setting up event handlers");
            eventHandler = new EventHandler(MinecraftServer.getGlobalEventHandler());
            eventHandler.init();

            Logger.info("Initializing server events");
            ServerEventListeners.initServerEvents();

            MinecraftServer.getSchedulerManager().buildShutdownTask(() -> {
                databaseManager.shutdown();
                messagingManager.shutdown();
                sideboardManager.shutdown();
                getOnlinePlayers().forEach(onlinePlayer -> onlinePlayer.kick(MM."<red>The server is shutting down."));
            });

            Logger.info("Starting Player list manager");
            playerListManager = new PlayerListManager();

            Logger.info("Building player list");
            playerListManager.setGlobalCategories(
                    Utils.list(
                            new PlayerListCategory(MM."<bold><yellow>Server Data</yellow></bold>", PlayerListFavicon.YELLOW,
                                    1, Utils.list(new PlayerListEntry(MM."Server ID: <green>\{Cytosis.getRawID()}", PlayerListFavicon.GREY, 1),
                                    new PlayerListEntry(MM."Something: <green>Yay!!", PlayerListFavicon.GREY, 2),
                                    new PlayerListEntry(MM."Players: <red>No.", PlayerListFavicon.GREY, 3)
                            )),
                            new PlayerListCategory(MM."<bold><rainbow>Rainbow</rainbow></bold>", PlayerListFavicon.GREY,
                                    1, Utils.list(
                                    new PlayerListEntry(MM." ", PlayerListFavicon.RED, 1),
                                    new PlayerListEntry(MM." ", PlayerListFavicon.ORANGE, 2),
                                    new PlayerListEntry(MM." ", PlayerListFavicon.YELLOW, 3),
                                    new PlayerListEntry(MM." ", PlayerListFavicon.GREEN, 4),
                                    new PlayerListEntry(MM." ", PlayerListFavicon.BLUE, 5),
                                    new PlayerListEntry(MM." ", PlayerListFavicon.PURPLE, 6),
                                    new PlayerListEntry(MM." ", PlayerListFavicon.WHITE, 7),
                                    new PlayerListEntry(MM." ", PlayerListFavicon.GREY, 8),
                                    new PlayerListEntry(MM." ", PlayerListFavicon.BLACK, 9)

                            ))
                    )
            );

            Logger.info("Initializing server commands");
            commandHandler = new CommandHandler();
            commandHandler.setupConsole();
            commandHandler.registerCytosisCommands();

            messagingManager = new MessagingManager();
            messagingManager.initialize().whenComplete((_, th) -> {
                if (th != null) {
                    Logger.error("An error occurred whilst initializing the messaging manager!", th);
                } else {
                    Logger.info("Messaging manager initialized!");
                }
            });

            Logger.info("Initializing Plugin Manager!");
            pluginManager = new PluginManager();
            Logger.info("Loading plugins!");
            Thread.ofVirtual().name("CytosisPluginLoader").start(pluginManager::loadPlugins);

            Logger.info("Initializing Rank Manager");
            rankManager = new RankManager();
            rankManager.init();

            Logger.info("Creating sideboard manager!");
            sideboardManager = new SideboardManager();
            sideboardManager.updateBoards();

            Logger.info("Starting NPC manager!");
            npcManager = new NPCManager();

            if (CytosisSettings.SERVER_PROXY_MODE) {
                Logger.info("Loading network setup!");
                cytonicNetwork = new CytonicNetwork();
                cytonicNetwork.importDataFromRedis(databaseManager.getRedisDatabase());
            }

            Thread.ofVirtual().name("WorldLoader").start(Cytosis::loadWorld);


            // Start the server
            Logger.info(STR."Server started on port \{CytosisSettings.SERVER_PORT}");
            minecraftServer.start("0.0.0.0", CytosisSettings.SERVER_PORT);

            long end = System.currentTimeMillis();
            Logger.info(STR."Server started in \{end - start}ms!");

            if (FLAGS.contains("--ci-test")) {
                Logger.info("Stopping server due to '--ci-test' flag.");
                MinecraftServer.stopCleanly();
            }
        });

    }

    /**
     * Generates a random Server ID:
     * <p>
     * TODO: make a check for existing server ids
     *
     * @return a random Server ID
     */
    private static String generateID() {
        //todo: make a check for existing server ids
        StringBuilder id = new StringBuilder("Cytosis-");
        Random random = new Random();
        id.append((char) (random.nextInt(26) + 'a'));
        for (int i = 0; i < 4; i++) {
            id.append(random.nextInt(10));
        }
        id.append((char) (random.nextInt(26) + 'a'));
        return id.toString();
    }

    /**
     * Gets the Raw ID of the server
     * <p>
     * For example, Cytosis-a1234b would return a1234b
     *
     * @return The raw ID
     */
    public static String getRawID() {
        return Cytosis.SERVER_ID.replace("Cytosis-", "");
    }
}