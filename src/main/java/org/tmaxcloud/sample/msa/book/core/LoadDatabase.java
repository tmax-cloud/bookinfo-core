package org.tmaxcloud.sample.msa.book.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LoadDatabase {
    private static final Logger log = LoggerFactory.getLogger(LoadDatabase.class);

    @Bean
    CommandLineRunner initDatabase(BookRepository repository) {
        return args -> {
            log.info("Preloading " + repository.save(new Book("Old man and the sea", 10)));
            log.info("Preloading " + repository.save(new Book("Crime and punishment", 7)));
            log.info("Preloading " + repository.save(new Book("Load of the Rings", 33)));
            log.info("Preloading " + repository.save(new Book("Fundamentals of mathematical statistics", 1)));
        };
    }
}