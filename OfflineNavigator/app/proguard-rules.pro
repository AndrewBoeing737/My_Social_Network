# keep GraphHopper and osmdroid (both use reflection in places)
-keep class com.graphhopper.** { *; }
-keep class org.osmdroid.** { *; }
-dontwarn org.slf4j.**
-dontwarn com.graphhopper.**
