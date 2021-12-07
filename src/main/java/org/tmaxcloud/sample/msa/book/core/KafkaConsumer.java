package org.tmaxcloud.sample.msa.book.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    private final BookRepository repository;

    KafkaConsumer(BookRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "purchase", containerFactory = "bookMessageListener")
    public void consumePurchaseMessage(BookMessage msg) {
        log.info("Consume purchase message : {}", msg);
        Book book = repository.findById(msg.getBookId()).orElseThrow(() -> new BookNotFoundException(msg.getBookId()));
        book.setQuantity(book.getQuantity() + msg.getQuantity());
        repository.save(book);
    }

    @KafkaListener(topics = "sale", containerFactory = "bookMessageListener")
    public void consumeSaleMessage(BookMessage msg) {
        log.info("Consume sale message : {}", msg);
        Book book = repository.findById(msg.getBookId()).orElseThrow(() -> new BookNotFoundException(msg.getBookId()));
        book.setQuantity(book.getQuantity() - msg.getQuantity());
        repository.save(book);
    }

    @KafkaListener(topics = "rent", containerFactory = "bookMessageListener")
    public void consumeRentMessage(BookMessage msg) {
        log.info("Consume rent message : {}", msg);
        Book book = repository.findById(msg.getBookId()).orElseThrow(() -> new BookNotFoundException(msg.getBookId()));
        book.setQuantity(book.getQuantity() - msg.getQuantity());
        repository.save(book);
    }
}
