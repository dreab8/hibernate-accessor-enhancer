package org.hibernate.accessor.bytecode;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import java.util.Set;

/**
 * Subclass entity for testing that superclass field accessors
 * work correctly on subclass instances.
 */
@Entity
public class Novel extends BasePublication {
	@Basic
	String genre;

	@ManyToMany
	Set<Author> authors;

	public Novel() {}
}
