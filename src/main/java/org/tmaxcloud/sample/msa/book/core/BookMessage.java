package org.tmaxcloud.sample.msa.book.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BookMessage {
    private Long bookId;
    private int quantity;

//    public BookMessage(Long bookId, int quantity) {
//        this.bookId = bookId;
//        this.quantity = quantity;
//    }
}