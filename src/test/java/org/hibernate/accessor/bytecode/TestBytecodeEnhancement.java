package org.hibernate.accessor.bytecode;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class TestBytecodeEnhancement {
	private static final String ENHANCED_BOOK_PATH =
			"enhanced_output/org/hibernate/processor/test/data/basic/Book.class";

	private static final String ENHANCED_ISBN_WRITER_PATH =
			"enhanced_output/org/hibernate/processor/test/data/basic/Book$$$__HibernateAccessorValueWriter__Book__isbn.class";

	@Test
	void shouldDeclareExpectedInnerClasses() throws Exception {
		// Read the enhanced Book.class file
		byte[] classBytes = Files.readAllBytes( Paths.get( ENHANCED_BOOK_PATH ) );

		ClassReader reader = new ClassReader( classBytes );
		BytecodeVerifier verifier = new BytecodeVerifier();
		reader.accept( verifier, 0 );

		// Expected inner classes for Book (writers only, as we're testing the writer accessor)
		Set<String> expectedWriterClasses = new HashSet<>();
		expectedWriterClasses.add(
				"org/hibernate/processor/test/data/basic/Book$$$__HibernateAccessorValueWriter__Book__isbn" );
		expectedWriterClasses.add(
				"org/hibernate/processor/test/data/basic/Book$$$__HibernateAccessorValueWriter__Book__title" );
		expectedWriterClasses.add(
				"org/hibernate/processor/test/data/basic/Book$$$__HibernateAccessorValueWriter__Book__text" );
		expectedWriterClasses.add(
				"org/hibernate/processor/test/data/basic/Book$$$__HibernateAccessorValueWriter__Book__publicationDate" );
		expectedWriterClasses.add(
				"org/hibernate/processor/test/data/basic/Book$$$__HibernateAccessorValueWriter__Book__authors" );

		// Verify all expected writer classes are declared
		assertTrue(
				verifier.innerClasses.containsAll( expectedWriterClasses ),
				() -> "Not all expected writer inner classes found. Expected: " + expectedWriterClasses +
						", Found: " + verifier.innerClasses
		);

		// Verify we found at least the expected number (may be more with readers)
		assertTrue(
				verifier.innerClasses.size() >= expectedWriterClasses.size(),
				"Expected at least " + expectedWriterClasses.size() + " inner classes, but found " + verifier.innerClasses.size()
		);
	}

	@Test
	void writerShouldImplementHibernateAccessorValueWriterInterface() throws Exception {
		InnerClassVerifier verifier = verifyIsbnWriter();

		assertTrue(
				verifier.implementsInterface,
				"Inner class should implement HibernateAccessorValueWriter interface"
		);
	}

	@Test
	void writerShouldHaveStaticFinalInstanceField() throws Exception {
		InnerClassVerifier verifier = verifyIsbnWriter();

		assertTrue(
				verifier.hasInstanceField,
				"Inner class should have public static final INSTANCE field"
		);
	}

	@Test
	void writerShouldHaveSetMethod() throws Exception {
		InnerClassVerifier verifier = verifyIsbnWriter();

		assertTrue(
				verifier.hasSetMethod,
				"Inner class should have set(Object, Object) method"
		);
	}

	@Test
	void writerShouldHaveConstructor() throws Exception {
		InnerClassVerifier verifier = verifyIsbnWriter();

		assertTrue(
				verifier.hasConstructor,
				"Inner class should have a constructor"
		);
	}

	@Test
	void writerShouldHaveStaticInitializer() throws Exception {
		InnerClassVerifier verifier = verifyIsbnWriter();

		assertTrue(
				verifier.hasStaticInit,
				"Inner class should have static initializer (<clinit>)"
		);
	}

	@Test
	void writerConstructorShouldRegisterInCache() throws Exception {
		InnerClassVerifier verifier = verifyIsbnWriter();

		assertTrue(
				verifier.constructorRegistersInCache,
				"Constructor should register instance in HibernateAccessorFactory.writerCache"
		);
	}

	@Test
	void writerSetMethodShouldContainPutFieldInstruction() throws Exception {
		InnerClassVerifier verifier = verifyIsbnWriter();

		assertTrue(
				verifier.setMethodHasPutField,
				"set() method should contain PUTFIELD instruction for isbn field"
		);
	}

	@Test
	void writerSetMethodShouldCastParameterToBook() throws Exception {
		InnerClassVerifier verifier = verifyIsbnWriter();

		assertTrue(
				verifier.setMethodCastsToBook,
				"set() method should cast first parameter to Book"
		);
	}

	/**
	 * Helper method to verify the isbn writer accessor.
	 */
	private InnerClassVerifier verifyIsbnWriter() throws Exception {
		byte[] isbnAccessorBytes = Files.readAllBytes( Paths.get( ENHANCED_ISBN_WRITER_PATH ) );

		ClassReader innerReader = new ClassReader( isbnAccessorBytes );
		InnerClassVerifier verifier = new InnerClassVerifier();
		innerReader.accept( verifier, 0 );

		return verifier;
	}

	/**
	 * ASM ClassVisitor to verify outer class structure.
	 */
	static class BytecodeVerifier extends ClassVisitor {
		Set<String> innerClasses = new HashSet<>();

		public BytecodeVerifier() {
			super( Opcodes.ASM9 );
		}

		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access) {
			if ( name.contains( "HibernateAccessorValueWriter" ) || name.contains( "HibernateAccessorValueReader" ) ) {
				innerClasses.add( name );
			}
		}
	}

	/**
	 * ASM ClassVisitor to verify inner class structure.
	 */
	static class InnerClassVerifier extends ClassVisitor {
		boolean implementsInterface = false;
		boolean hasInstanceField = false;
		boolean hasSetMethod = false;
		boolean hasConstructor = false;
		boolean hasStaticInit = false;
		boolean constructorRegistersInCache = false;
		boolean setMethodHasPutField = false;
		boolean setMethodCastsToBook = false;

		public InnerClassVerifier() {
			super( Opcodes.ASM9 );
		}

		@Override
		public void visit(
				int version, int access, String name, String signature,
				String superName, String[] interfaces) {
			if ( interfaces != null ) {
				for ( String iface : interfaces ) {
					if ( iface.endsWith( "HibernateAccessorValueWriter" ) ) {
						implementsInterface = true;
						break;
					}
				}
			}
			super.visit( version, access, name, signature, superName, interfaces );
		}

		@Override
		public FieldVisitor visitField(
				int access, String name, String descriptor,
				String signature, Object value) {
			if ( "INSTANCE".equals( name ) &&
					( access & Opcodes.ACC_STATIC ) != 0 &&
					( access & Opcodes.ACC_FINAL ) != 0 ) {
				hasInstanceField = true;
			}
			return super.visitField( access, name, descriptor, signature, value );
		}

		@Override
		public MethodVisitor visitMethod(
				int access, String name, String descriptor,
				String signature, String[] exceptions) {
			if ( "set".equals( name ) && "(Ljava/lang/Object;Ljava/lang/Object;)V".equals( descriptor ) ) {
				hasSetMethod = true;
				return new MethodVisitor(
						Opcodes.ASM9,
						super.visitMethod( access, name, descriptor, signature, exceptions )
				) {
					@Override
					public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
						if ( opcode == Opcodes.PUTFIELD && "isbn".equals( fieldName ) ) {
							setMethodHasPutField = true;
						}
						super.visitFieldInsn( opcode, owner, fieldName, fieldDescriptor );
					}

					@Override
					public void visitTypeInsn(int opcode, String type) {
						if ( opcode == Opcodes.CHECKCAST && type.contains( "Book" ) ) {
							setMethodCastsToBook = true;
						}
						super.visitTypeInsn( opcode, type );
					}
				};
			}
			else if ( "<init>".equals( name ) ) {
				hasConstructor = true;
				return new MethodVisitor(
						Opcodes.ASM9,
						super.visitMethod( access, name, descriptor, signature, exceptions )
				) {
					@Override
					public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDescriptor) {
						if ( opcode == Opcodes.GETSTATIC &&
								"writerCache".equals( fieldName ) &&
								owner.endsWith( "HibernateAccessorFactory" ) ) {
							constructorRegistersInCache = true;
						}
						super.visitFieldInsn( opcode, owner, fieldName, fieldDescriptor );
					}
				};
			}
			else if ( "<clinit>".equals( name ) ) {
				hasStaticInit = true;
			}

			return super.visitMethod( access, name, descriptor, signature, exceptions );
		}
	}
}
