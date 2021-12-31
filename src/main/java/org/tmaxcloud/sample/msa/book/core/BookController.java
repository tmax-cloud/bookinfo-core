package org.tmaxcloud.sample.msa.book.core;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.tmaxcloud.sample.msa.book.common.dto.BookDto;
import org.tmaxcloud.sample.msa.book.common.dto.BookDetailDto;
import org.tmaxcloud.sample.msa.book.common.dto.RatingDto;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class BookController {

    private static final Logger log = LoggerFactory.getLogger(BookController.class);

    private final BookRepository repository;

    @Value("${upstream.rating}")
    private String ratingSvcAddr;

    private final WebClient webClient;

    private final ModelMapper modelMapper;

    BookController(BookRepository repository, ModelMapper modelMapper, WebClient webClient) {
        this.repository = repository;
        this.modelMapper = modelMapper;
        this.webClient = webClient;
    }

    @GetMapping("/books")
    List<BookDto> all() {
        List<Book> books = repository.findAll();
        return books.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/books")
    BookDto newBook(@RequestBody Book newBook) {
        return convertToDto(repository.save(newBook));
    }

    @GetMapping("/books/{id}")
    BookDetailDto findOne(@PathVariable Long id) {
        Book book = repository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
        RatingDto response = webClient.get()
                .uri(ratingSvcAddr + "/api/rating/"+id)
                .retrieve()
                .bodyToMono(RatingDto.class)
                .block();
        return convertToDetailDto(book).setRating(response.getScore());
    }

    @PutMapping("/books/{id}")
    BookDto replaceBook(@RequestBody Book newBook, @PathVariable Long id) {
        return repository.findById(id)
                .map(book -> {
                    book.setTitle(newBook.getTitle());
                    book.setQuantity(newBook.getQuantity());
                    return convertToDto(repository.save(book));
                })
                .orElseGet(() -> {
                    newBook.setId(id);
                    return convertToDto(repository.save(newBook));
                });
    }

    @PutMapping("/books/{id}/rating")
    BookDto evaluateBooks(@RequestBody String score, @PathVariable Long id) {
        Book book = repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        RatingDto newRating = new RatingDto().setBookId(id).setScore(Float.parseFloat(score));

        Mono<RatingDto> response = webClient.post()
                .uri(ratingSvcAddr + "/api/rating")
                .bodyValue(newRating)
                .retrieve()
                .bodyToMono(RatingDto.class);
        response.subscribe(res -> {
            log.info("succeed to set rating score for book:{}", id);
        }, e -> {
            log.warn("failed to set rating score for book:{}", id);
        });
        return convertToDto(book);
    }

    @DeleteMapping("/books/{id}")
    void deleteBook(@PathVariable Long id) {
        repository.deleteById(id);
    }

    private BookDto convertToDto(Book book) {
        return modelMapper.map(book, BookDto.class);
    }

    private BookDetailDto convertToDetailDto(Book book) {
        return modelMapper.map(book, BookDetailDto.class);
    }
}
