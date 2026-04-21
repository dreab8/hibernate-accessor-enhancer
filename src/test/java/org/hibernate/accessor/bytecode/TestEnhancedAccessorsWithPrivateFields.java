package org.hibernate.accessor.bytecode;

import org.hibernate.accessor.HibernateAccessorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * Tests that bytecode-enhanced accessors work correctly on private fields.
 *
 * <p>Without nest-based access control ({@code visitNestHost} / {@code visitNestMember}),
 * the generated inner classes would throw {@link IllegalAccessError} when attempting
 * {@code PUTFIELD} or {@code GETFIELD} on a private field. This test verifies that the
 * enhancer correctly declares the nest relationship so that private field access succeeds.
 *
 * <p>Also tests inheritance: {@link PrivateFieldSubEntity} extends {@link PrivateFieldEntity},
 * and the superclass's private field accessors must work on subclass instances.
 */
public class TestEnhancedAccessorsWithPrivateFields {

	private static final String ENTITY_CLASS = "org.hibernate.accessor.bytecode.PrivateFieldEntity";
	private static final String SUB_ENTITY_CLASS = "org.hibernate.accessor.bytecode.PrivateFieldSubEntity";

	private static ClassLoader enhancedCl;
	private static Class<?> entityClass;
	private static Class<?> subEntityClass;

