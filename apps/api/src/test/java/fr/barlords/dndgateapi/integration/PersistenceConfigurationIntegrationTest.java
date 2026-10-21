package fr.barlords.dndgateapi.integration;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class PersistenceConfigurationIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private ApplicationContext context;

    @Test
    void no_database_session_stays_open_while_a_response_is_written() {
        // With open-in-view, a lazy collection leaking out of the adapters would be loaded silently while the JSON is written:
        // the lazy loads have to fail loudly instead, so that the mappers are the only place where data is read.
        Assertions.assertEquals("false", context.getEnvironment().getProperty("spring.jpa.open-in-view"));
        Assertions.assertTrue(context.getBeansOfType(WebMvcConfigurer.class).values().stream()
                .noneMatch(bean -> bean.getClass().getSimpleName().contains("OpenEntityManagerInView")));
        Assertions.assertNotNull(context.getBean(EntityManagerFactory.class));
    }
}
