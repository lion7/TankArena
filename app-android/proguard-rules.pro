# R8 rules for the release build (the Kotlin toolchain applies them automatically).

# Ktor's debugger detection reads java.lang.management, which Android does not have; Ktor catches
# the failure, so the reference can stay unresolved.
-dontwarn java.lang.management.**
