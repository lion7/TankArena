package nl.tankarena.app

/** Milliseconds the garbage collector has spent so far, for [PerfLog]; 0 where unknown. */
internal expect fun collectionMillis(): Long
