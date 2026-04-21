/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.bytecode;

/**
 * Metadata about a persistent field in a JPA entity.
 * Holds information needed to generate accessor writer classes.
 */
public class FieldMetadata {
    private final String name;
    private final String descriptor;
    private final String signature;
    private final boolean isPrimitive;

    public FieldMetadata(String name, String descriptor, String signature) {
        this.name = name;
        this.descriptor = descriptor;
        this.signature = signature;
        this.isPrimitive = descriptor != null && descriptor.length() == 1;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public String getSignature() {
        return signature;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    /**
     * Gets the internal type name for use in bytecode.
     * For primitives, returns the wrapper class internal name.
     * For objects, extracts the class name from the descriptor.
     *
     * @return Internal type name (e.g., "java/lang/String" or "java/lang/Integer")
     */
    public String getInternalTypeName() {
        if (isPrimitive) {
            return TypeDescriptorHelper.getPrimitiveWrapper(descriptor);
        }

        // Extract class name from descriptor
        // "Ljava/lang/String;" -> "java/lang/String"
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1);
        }

        // For arrays, return the descriptor as-is
        return descriptor;
    }

    @Override
    public String toString() {
        return "FieldMetadata{name='" + name + "', descriptor='" + descriptor + "', primitive=" + isPrimitive + "}";
    }
}
