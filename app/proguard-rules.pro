# The Room rule bundled with the transitive room-runtime (via WorkManager, pulled in by Glance)
# keeps RoomDatabase subclasses but not their constructors, so Room's reflective
# instantiation of WorkDatabase_Impl fails at startup in release builds.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Glance instantiates widgets and action callbacks reflectively by class name; without these
# R8 strips/renames their constructors and the home widget never renders or responds.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# WorkManager (which Glance runs on) instantiates these reflectively via Class.newInstance();
# the bundled rules don't keep their constructors, so the widget session never starts.
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class * extends androidx.work.WorkerFactory { <init>(); }
