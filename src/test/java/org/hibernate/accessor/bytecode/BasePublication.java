package org.hibernate.accessor.bytecode;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Abstract superclass entity for testing accessor enhancement with inheritance.
 */
@Entity
public abstract class BasePublication {
	@Id
	Long id;

	@Basic(optional = false)
	String title;

	protected BasePublication() {}
}
