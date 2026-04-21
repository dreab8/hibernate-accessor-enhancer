package org.hibernate.accessor.bytecode;

import org.hibernate.annotations.NaturalId;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Entity
@Table(name = "books")
public class Book {
	@Id
	String isbn;

	@NaturalId
	@Basic(optional = false)
	String title;

	@Basic(optional = false)
	String text;

	@NaturalId
	LocalDate publicationDate;

	@ManyToMany(mappedBy = "books")
	Set<Author> authors;

	BigDecimal price;

	int pages;

	public Book(String isbn, String title, String text) {
		this.isbn = isbn;
		this.title = title;
		this.text = text;
	}

	Book() {}

	public String getIsbn() {
		return isbn;
	}

	public String getTitle() {
		return title;
	}

	public String getText() {
		return text;
	}

	public LocalDate getPublicationDate() {
		return publicationDate;
	}

	public Set<Author> getAuthors() {
		return authors;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public int getPages() {
		return pages;
	}
}


