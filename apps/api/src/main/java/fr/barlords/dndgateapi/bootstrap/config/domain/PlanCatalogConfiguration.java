package fr.barlords.dndgateapi.bootstrap.config.domain;

import fr.barlords.dndgateapi.domain.functional.model.plan.PlanCatalog;
import fr.barlords.dndgateapi.server.plan.PlanCatalogReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** The plan catalog, read once at startup from the file named by {@code plans.file}. */
@Configuration
public class PlanCatalogConfiguration {

    @Bean
    public PlanCatalog planCatalog(@Value("${plans.file}") Resource file) {
        try (InputStream input = file.getInputStream()) {
            return new PlanCatalogReader().read(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the plan catalog " + file, e);
        }
    }

}
