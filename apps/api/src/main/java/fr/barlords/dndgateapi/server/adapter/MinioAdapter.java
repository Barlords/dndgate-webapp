package fr.barlords.dndgateapi.server.adapter;

import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.server.audit.AfterCommit;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.beans.factory.annotation.Value;

import java.io.InputStream;
import java.nio.ByteBuffer;

@Slf4j
@RequiredArgsConstructor
public class MinioAdapter implements MinioSpi {

    private final MinioClient minioClient;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Override
    public void uploadFile(String contentType, InputStream inputStream, String path) throws Exception {
        if (!isValidContentType(contentType)) {
            throw new IllegalArgumentException("Invalid file type. Only PNG are allowed.");
        }

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path)
                        .stream(inputStream, (long) inputStream.available(), -1L)
                        .contentType(contentType)
                        .build());
    }

    private boolean isValidContentType(String contentType) {
        return "image/png".equals(contentType) || "image/jpeg".equals(contentType);
    }

    public ByteBuffer downloadFile(String filename) throws Exception {
        try (GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucketName).object(filename).build())) {
            return ByteBuffer.wrap(IOUtils.toByteArray(response));
        }
    }

    @Override
    public void deleteAfterCommit(String path) {
        AfterCommit.run(() -> {
            try {
                deleteFile(path);
            } catch (Exception e) {
                log.warn("The file {} could not be removed and is left behind", path, e);
            }
        });
    }

    public void deleteFile(String path) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path)
                        .build());
    }
}
