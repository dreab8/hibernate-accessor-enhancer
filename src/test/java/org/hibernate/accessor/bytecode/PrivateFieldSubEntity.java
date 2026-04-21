package org.hibernate.accessor.bytecode;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;

/**
 * Subclass of {@link PrivateFieldEntity} to verify that the superclass's
 * private field accessors work on subclass instances via nest-based access.
 */
@Entity
public class PrivateFieldSubEntity extends PrivateFieldEntity {
	@Basic
	private String description;

	public PrivateFieldSubEntity() {}
}
