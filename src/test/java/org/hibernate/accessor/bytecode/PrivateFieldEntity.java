package org.hibernate.accessor.bytecode;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Test entity with private fields to verify that nest-based access control
 * allows the generated accessor inner classes to read/write private members.
 */
@Entity
public class PrivateFieldEntity {
	@Id
	private Long id;

	@Basic(optional = false)
	private String name;

	public PrivateFieldEntity() {}
}
