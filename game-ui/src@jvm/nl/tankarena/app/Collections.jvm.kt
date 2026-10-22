package nl.tankarena.app

import java.lang.management.ManagementFactory

internal actual fun collectionMillis(): Long =
    ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime.coerceAtLeast(0) }
