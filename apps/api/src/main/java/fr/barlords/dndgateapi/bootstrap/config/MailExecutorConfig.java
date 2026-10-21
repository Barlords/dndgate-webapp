package fr.barlords.dndgateapi.bootstrap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The outbox of the e-mails: a small bounded pool, so that a slow mail server never holds an HTTP request
 * and never piles up unlimited work. When the queue is full the adapter drops the mail (the person can ask again).
 */
@Configuration
public class MailExecutorConfig {

    private static final int THREADS = 2;

    private static final int QUEUE_CAPACITY = 100;

    @Bean(name = "mailExecutor", destroyMethod = "shutdown")
    public Executor mailExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "mail-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(THREADS, THREADS, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), factory, new ThreadPoolExecutor.AbortPolicy());
    }

}
