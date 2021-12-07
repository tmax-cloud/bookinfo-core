package org.tmaxcloud.sample.msa.book.core;

public class BookNotFoundException extends RuntimeException {
    BookNotFoundException(Long id) {
        super("Could not find book " + id);
    }
}