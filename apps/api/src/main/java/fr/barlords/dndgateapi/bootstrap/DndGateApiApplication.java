package fr.barlords.dndgateapi.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaRepositories
@SpringBootApplication(scanBasePackages = {"fr.barlords"})
public class DndGateApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DndGateApiApplication.class, args);
    }

}
