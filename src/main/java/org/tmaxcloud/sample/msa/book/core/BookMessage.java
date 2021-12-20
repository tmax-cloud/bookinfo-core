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
}