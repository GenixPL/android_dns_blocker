# The Room rule bundled with the transitive room-runtime (via WorkManager, pulled in by Glance)
# keeps RoomDatabase subclasses but not their constructors, so Room's reflective
# instantiation of WorkDatabase_Impl fails at startup in release builds.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
