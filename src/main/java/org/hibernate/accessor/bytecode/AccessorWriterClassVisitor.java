/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * ASM ClassVisitor that enhances a class by adding HibernateAccessorValueWriter inner classes.
 * This is the second pass visitor that generates the bytecode modifications.
 */
public class AccessorWriterClassVisitor extends ClassVisitor {

    static final String WRITER_INFIX = "$$__HibernateAccessorValueWriter";
    static final String READER_INFIX = "$$__HibernateAccessorValueReader";

    private final List<FieldMetadata> persistentFields;
    private String className;
    private final List<InnerClassInfo> innerClasses = new ArrayList<>();

    public AccessorWriterClassVisitor(ClassVisitor delegate, List<FieldMetadata> persistentFields) {
        super(Opcodes.ASM9, delegate);
        this.persistentFields = persistentFields;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
                      String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitEnd() {
        // Generate inner classes for each persistent field
        for (FieldMetadata field : persistentFields) {
            // Generate WRITER accessor
            String writerClassName = generateFullyQualifiedWriterInnerClassName( className, field.getName());
            String writerCacheKey = generateCacheKey(className, field.getName());

            // Declare writer inner class relationship in outer class
            cv.visitInnerClass(
                    writerClassName,
                    className,
                    generateSimpleWriterInnerClassName(className, field.getName()),
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
            );

            // Generate writer inner class bytecode
            byte[] writerBytes = InnerClassGenerator.generate(
                    writerClassName,
                    className,
                    field,
                    writerCacheKey
            );

            innerClasses.add(new InnerClassInfo(writerClassName, writerBytes));

            // Generate READER accessor
            String readerClassName = generateFullyQualifiedReaderInnerClassName( className, field.getName());
            String readerCacheKey = generateCacheKey(className, field.getName());

            // Declare reader inner class relationship in outer class
            cv.visitInnerClass(
                    readerClassName,
                    className,
                    generateSimpleReaderInnerClassName(className, field.getName()),
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
            );

            // Generate reader inner class bytecode
            byte[] readerBytes = InnerClassGenerator.generateReader(
                    readerClassName,
                    className,
                    field,
                    readerCacheKey
            );

            innerClasses.add(new InnerClassInfo(readerClassName, readerBytes));
        }

        super.visitEnd();
    }

    /**
     * Gets the list of generated inner classes.
     *
     * @return List of inner class info (name and bytecode)
     */
    public List<InnerClassInfo> getInnerClasses() {
        return innerClasses;
    }

    /**
     * Generates the fully qualified internal name for an inner accessor writer class.
     *
     * @param outerClassName Outer class internal name
     * @param fieldName      Field name
     * @return Inner class internal name
     */
    private String generateFullyQualifiedWriterInnerClassName(String outerClassName, String fieldName) {
        String simpleOuterName = TypeDescriptorHelper.getSimpleClassName(outerClassName);
        return outerClassName + WRITER_INFIX + "__" + simpleOuterName + "__" + fieldName;
    }

    /**
     * Generates the simple name for an inner accessor writer class.
     *
     * @param outerClassName Outer class internal name
     * @param fieldName      Field name
     * @return Simple inner class name
     */
    private String generateSimpleWriterInnerClassName(String outerClassName, String fieldName) {
        String simpleOuterName = TypeDescriptorHelper.getSimpleClassName(outerClassName);
        return WRITER_INFIX + simpleOuterName + "__" + fieldName;
    }

    /**
     * Generates the fully qualified internal name for an inner accessor reader class.
     *
     * @param outerClassName Outer class internal name
     * @param fieldName      Field name
     * @return Inner class internal name
     */
    private String generateFullyQualifiedReaderInnerClassName(String outerClassName, String fieldName) {
        String simpleOuterName = TypeDescriptorHelper.getSimpleClassName(outerClassName);
        return outerClassName + READER_INFIX + "__" + simpleOuterName + "__" + fieldName;
    }

    /**
     * Generates the simple name for an inner accessor reader class.
     *
     * @param outerClassName Outer class internal name
     * @param fieldName      Field name
     * @return Simple inner class name
     */
    private String generateSimpleReaderInnerClassName(String outerClassName, String fieldName) {
        String simpleOuterName = TypeDescriptorHelper.getSimpleClassName(outerClassName);
        return READER_INFIX + simpleOuterName + "__" + fieldName;
    }

    /**
     * Generates the cache key for HibernateAccessorFactory registration.
     *
     * @param className Fully qualified internal class name
     * @param fieldName Field name
     * @return Cache key (e.g., "org.hibernate.test.Book_isbn")
     */
    private String generateCacheKey(String className, String fieldName) {
        return TypeDescriptorHelper.toQualifiedName(className) + "_" + fieldName;
    }

    /**
     * Holds information about a generated inner class.
     */
    public static class InnerClassInfo {
        private final String className;
        private final byte[] bytecode;

        public InnerClassInfo(String className, byte[] bytecode) {
            this.className = className;
            this.bytecode = bytecode;
        }

        public String getClassName() {
            return className;
        }

        public byte[] getBytecode() {
            return bytecode;
        }
    }
}
