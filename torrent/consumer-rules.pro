# libtorrent4j es una capa SWIG: la biblioteca nativa busca sus clases y métodos de Java por su nombre (y llama a los "listeners")
-keep class org.libtorrent4j.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn org.libtorrent4j.**