	@BeforeAll
	static void setup() throws Exception {
		HibernateAccessorFactory.writerCache.clear();
		HibernateAccessorFactory.readerCache.clear();

		Map<String, byte[]> classMap = new HashMap<>();
		classMap.put( ENTITY_CLASS, readClassBytes( ENTITY_CLASS ) );
		classMap.put( SUB_ENTITY_CLASS, readClassBytes( SUB_ENTITY_CLASS ) );

		enhanceAndCollect( ENTITY_CLASS, classMap );
		enhanceAndCollect( SUB_ENTITY_CLASS, classMap );

		enhancedCl = new ClassLoader( TestEnhancedAccessorsWithPrivateFields.class.getClassLoader() ) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				synchronized ( getClassLoadingLock( name ) ) {
					Class<?> c = findLoadedClass( name );
					if ( c != null ) {
						return c;
					}
					byte[] bytes = classMap.get( name );
					if ( bytes != null ) {
						c = defineClass( name, bytes, 0, bytes.length );
						if ( resolve ) {
							resolveClass( c );
						}
						return c;
					}
					return super.loadClass( name, resolve );
				}
			}
		};

		entityClass = enhancedCl.loadClass( ENTITY_CLASS );
		subEntityClass = enhancedCl.loadClass( SUB_ENTITY_CLASS );
	}

	private static byte[] readClassBytes(String className) throws IOException {
		String path = className.replace( '.', '/' ) + ".class";
		try ( InputStream is = TestEnhancedAccessorsWithPrivateFields.class.getClassLoader().getResourceAsStream( path ) ) {
			return is.readAllBytes();
		}
	}

	private static void enhanceAndCollect(String className, Map<String, byte[]> classMap) throws IOException {
		byte[] originalBytes = classMap.get( className );

		ClassReader reader = new ClassReader( originalBytes );
		PersistentFieldCollector collector = new PersistentFieldCollector();
		reader.accept( collector, 0 );

		ClassWriter writer = new ClassWriter( reader, 0 );
		AccessorWriterClassVisitor enhancer = new AccessorWriterClassVisitor(
				writer, collector.getPersistentFields()
		);
		reader.accept( enhancer, 0 );

		// Store the enhanced outer class bytecode (with NestMembers attribute)
		classMap.put( className, writer.toByteArray() );

		for ( AccessorWriterClassVisitor.InnerClassInfo info : enhancer.getInnerClasses() ) {
			classMap.put( info.getClassName().replace( '/', '.' ), info.getBytecode() );
		}
	}

	private static Object getAccessorInstance(String className) throws Exception {
		Class<?> clazz = enhancedCl.loadClass( className );
		return clazz.getField( "INSTANCE" ).get( null );
	}

	private static Object newEntity() throws Exception {
		return entityClass.getDeclaredConstructor().newInstance();
	}

	private static Object newSubEntity() throws Exception {
		return subEntityClass.getDeclaredConstructor().newInstance();
	}

	private static Object getField(Object obj, String fieldName) throws Exception {
		Class<?> clazz = obj.getClass();
		while ( clazz != null ) {
			try {
				Field f = clazz.getDeclaredField( fieldName );
				f.setAccessible( true );
				return f.get( obj );
			}
			catch ( NoSuchFieldException e ) {
				clazz = clazz.getSuperclass();
			}
		}
		throw new NoSuchFieldException( fieldName );
	}

	private static void setField(Object obj, String fieldName, Object value) throws Exception {
		Class<?> clazz = obj.getClass();
		while ( clazz != null ) {
			try {
				Field f = clazz.getDeclaredField( fieldName );
				f.setAccessible( true );
				f.set( obj, value );
				return;
			}
			catch ( NoSuchFieldException e ) {
				clazz = clazz.getSuperclass();
			}
		}
		throw new NoSuchFieldException( fieldName );
	}

	private static void invokeSet(Object accessor, Object target, Object value) throws Exception {
		accessor.getClass()
				.getMethod( "set", Object.class, Object.class )
				.invoke( accessor, target, value );
	}

	private static Object invokeGet(Object accessor, Object target) throws Exception {
		return accessor.getClass()
				.getMethod( "get", Object.class )
				.invoke( accessor, target );
	}

	// ---- Private field writer tests ----

	@Test
	void writerSetsPrivateId() throws Exception {
		Object entity = newEntity();
		Object writer = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__PrivateFieldEntity__id" );

		invokeSet( writer, entity, 42L );

		assertEquals( 42L, getField( entity, "id" ) );
	}

	@Test
	void writerSetsPrivateName() throws Exception {
		Object entity = newEntity();
		Object writer = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__PrivateFieldEntity__name" );

		invokeSet( writer, entity, "test-name" );

		assertEquals( "test-name", getField( entity, "name" ) );
	}

	// ---- Private field reader tests ----

	@Test
	void readerGetsPrivateId() throws Exception {
		Object entity = newEntity();
		setField( entity, "id", 99L );

		Object reader = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__PrivateFieldEntity__id" );

		assertEquals( 99L, invokeGet( reader, entity ) );
	}

	@Test
	void readerGetsPrivateName() throws Exception {
		Object entity = newEntity();
		setField( entity, "name", "hello" );

		Object reader = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__PrivateFieldEntity__name" );

		assertEquals( "hello", invokeGet( reader, entity ) );
	}

	@Test
	void readerReturnsNullForUnsetPrivateField() throws Exception {
		Object entity = newEntity();

		Object reader = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__PrivateFieldEntity__id" );

		assertNull( invokeGet( reader, entity ) );
	}

	// ---- Round-trip on private fields ----

	@Test
	void privateFieldWriterAndReaderRoundTrip() throws Exception {
		Object entity = newEntity();

		Object writer = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__PrivateFieldEntity__name" );
		Object reader = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__PrivateFieldEntity__name" );

		invokeSet( writer, entity, "round-trip" );

		assertEquals( "round-trip", invokeGet( reader, entity ) );
	}

	// ---- Inheritance: superclass private field accessors on subclass instance ----

	@Test
	void superclassWriterSetsPrivateIdOnSubclassInstance() throws Exception {
		Object sub = newSubEntity();
		Object writer = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__PrivateFieldEntity__id" );

		invokeSet( writer, sub, 77L );

		assertEquals( 77L, getField( sub, "id" ) );
	}

	@Test
	void superclassReaderGetsPrivateIdFromSubclassInstance() throws Exception {
		Object sub = newSubEntity();
		setField( sub, "id", 55L );

		Object reader = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__PrivateFieldEntity__id" );

		assertEquals( 55L, invokeGet( reader, sub ) );
	}

	@Test
	void subclassWriterSetsPrivateDescription() throws Exception {
		Object sub = newSubEntity();
		Object writer = getAccessorInstance(
				SUB_ENTITY_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__PrivateFieldSubEntity__description" );

		invokeSet( writer, sub, "a description" );

		assertEquals( "a description", getField( sub, "description" ) );
	}

	@Test
	void subclassReaderGetsPrivateDescription() throws Exception {
		Object sub = newSubEntity();
		setField( sub, "description", "some text" );

		Object reader = getAccessorInstance(
				SUB_ENTITY_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__PrivateFieldSubEntity__description" );

		assertEquals( "some text", invokeGet( reader, sub ) );
	}

	// ---- Combined: set both superclass and subclass private fields ----

	@Test
	void settingBothSuperclassAndSubclassPrivateFields() throws Exception {
		Object sub = newSubEntity();

		Object idWriter = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__PrivateFieldEntity__id" );
		Object nameWriter = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__PrivateFieldEntity__name" );
		Object descWriter = getAccessorInstance(
				SUB_ENTITY_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__PrivateFieldSubEntity__description" );

		invokeSet( idWriter, sub, 1L );
		invokeSet( nameWriter, sub, "entity" );
		invokeSet( descWriter, sub, "desc" );

		Object idReader = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__PrivateFieldEntity__id" );
		Object nameReader = getAccessorInstance(
				ENTITY_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__PrivateFieldEntity__name" );
		Object descReader = getAccessorInstance(
				SUB_ENTITY_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__PrivateFieldSubEntity__description" );

		assertEquals( 1L, invokeGet( idReader, sub ) );
		assertEquals( "entity", invokeGet( nameReader, sub ) );
		assertEquals( "desc", invokeGet( descReader, sub ) );
	}
}
