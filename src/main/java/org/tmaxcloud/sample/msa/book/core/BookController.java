package org.tmaxcloud.sample.msa.book.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import orgltmaxcloud.sample.msa.book.common.models.Rating;

import java.util.List;

@RestController
public class BookController {
    private static final Logger log = LoggerFactory.getLogger(BookController.class);

    private final BookRepository repository;

    @Value("${BOOK_RATING_URL}")
    private String ratingSvcAddr;

    @Autowired
    RestTemplate restTemplate;

    BookController(BookRepository repository) {
        this.repository = repository;
    }

    // Aggregate root
    // tag::get-aggregate-root[]
    @GetMapping("/books")
    List<Book> all() {
        return repository.findAll();
    }
    // end::get-aggregate-root[]

    @PostMapping("/books")
    Book newBook(@RequestBody Book newBook) {
        return repository.save(newBook);
    }

    // Single item

    @GetMapping("/books/{id}")
    Book one(@PathVariable Long id) {
        ResponseEntity<Rating> response = restTemplate.getForEntity(ratingSvcAddr + "/rating/{id}", Rating.class, id);
        if (HttpStatus.OK != response.getStatusCode()) {
            log.error("failed to get rating");
        }

        Rating rating = response.getBody();
        log.info("get rating {}", rating);

        Book book = repository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));

        book.setRating(rating.getScore());

        return book;
    }

    @PutMapping("/books/{id}")
    Book replaceBook(@RequestBody Book newBook, @PathVariable Long id) {

        return repository.findById(id)
                .map(book -> {
                    book.setTitle(newBook.getTitle());
                    book.setQuantity(newBook.getQuantity());
                    return repository.save(book);
                })
                .orElseGet(() -> {
                    newBook.setId(id);
                    return repository.save(newBook);
                });
    }

    @PutMapping("/books/{id}/rating")
    Book evaluateBook(@RequestBody String score, @PathVariable Long id) {
        Book book = repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        ResponseEntity<Rating> response = restTemplate.postForEntity(
                ratingSvcAddr + "/rating/{id}", new Rating(id, Float.parseFloat(score)), Rating.class, id);

        if (HttpStatus.OK != response.getStatusCode()) {
            log.warn("failed to set rating score for book:{}", id);
        }

        book.setRating(Float.parseFloat(score));

        return book;
    }

    @DeleteMapping("/books/{id}")
    void deleteBook(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
