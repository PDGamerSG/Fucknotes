# Fucknotes release rules. Library rules (Room, Compose, Ink, lifecycle) ship inside the artifacts.

# kotlinx.serialization: keep generated serializers and the companions that hand them out.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.fucknotes.**$$serializer { *; }
-keepclassmembers class com.example.fucknotes.** { *** Companion; }
-keepclasseswithmembers class com.example.fucknotes.** { kotlinx.serialization.KSerializer serializer(...); }

# Enum names are stored in the database and in settings.
-keepclassmembers enum com.example.fucknotes.** { *; }

# The activity hands the PDF importer a ViewModel through the default factory, by reflection.
-keep class com.example.fucknotes.DocumentOpenViewModel { <init>(android.app.Application); }

# Keep stack traces readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
