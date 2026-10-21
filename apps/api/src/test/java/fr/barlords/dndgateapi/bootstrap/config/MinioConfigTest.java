package fr.barlords.dndgateapi.bootstrap.config;

import io.minio.DeleteBucketPolicyArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioConfigTest {

    private final MinioConfig config = new MinioConfig();

    private final MinioClient client = mock(MinioClient.class);

    private CommandLineRunner runner() {
        ReflectionTestUtils.setField(config, "bucketName", "dndgate");
        return config.initMinio(client);
    }

    @Test
    void the_bucket_is_never_made_public_and_an_old_public_policy_is_removed() throws Exception {
        when(client.bucketExists(any())).thenReturn(true);

        runner().run();

        verify(client).deleteBucketPolicy(any(DeleteBucketPolicyArgs.class));
        verify(client, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    void a_missing_bucket_is_created_private() throws Exception {
        when(client.bucketExists(any())).thenReturn(false);

        runner().run();

        verify(client).makeBucket(any(MakeBucketArgs.class));
        verify(client).deleteBucketPolicy(any(DeleteBucketPolicyArgs.class));
        verify(client, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    void an_unreachable_storage_does_not_stop_the_application() throws Exception {
        when(client.bucketExists(any())).thenThrow(new IllegalStateException("down"));

        runner().run();
    }
}
