# The API models are read by kotlinx.serialization; its own rules cover them, this is insurance
# against a missing field name in a release build only.
-keep class com.infraviored.glimpse.data.** { *; }
