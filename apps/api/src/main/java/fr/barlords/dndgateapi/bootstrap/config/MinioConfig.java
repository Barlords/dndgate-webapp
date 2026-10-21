package fr.barlords.dndgateapi.bootstrap.config;

import fr.barlords.dndgateapi.domain.port.out.MinioSpi;
import fr.barlords.dndgateapi.server.adapter.MinioAdapter;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.DeleteBucketPolicyArgs;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinioConfig {
    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.accessKey}")
    private String accessKey;

    @Value("${minio.secretKey}")
    private String secretKey;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public CommandLineRunner initMinio(MinioClient minioClient) {
        return args -> {
            try {
                boolean isExist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
                if (!isExist) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                }

                // The pictures are served by the API, to the people who may see them: the bucket is never public.
                // A policy left by an earlier version (public read) is removed.
                minioClient.deleteBucketPolicy(DeleteBucketPolicyArgs.builder().bucket(bucketName).build());
                log.info("The bucket {} is private", bucketName);

            } catch (Exception e) {
                log.error("The bucket {} could not be checked: make sure that it is not public", bucketName, e);
            }
        };
    }

    @Bean
    public MinioSpi minioSpi(
            MinioClient minioClient
    ) {
        return new MinioAdapter(minioClient);
    }

}
