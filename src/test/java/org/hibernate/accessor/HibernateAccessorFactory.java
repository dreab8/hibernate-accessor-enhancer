package org.hibernate.accessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test stub that shadows the core library's HibernateAccessorFactory interface.
 * The generated inner class constructors register themselves in
 * {@code HibernateAccessorFactory.writerCache} / {@code readerCache}
 * via GETSTATIC + Map.put. This stub provides those static Map fields
 * so that class loading of the generated accessors succeeds at runtime.
 */
public class HibernateAccessorFactory {
	public static final Map<String, Object> writerCache = new ConcurrentHashMap<>();
	public static final Map<String, Object> readerCache = new ConcurrentHashMap<>();
}
