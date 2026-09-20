package ru.rooyzee.elytrixclans.database;

public interface IDataBase {
    void connect();
    void disconnect();
    void save();

    /** Снимок в main-потоке, запись на диск асинхронно. */
    void saveAsync();
}