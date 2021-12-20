package org.tmaxcloud.sample.msa.book.core;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.tmaxcloud.sample.msa.book.common.dto.BookDto;
import org.tmaxcloud.sample.msa.book.common.dto.BookDetailDto;
import org.tmaxcloud.sample.msa.book.common.dto.RatingDto;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class BookController {

    private static final Logger log = LoggerFactory.getLogger(BookController.class);

    private final BookRepository repository;

    @Value("${upstream.rating}")
    private String ratingSvcAddr;

    final
    RestTemplate restTemplate;

    private final ModelMapper modelMapper;

    BookController(BookRepository repository, ModelMapper modelMapper, RestTemplate restTemplate) {
        this.repository = repository;
        this.modelMapper = modelMapper;
        this.restTemplate = restTemplate;
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
    BookDetailDto one(@PathVariable Long id) {
        Book book = repository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));

        ResponseEntity<RatingDto> response = restTemplate.getForEntity(ratingSvcAddr + "/api/rating/{id}", RatingDto.class, id);
        if (HttpStatus.OK != response.getStatusCode()) {
            log.error("failed to get rating");
        }
        return convertToDetailDto(book).setRating(response.getBody().getScore());
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
    BookDto evaluateBook(@RequestBody String score, @PathVariable Long id) {
        Book book = repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
        ResponseEntity<RatingDto> response = restTemplate.postForEntity(
                ratingSvcAddr + "/api/rating", new RatingDto().setBookId(id).setScore(Float.parseFloat(score)), RatingDto.class);

        if (HttpStatus.OK != response.getStatusCode()) {
            log.warn("failed to set rating score for book:{}", id);
        }

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
