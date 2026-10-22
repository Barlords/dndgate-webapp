package fr.barlords.dndgateapi.domain.port.out;

import java.io.InputStream;
import java.nio.ByteBuffer;

public interface MinioSpi {

    void uploadFile(String contentType, InputStream inputStream, String path) throws Exception;

    ByteBuffer downloadFile(String filename) throws Exception;

    void deleteFile(String path) throws Exception;

    /**
     * Deletes the file once the current transaction is committed (at once outside a transaction), never when it
     * is rolled back. A failure is logged and never reaches the caller: a file left behind is only an orphan.
     */
    void deleteAfterCommit(String path);
}
