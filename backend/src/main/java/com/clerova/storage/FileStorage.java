package com.clerova.storage;

/** Keeps files such as email attachments outside the database. */
public interface FileStorage {

    /** False when storage isn't configured; then only file names are kept. */
    boolean isEnabled();

    /** @throws FileStorageException if the file could not be saved */
    void put(String path, byte[] content, String contentType);

    /** @throws FileStorageException if the file could not be read */
    byte[] get(String path);
}
