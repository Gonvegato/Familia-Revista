package org.robolectric

/**
 * Las pruebas compartidas con Cobalto de Android (la mezcla, la estructura) llevan `@RunWith(RobolectricTestRunner::class)` solo para
 * tener org.json en el móvil. En el ordenador org.json es una biblioteca normal: este «Robolectric» es el ejecutor de JUnit de siempre.
 */
class RobolectricTestRunner(klass: Class<*>) : org.junit.runners.BlockJUnit4ClassRunner(klass)
