/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.accessor.bytecode;

import org.jboss.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Processes .class files and adds HibernateAccessorValueWriter inner classes
 * for persistent fields.
 */
public class HibernateAccessorEnhancer {

    private static final Logger LOG = Logger.getLogger( HibernateAccessorEnhancer.class );

    private final boolean allFields;

    /**
     * Creates an enhancer that only processes JPA-annotated fields.
     */
    public HibernateAccessorEnhancer() {
        this(false);
    }

    /**
     * Creates an enhancer with configurable field processing.
     *
     * @param allFields if true, processes all non-static/non-transient fields;
     *                  if false, only processes JPA-annotated fields
     */
    public HibernateAccessorEnhancer(boolean allFields) {
        this.allFields = allFields;
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            printUsage();
            System.exit(1);
        }

        String inputPath = null;
        String outputPath = null;
        boolean allFields = false;

        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            if ("--input".equals(args[i]) && i + 1 < args.length) {
                inputPath = args[++i];
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputPath = args[++i];
            } else if ("--all-fields".equals(args[i])) {
                allFields = true;
            }
        }

        if (inputPath == null || outputPath == null) {
            LOG.error( "Both --input and --output are required" );
            printUsage();
            System.exit(1);
        }

        try {
            Path input = Paths.get(inputPath);
            Path output = Paths.get(outputPath);

            if (!Files.exists(input)) {
                LOG.errorf( "Input path does not exist: %s", inputPath );
                System.exit(1);
            }

            // Create output directory if it doesn't exist
            Files.createDirectories(output);

            if (allFields) {
                LOG.info( "Mode: Processing ALL fields (not just JPA-annotated)" );
            } else {
                LOG.info( "Mode: Processing only JPA-annotated fields" );
            }

            HibernateAccessorEnhancer enhancer = new HibernateAccessorEnhancer( allFields);
            enhancer.processDirectory(input, output);

        } catch (IOException e) {
            LOG.error( "Error processing files", e );
            System.exit(1);
        }
    }

    private static void printUsage() {
        LOG.info( "Usage: java -jar hibernate-accessor-enhancer.jar --input <path> --output <path> [--all-fields]" );
        LOG.info( "Options:" );
        LOG.info( "  --input       Directory containing .class files to enhance" );
        LOG.info( "  --output      Directory where enhanced .class files will be written" );
        LOG.info( "  --all-fields  Process ALL fields (default: only JPA-annotated fields)" );
    }

    /**
     * Processes all .class files in a directory.
     */
    private void processDirectory(Path inputDir, Path outputDir) throws IOException {
        List<Path> classFiles;

        try (Stream<Path> paths = Files.walk(inputDir)) {
            classFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .collect(Collectors.toList());
        }

        if (classFiles.isEmpty()) {
            LOG.infof( "No .class files found in %s", inputDir );
            return;
        }

        int processedCount = 0;
        int enhancedCount = 0;
        int innerClassCount = 0;

        for (Path classFile : classFiles) {
            try {
                byte[] originalBytes = Files.readAllBytes(classFile);
                EnhancementResult result = enhanceClass(originalBytes);

                processedCount++;

                if (result.isEnhanced()) {
                    enhancedCount++;
                    innerClassCount += result.getInnerClasses().size();

                    // Write enhanced outer class
                    Path relativePath = inputDir.relativize(classFile);
                    Path outputFile = outputDir.resolve(relativePath);
                    Files.createDirectories(outputFile.getParent());
                    Files.write(outputFile, result.getEnhancedBytes());

                    // Write inner class files
                    for (AccessorWriterClassVisitor.InnerClassInfo innerClass : result.getInnerClasses()) {
                        String innerClassName = innerClass.getClassName();
                        String innerClassFileName = innerClassName.replace('/', java.io.File.separatorChar) + ".class";
                        Path innerClassFile = outputDir.resolve(innerClassFileName);
                        Files.createDirectories(innerClassFile.getParent());
                        Files.write(innerClassFile, innerClass.getBytecode());
                    }

                    LOG.infof( "Enhanced: %s (+%d accessors)", relativePath, result.getInnerClasses().size() );
                } else {
                    // Copy unchanged
                    Path relativePath = inputDir.relativize(classFile);
                    Path outputFile = outputDir.resolve(relativePath);
                    Files.createDirectories(outputFile.getParent());
                    Files.write(outputFile, originalBytes);
                }

            } catch (Exception e) {
                LOG.errorf( e, "Error processing %s", classFile );
            }
        }

        LOG.infof( "Summary: Processed %d classes, Enhanced %d classes, Generated %d accessor writers",
                processedCount, enhancedCount, innerClassCount );
    }

    /**
     * Enhances a single class using the two-pass visitor pattern.
     *
     * @param originalBytes Original class bytecode
     * @return Enhancement result
     */
    private EnhancementResult enhanceClass(byte[] originalBytes) {
        // Pass 1: Collect persistent fields
        ClassReader reader1 = new ClassReader(originalBytes);
        PersistentFieldCollector collector = new PersistentFieldCollector(allFields);
        reader1.accept(collector, ClassReader.SKIP_DEBUG);

        List<FieldMetadata> persistentFields = collector.getPersistentFields();

        if (persistentFields.isEmpty()) {
            // No enhancement needed
            return new EnhancementResult(originalBytes, null, false);
        }

        // Pass 2: Generate enhanced bytecode
        ClassReader reader2 = new ClassReader(originalBytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        AccessorWriterClassVisitor enhancer = new AccessorWriterClassVisitor(writer, persistentFields);
        reader2.accept(enhancer, ClassReader.SKIP_DEBUG);

        byte[] enhancedBytes = writer.toByteArray();
        List<AccessorWriterClassVisitor.InnerClassInfo> innerClasses = enhancer.getInnerClasses();

        return new EnhancementResult(enhancedBytes, innerClasses, true);
    }

    /**
     * Holds the result of a class enhancement operation.
     */
    private static class EnhancementResult {
        private final byte[] enhancedBytes;
        private final List<AccessorWriterClassVisitor.InnerClassInfo> innerClasses;
        private final boolean enhanced;

        public EnhancementResult(byte[] enhancedBytes,
                                 List<AccessorWriterClassVisitor.InnerClassInfo> innerClasses,
                                 boolean enhanced) {
            this.enhancedBytes = enhancedBytes;
            this.innerClasses = innerClasses;
            this.enhanced = enhanced;
        }

        public byte[] getEnhancedBytes() {
            return enhancedBytes;
        }

        public List<AccessorWriterClassVisitor.InnerClassInfo> getInnerClasses() {
            return innerClasses;
        }

        public boolean isEnhanced() {
            return enhanced;
        }
    }
}
