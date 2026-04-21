/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * ASM ClassVisitor that collects metadata about persistent fields in a JPA entity.
 * This is used in the first pass to identify which fields need accessor writers.
 */
public class PersistentFieldCollector extends ClassVisitor {

    private String className;
    private final List<FieldMetadata> persistentFields = new ArrayList<>();
    private final boolean allFields;

    /**
     * Creates a collector that only processes JPA-annotated fields.
     */
    public PersistentFieldCollector() {
        this(false);
    }

    /**
     * Creates a collector with configurable field processing.
     *
     * @param allFields if true, processes all non-static/non-transient fields;
     *                  if false, only processes JPA-annotated fields
     */
    public PersistentFieldCollector(boolean allFields) {
        super(Opcodes.ASM9);
        this.allFields = allFields;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
                                    String signature, Object value) {
        // Skip static and transient fields
        if ((access & Opcodes.ACC_STATIC) != 0 || (access & Opcodes.ACC_TRANSIENT) != 0) {
            return super.visitField(access, name, descriptor, signature, value);
        }

        if (allFields) {
            // Process all non-static, non-transient fields
            // Still check for @Transient annotation to exclude those
            JpaAnnotationDetector detector = new JpaAnnotationDetector(
                    super.visitField(access, name, descriptor, signature, value)
            );
            detector.setFieldInfo(name, descriptor, signature);
            detector.setAllFieldsMode(true);

            return new FieldVisitor(Opcodes.ASM9, detector) {
                @Override
                public void visitEnd() {
                    // In all-fields mode, add field unless it has @Transient
                    if (!detector.isExplicitlyTransient()) {
                        FieldMetadata metadata = new FieldMetadata(name, descriptor, signature);
                        persistentFields.add(metadata);
                    }
                    super.visitEnd();
                }
            };
        } else {
            // Original behavior: only JPA-annotated fields
            JpaAnnotationDetector detector = new JpaAnnotationDetector(
                    super.visitField(access, name, descriptor, signature, value)
            );
            detector.setFieldInfo(name, descriptor, signature);

            return new FieldVisitor(Opcodes.ASM9, detector) {
                @Override
                public void visitEnd() {
                    // After visiting all annotations, check if field is persistent
                    if (detector.isPersistent()) {
                        FieldMetadata metadata = detector.getFieldMetadata();
                        if (metadata != null) {
                            persistentFields.add(metadata);
                        }
                    }
                    super.visitEnd();
                }
            };
        }
    }

    public String getClassName() {
        return className;
    }

    public List<FieldMetadata> getPersistentFields() {
        return persistentFields;
    }
}
