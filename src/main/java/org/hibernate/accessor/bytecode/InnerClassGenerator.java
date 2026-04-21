/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.bytecode;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Generates bytecode for HibernateAccessorValueWriter inner classes.
 * Each inner class provides optimized field access for a single persistent field.
 */
public class InnerClassGenerator implements Opcodes {

    private static final String ACCESSOR_PACKAGE = "org/hibernate/accessor/";
    private static final String WRITER_INTERFACE = ACCESSOR_PACKAGE + "HibernateAccessorValueWriter";
    private static final String READER_INTERFACE = ACCESSOR_PACKAGE + "HibernateAccessorValueReader";
    private static final String FACTORY_CLASS = ACCESSOR_PACKAGE + "HibernateAccessorFactory";

    /**
     * Generates bytecode for an accessor writer inner class.
     *
     * @param innerClassName Fully qualified internal name of inner class
     * @param outerClassName Fully qualified internal name of outer class
     * @param field          Field metadata
     * @param cacheKey       Cache key for registration
     * @return Bytecode for the inner class
     */
    public static byte[] generate(String innerClassName, String outerClassName,
                                   FieldMetadata field, String cacheKey) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        // Visit class header
        cw.visit(
                V11,  // Java 11 bytecode
                ACC_PUBLIC | ACC_SUPER | ACC_STATIC,
                innerClassName,
                null,
                "java/lang/Object",
                new String[]{WRITER_INTERFACE}
        );

        // Declare nest host so the inner class can access private fields of the outer class
        cw.visitNestHost(outerClassName);

        // Declare this as inner class of outer class
        String simpleInnerName = innerClassName.substring(innerClassName.lastIndexOf('$') + 1);
        cw.visitInnerClass(
                innerClassName,
                outerClassName,
                simpleInnerName,
                ACC_PUBLIC | ACC_STATIC
        );

        // Generate INSTANCE static field
        generateInstanceField(cw, innerClassName);

        // Generate constructor
        generateConstructor(cw, innerClassName, cacheKey, FACTORY_CLASS);

        // Generate set() method
        generateSetMethod(cw, outerClassName, field);

        // Generate static initializer
        generateStaticInitializer(cw, innerClassName);

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates the public static final INSTANCE field.
     */
    private static void generateInstanceField(ClassWriter cw, String innerClassName) {
        FieldVisitor fv = cw.visitField(
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                "INSTANCE",
                "L" + innerClassName + ";",
                null,
                null
        );
        fv.visitEnd();
    }

    /**
     * Generates the constructor that registers the instance in HibernateAccessorFactory.writerCache.
     */
    private static void generateConstructor(ClassWriter cw, String innerClassName, String cacheKey,
                                            String factoryClassName) {
        MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null
        );
        mv.visitCode();

