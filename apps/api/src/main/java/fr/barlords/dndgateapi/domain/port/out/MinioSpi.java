package fr.barlords.dndgateapi.domain.port.out;

import java.io.InputStream;
import java.nio.ByteBuffer;

public interface MinioSpi {

    void uploadFile(String contentType, InputStream inputStream, String path) throws Exception;

    ByteBuffer downloadFile(String filename) throws Exception;

    void deleteFile(String path) throws Exception;
}
