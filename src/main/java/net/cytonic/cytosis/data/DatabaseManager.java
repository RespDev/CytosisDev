package net.cytonic.cytosis.data;

import net.cytonic.cytosis.logging.Logger;

public class DatabaseManager {

    private Database database;

    public DatabaseManager() {
    }

    public void shutdown() {
            database.disconnect();
            Logger.info("Good night!");
    }

    public void setupDatabase() {
            database = new Database();
            database.connect();
            database.createChatTable();
    }

    public Database getDatabase() {return database;}
}