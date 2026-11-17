package io.github.tancan1998.xviewtv

import java.util.concurrent.atomic.AtomicInteger

/** Numbers only, never retain a Context/View/Player; shown solely by debug builds. */
internal object PlayerDiagnostics { val active = AtomicInteger(0) }
