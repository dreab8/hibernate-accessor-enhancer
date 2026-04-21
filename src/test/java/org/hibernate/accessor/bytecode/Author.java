package org.hibernate.accessor.bytecode;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import java.util.Set;

/**
 * Stub Author entity for testing.
 */
@Entity
public class Author {
	@Id
	Long id;

	String name;

	@ManyToMany
	Set<Book> books;
}

