package com.damla.gauge

/** Stands in for the JVM's `@Synchronized` used by :gauge: JavaScript runs one thread, nothing to guard. */
@Target(AnnotationTarget.FUNCTION)
internal annotation class Synchronized