        // Call super()
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        // HibernateAccessorFactory.writerCache.put(cacheKey, this)
        mv.visitFieldInsn(GETSTATIC, factoryClassName, "writerCache", "Ljava/util/Map;");
        mv.visitLdcInsn(cacheKey);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(
                INVOKEINTERFACE,
                "java/util/Map",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                true
        );
        mv.visitInsn(POP);  // Discard return value from put()

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);  // COMPUTE_FRAMES will calculate
        mv.visitEnd();
    }

    /**
     * Generates the set(Object, Object) method that assigns the value to the field.
     */
    private static void generateSetMethod(ClassWriter cw, String outerClassName, FieldMetadata field) {
        MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "set",
                "(Ljava/lang/Object;Ljava/lang/Object;)V",
                null,
                null
        );
        mv.visitCode();

        // Cast first parameter to owner class
        // ((OwnerClass) var1)
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, outerClassName);

        // Cast and potentially unbox second parameter
        mv.visitVarInsn(ALOAD, 2);

        if (field.isPrimitive()) {
            // For primitives: cast to wrapper, then unbox
            String wrapperClass = TypeDescriptorHelper.getPrimitiveWrapper(field.getDescriptor());
            String unboxMethod = TypeDescriptorHelper.getUnboxMethod(field.getDescriptor());
            String unboxDescriptor = "()" + field.getDescriptor();

            mv.visitTypeInsn(CHECKCAST, wrapperClass);
            mv.visitMethodInsn(INVOKEVIRTUAL, wrapperClass, unboxMethod, unboxDescriptor, false);
        } else {
            // For objects: cast to target type
            String targetType = field.getInternalTypeName();
            mv.visitTypeInsn(CHECKCAST, targetType);
        }

        // Set the field value
        // fieldName = (FieldType) var2
        mv.visitFieldInsn(PUTFIELD, outerClassName, field.getName(), field.getDescriptor());

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);  // COMPUTE_FRAMES will calculate
        mv.visitEnd();
    }

    /**
     * Generates the static initializer that creates and assigns the INSTANCE.
     */
    private static void generateStaticInitializer(ClassWriter cw, String innerClassName) {
        MethodVisitor mv = cw.visitMethod(
                ACC_STATIC,
                "<clinit>",
                "()V",
                null,
                null
        );
        mv.visitCode();

        // INSTANCE = new InnerClass();
        mv.visitTypeInsn(NEW, innerClassName);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, innerClassName, "<init>", "()V", false);
        mv.visitFieldInsn(PUTSTATIC, innerClassName, "INSTANCE", "L" + innerClassName + ";");

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);  // COMPUTE_FRAMES will calculate
        mv.visitEnd();
    }

    /**
     * Generates bytecode for an accessor reader inner class.
     *
     * @param innerClassName Fully qualified internal name of inner class
     * @param outerClassName Fully qualified internal name of outer class
     * @param field          Field metadata
     * @param cacheKey       Cache key for registration
     * @return Bytecode for the inner class
     */
    public static byte[] generateReader(String innerClassName, String outerClassName,
                                        FieldMetadata field, String cacheKey) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        // Determine generic signature for the interface
        String fieldTypeSignature;
        if (field.isPrimitive()) {
            // For primitives, use the wrapper type in the generic signature
            String wrapperClass = TypeDescriptorHelper.getPrimitiveWrapper(field.getDescriptor());
            fieldTypeSignature = "L" + wrapperClass + ";";
        } else {
            fieldTypeSignature = field.getDescriptor();
        }

        // Generic signature: implements HibernateAccessorValueReader<FieldType>
        String classSignature = "Ljava/lang/Object;L" + READER_INTERFACE + "<" + fieldTypeSignature + ">;";

        // Visit class header
        cw.visit(
                V11,  // Java 11 bytecode
                ACC_PUBLIC | ACC_SUPER | ACC_STATIC,
                innerClassName,
                classSignature,
                "java/lang/Object",
                new String[]{READER_INTERFACE}
        );

        // Declare nest host so the inner class can access private fields of the outer class
        cw.visitNestHost(outerClassName);

        // Declare this as inner class of outer class
        String simpleInnerName = innerClassName.substring(innerClassName.lastIndexOf('$') + 1);
        cw.visitInnerClass(
                innerClassName,
                outerClassName,
                simpleInnerName,
                ACC_PUBLIC | ACC_STATIC
        );

        // Generate INSTANCE static field
        generateInstanceField(cw, innerClassName);

        // Generate constructor for reader
        generateReaderConstructor(cw, innerClassName, cacheKey, FACTORY_CLASS);

        // Generate get() method
        generateGetMethod(cw, outerClassName, field);

        // Generate static initializer
        generateStaticInitializer(cw, innerClassName);

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates the constructor for reader that registers in HibernateAccessorFactory.readerCache.
     */
    private static void generateReaderConstructor(ClassWriter cw, String innerClassName, String cacheKey,
                                                   String factoryClassName) {
        MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null
        );
        mv.visitCode();

        // Call super()
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        // HibernateAccessorFactory.readerCache.put(cacheKey, this)
        mv.visitFieldInsn(GETSTATIC, factoryClassName, "readerCache", "Ljava/util/Map;");
        mv.visitLdcInsn(cacheKey);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(
                INVOKEINTERFACE,
                "java/util/Map",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                true
        );
        mv.visitInsn(POP);  // Discard return value from put()

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);  // COMPUTE_FRAMES will calculate
        mv.visitEnd();
    }

    /**
     * Generates the get(Object) method that reads the field value.
     */
    private static void generateGetMethod(ClassWriter cw, String outerClassName, FieldMetadata field) {
        MethodVisitor mv = cw.visitMethod(
                ACC_PUBLIC,
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null,
                null
        );
        mv.visitCode();

        // Cast parameter to owner class
        // ((OwnerClass) var1)
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, outerClassName);

        // Get the field value
        mv.visitFieldInsn(GETFIELD, outerClassName, field.getName(), field.getDescriptor());

        // Box primitive if necessary
        if (field.isPrimitive()) {
            String wrapperClass = TypeDescriptorHelper.getPrimitiveWrapper(field.getDescriptor());
            String valueOfDescriptor = "(" + field.getDescriptor() + ")L" + wrapperClass + ";";
            mv.visitMethodInsn(INVOKESTATIC, wrapperClass, "valueOf", valueOfDescriptor, false);
        }

        // Return the value
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);  // COMPUTE_FRAMES will calculate
        mv.visitEnd();
    }
}
