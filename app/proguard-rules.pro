# R8 rules for the release build.
# The app uses no reflection: org.json is part of Android, and WorkManager and
# Compose ship their own consumer rules. Keep line info for readable stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
