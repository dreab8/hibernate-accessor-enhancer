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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * Tests that bytecode-enhanced accessors work correctly across an inheritance
 * hierarchy.
 *
 * <p>{@link BasePublication} declares {@code @Id Long id} and
 * {@code @Basic String title}. {@link Novel} extends it and adds
 * {@code @Basic String genre} and {@code @ManyToMany Set<Author> authors}.
 *
 * <p>Both classes are enhanced independently. The key verification is that
 * the accessors generated for BasePublication's fields (id, title) can
 * set and get values on a Novel instance, since Novel inherits those fields.
 * The generated writer does {@code CHECKCAST BasePublication} followed by
 * {@code PUTFIELD BasePublication.id} — this succeeds on a Novel instance
 * because Novel IS-A BasePublication.
 */
public class TestEnhancedAccessorsWithInheritance {

	private static final String BASE_CLASS = "org.hibernate.accessor.bytecode.BasePublication";
	private static final String NOVEL_CLASS = "org.hibernate.accessor.bytecode.Novel";

	/** Custom ClassLoader hosting entity classes and generated inner classes. */
	private static ClassLoader enhancedCl;
	private static Class<?> baseClass;
	private static Class<?> novelClass;

	/**
	 * Enhances both BasePublication and Novel, then creates a custom ClassLoader
	 * that hosts the original entity bytecodes and all generated accessor inner
	 * classes in the same runtime package.
	 */
	@BeforeAll
	static void setup() throws Exception {
		HibernateAccessorFactory.writerCache.clear();
		HibernateAccessorFactory.readerCache.clear();

		Map<String, byte[]> classMap = new HashMap<>();
		classMap.put( BASE_CLASS, readClassBytes( BASE_CLASS ) );
		classMap.put( NOVEL_CLASS, readClassBytes( NOVEL_CLASS ) );

		// Enhance both classes independently
		enhanceAndCollect( BASE_CLASS, classMap );
		enhanceAndCollect( NOVEL_CLASS, classMap );

		enhancedCl = new ClassLoader( TestEnhancedAccessorsWithInheritance.class.getClassLoader() ) {
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

		baseClass = enhancedCl.loadClass( BASE_CLASS );
		novelClass = enhancedCl.loadClass( NOVEL_CLASS );
	}

	private static byte[] readClassBytes(String className) throws IOException {
		String path = className.replace( '.', '/' ) + ".class";
		try ( InputStream is = TestEnhancedAccessorsWithInheritance.class.getClassLoader().getResourceAsStream( path ) ) {
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

		for ( AccessorWriterClassVisitor.InnerClassInfo info : enhancer.getInnerClasses() ) {
			classMap.put( info.getClassName().replace( '/', '.' ), info.getBytecode() );
		}
	}

	private static Object getAccessorInstance(String className) throws Exception {
		Class<?> clazz = enhancedCl.loadClass( className );
		return clazz.getField( "INSTANCE" ).get( null );
	}

	private static Object newNovel() throws Exception {
		return novelClass.getDeclaredConstructor().newInstance();
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

	// ---- Superclass writer on subclass instance ----
	// These are the core inheritance tests: BasePublication's accessors
	// must work on a Novel instance because Novel inherits those fields.

	@Test
	void superclassWriterSetsIdOnSubclassInstance() throws Exception {
		Object novel = newNovel();
		Object writer = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__BasePublication__id" );

		invokeSet( writer, novel, 42L );

		assertEquals( 42L, getField( novel, "id" ) );
	}

	@Test
	void superclassWriterSetsTitleOnSubclassInstance() throws Exception {
		Object novel = newNovel();
		Object writer = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__BasePublication__title" );

		invokeSet( writer, novel, "War and Peace" );

		assertEquals( "War and Peace", getField( novel, "title" ) );
	}

	// ---- Superclass reader on subclass instance ----

	@Test
	void superclassReaderGetsIdFromSubclassInstance() throws Exception {
		Object novel = newNovel();
		setField( novel, "id", 99L );

		Object reader = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__BasePublication__id" );

		assertEquals( 99L, invokeGet( reader, novel ) );
	}

	@Test
	void superclassReaderGetsTitleFromSubclassInstance() throws Exception {
		Object novel = newNovel();
		setField( novel, "title", "1984" );

		Object reader = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__BasePublication__title" );

		assertEquals( "1984", invokeGet( reader, novel ) );
	}

	@Test
	void superclassReaderReturnsNullForUnsetIdOnSubclassInstance() throws Exception {
		Object novel = newNovel();

		Object reader = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__BasePublication__id" );

		assertNull( invokeGet( reader, novel ) );
	}

	// ---- Subclass's own writer/reader ----
	// Verify that Novel's own accessors still work as expected.

	@Test
	void subclassWriterSetsGenre() throws Exception {
		Object novel = newNovel();
		Object writer = getAccessorInstance(
				NOVEL_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__Novel__genre" );

		invokeSet( writer, novel, "Science Fiction" );

		assertEquals( "Science Fiction", getField( novel, "genre" ) );
	}

	@Test
	void subclassWriterSetsAuthors() throws Exception {
		Object novel = newNovel();
		Object writer = getAccessorInstance(
				NOVEL_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__Novel__authors" );
		Set<Object> authors = new HashSet<>();

		invokeSet( writer, novel, authors );

		assertEquals( authors, getField( novel, "authors" ) );
	}

	@Test
	void subclassReaderGetsGenre() throws Exception {
		Object novel = newNovel();
		setField( novel, "genre", "Fantasy" );

		Object reader = getAccessorInstance(
				NOVEL_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__Novel__genre" );

		assertEquals( "Fantasy", invokeGet( reader, novel ) );
	}

	@Test
	void subclassReaderGetsAuthors() throws Exception {
		Object novel = newNovel();
		Set<Object> authors = new HashSet<>();
		setField( novel, "authors", authors );

		Object reader = getAccessorInstance(
				NOVEL_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__Novel__authors" );

		assertEquals( authors, invokeGet( reader, novel ) );
	}

	// ---- Round-trip: superclass accessor on subclass instance ----

	@Test
	void superclassWriterAndReaderRoundTripOnSubclassInstance() throws Exception {
		Object novel = newNovel();

		Object idWriter = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__BasePublication__id" );
		Object idReader = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__BasePublication__id" );

		invokeSet( idWriter, novel, 123L );

		assertEquals( 123L, invokeGet( idReader, novel ) );
	}

	@Test
	void superclassTitleRoundTripOnSubclassInstance() throws Exception {
		Object novel = newNovel();

		Object titleWriter = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__BasePublication__title" );
		Object titleReader = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__BasePublication__title" );

		invokeSet( titleWriter, novel, "Brave New World" );

		assertEquals( "Brave New World", invokeGet( titleReader, novel ) );
	}

	// ---- Combined: set both superclass and subclass fields on same instance ----

	@Test
	void settingBothSuperclassAndSubclassFieldsOnSameInstance() throws Exception {
		Object novel = newNovel();

		// Set superclass fields via BasePublication accessors
		Object idWriter = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__BasePublication__id" );
		Object titleWriter = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__BasePublication__title" );
		invokeSet( idWriter, novel, 7L );
		invokeSet( titleWriter, novel, "Dune" );

		// Set subclass fields via Novel accessors
		Object genreWriter = getAccessorInstance(
				NOVEL_CLASS + AccessorWriterClassVisitor.WRITER_INFIX + "__Novel__genre" );
		invokeSet( genreWriter, novel, "Science Fiction" );

		// Read all fields back and verify
		Object idReader = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__BasePublication__id" );
		Object titleReader = getAccessorInstance(
				BASE_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__BasePublication__title" );
		Object genreReader = getAccessorInstance(
				NOVEL_CLASS + AccessorWriterClassVisitor.READER_INFIX + "__Novel__genre" );

		assertEquals( 7L, invokeGet( idReader, novel ) );
		assertEquals( "Dune", invokeGet( titleReader, novel ) );
		assertEquals( "Science Fiction", invokeGet( genreReader, novel ) );
	}
}
