#!/bin/sh
# Gradle runner for this project. The harness shell is WSL, so Gradle is driven through cmd.exe
# with JDK 25 pinned; JAVA_HOME is required by the NeoForge moddev plugin.
MSYS_NO_PATHCONV=1 cmd.exe /c "cd /d C:\Users\pppop\Desktop\azuki\Project\FX-EASY && set \"JAVA_HOME=C:\Users\pppop\scoop\apps\openjdk25\current\" && gradlew.bat $*"
