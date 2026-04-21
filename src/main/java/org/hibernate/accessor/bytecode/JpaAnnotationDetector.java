/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.bytecode;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

/**
 * ASM FieldVisitor that detects JPA annotations on fields.
 * Determines whether a field is persistent based on its annotations.
 */
public class JpaAnnotationDetector extends FieldVisitor {

    private static final Set<String> PERSISTENT_ANNOTATIONS = new HashSet<>();

    static {
        // Jakarta Persistence annotations
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/Id;");
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/Basic;");
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/ManyToMany;");
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/OneToMany;");
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/ManyToOne;");
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/OneToOne;");
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/ElementCollection;");
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/Embedded;");
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/EmbeddedId;");
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/Version;");
        PERSISTENT_ANNOTATIONS.add("Ljakarta/persistence/Lob;");

        // Hibernate annotations
        PERSISTENT_ANNOTATIONS.add("Lorg/hibernate/annotations/NaturalId;");
    }

    private static final String TRANSIENT_ANNOTATION = "Ljakarta/persistence/Transient;";

    private boolean isPersistent = false;
    private boolean isTransient = false;
    private boolean allFieldsMode = false;
    private String fieldName;
    private String fieldDescriptor;
    private String fieldSignature;

    public JpaAnnotationDetector(FieldVisitor delegate) {
        super(Opcodes.ASM9, delegate);
    }

    public void setFieldInfo(String name, String descriptor, String signature) {
        this.fieldName = name;
        this.fieldDescriptor = descriptor;
        this.fieldSignature = signature;
    }

    public void setAllFieldsMode(boolean allFieldsMode) {
        this.allFieldsMode = allFieldsMode;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (PERSISTENT_ANNOTATIONS.contains(descriptor)) {
            isPersistent = true;
        }
        if (TRANSIENT_ANNOTATION.equals(descriptor)) {
            isTransient = true;
            isPersistent = false; // @Transient overrides any persistent annotation
        }
        return super.visitAnnotation(descriptor, visible);
    }

    /**
     * Returns true if the field is persistent (has JPA annotations and is not @Transient).
     *
     * @return true if persistent, false otherwise
     */
    public boolean isPersistent() {
        return isPersistent && !isTransient;
    }

    /**
     * Returns true if the field is explicitly marked with @Transient annotation.
     *
     * @return true if @Transient is present, false otherwise
     */
    public boolean isExplicitlyTransient() {
        return isTransient;
    }

    /**
     * Gets the field metadata if the field is persistent.
     *
     * @return FieldMetadata or null if not persistent
     */
    public FieldMetadata getFieldMetadata() {
        if (!isPersistent()) {
            return null;
        }
        return new FieldMetadata(fieldName, fieldDescriptor, fieldSignature);
    }
}
