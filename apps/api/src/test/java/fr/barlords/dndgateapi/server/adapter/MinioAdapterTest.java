package fr.barlords.dndgateapi.server.adapter;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** A deferred deletion waits for the commit, vanishes on rollback and never fails the caller. */
class MinioAdapterTest {

    private final MinioClient client = mock(MinioClient.class);

    private MinioAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MinioAdapter(client);
        ReflectionTestUtils.setField(adapter, "bucketName", "bucket");
    }

    @AfterEach
    void endTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    @Test
    void outside_a_transaction_the_file_is_deleted_at_once() throws Exception {
        adapter.deleteAfterCommit("a.png");

        verify(client).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void inside_a_transaction_the_file_is_deleted_only_after_the_commit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        adapter.deleteAfterCommit("a.png");
        verify(client, never()).removeObject(any(RemoveObjectArgs.class));

        commit();
        verify(client).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void a_rolled_back_transaction_keeps_the_file() throws Exception {
        TransactionSynchronizationManager.initSynchronization();

        adapter.deleteAfterCommit("a.png");
        rollback();

        verify(client, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void a_storage_failure_is_swallowed_outside_and_inside_a_transaction() throws Exception {
        doThrow(new RuntimeException("storage down")).when(client).removeObject(any(RemoveObjectArgs.class));

        Assertions.assertDoesNotThrow(() -> adapter.deleteAfterCommit("a.png"));

        TransactionSynchronizationManager.initSynchronization();
        adapter.deleteAfterCommit("b.png");
        Assertions.assertDoesNotThrow(MinioAdapterTest::commit);
    }
}
