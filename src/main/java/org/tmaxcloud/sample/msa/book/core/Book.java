package org.tmaxcloud.sample.msa.book.core;

import javax.persistence.*;

@Entity
@Table(name = "BOOK")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private int quantity;
    @Transient
    private float rating;

    public Book() {}

    public Book(String title, int quantity) {
        this.title = title;
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return String.format(
                "Book[id=%d, title='%s', quantity='%s']",
                id, title, quantity);
    }

    public Long getId() {
        return this.id;
    }

    public String getTitle() {
        return this.title;
    }

    public int getQuantity() {
        return this.quantity;
    }

    public float getRating() {
        return this.rating;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setRating(float rating) {
        this.rating = rating;
    }

}
