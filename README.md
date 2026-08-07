# Material Frontend (WIP)

A Material 3 Expressive emulation frontend for Windows, macOS and Linux, built with Compose Multiplatform.

- Library view, a controller-first Big Screen mode and an XMB mode.
- Libretro cores run in-app through the Java FFM API; standalone emulators are detected and launched.
- Steam import, ports/recomps, artwork scraping, SDL3 controllers.

## Build and run

```bash
./gradlew :composeApp:run
./gradlew :composeApp:jvmTest
./gradlew :composeApp:packageDistributionForCurrentOS
```

The build downloads the JDK it needs (Gradle toolchains).
