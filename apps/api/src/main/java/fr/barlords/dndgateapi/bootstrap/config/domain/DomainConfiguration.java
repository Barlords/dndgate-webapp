package fr.barlords.dndgateapi.bootstrap.config.domain;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {"fr.barlords.dndgateapi.server.entity"})
@EnableJpaRepositories(basePackages = {"fr.barlords.dndgateapi.server.repository"})
@ComponentScan(basePackages = {"fr.barlords.dndgateapi.server.adapter"})
public class DomainConfiguration {

}
