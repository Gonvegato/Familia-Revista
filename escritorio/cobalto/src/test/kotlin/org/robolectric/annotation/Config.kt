package org.robolectric.annotation

/** (Ver RobolectricTestRunner: en el ordenador no hace nada.) */
@Retention(AnnotationRetention.RUNTIME)
annotation class Config(val sdk: IntArray = [])
