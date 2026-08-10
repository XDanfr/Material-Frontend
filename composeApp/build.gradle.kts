import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// SDL3 natives for the OS/arch being built (CI builds each platform on its own runner).
// Pass -PallNatives to bundle every platform (e.g. for a portable jar).
val lwjglNatives: List<String> = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").let { it.contains("aarch64") || it.contains("arm64") }
    val all = listOf("natives-windows", "natives-windows-arm64", "natives-linux", "natives-linux-arm64", "natives-macos", "natives-macos-arm64")
    if (project.hasProperty("allNatives")) all else listOf(
        when {
            os.contains("win") -> if (arm) "natives-windows-arm64" else "natives-windows"
            os.contains("mac") -> if (arm) "natives-macos-arm64" else "natives-macos"
            else -> if (arm) "natives-linux-arm64" else "natives-linux"
        }
    )
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.material3)
            implementation(libs.compose.icons)
            implementation(libs.coroutines.swing)
            implementation(libs.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.materialKolor)
            implementation(libs.lwjgl.core)
            implementation(libs.lwjgl.sdl)
            lwjglNatives.forEach { n ->
                runtimeOnly("org.lwjgl:lwjgl:${libs.versions.lwjgl.get()}:$n")
                runtimeOnly("org.lwjgl:lwjgl-sdl:${libs.versions.lwjgl.get()}:$n")
            }
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.materialfrontend.MainKt"
        // Bundle the same JDK the code is compiled for (not whatever JVM runs Gradle).
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmToolchain.get().toInt()))
        }.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Xss4m")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "Material Frontend"
            packageVersion = "1.0.0" // jpackage needs major >= 1; the app itself reports 0.1 (WIP)
            description = "Material 3 Expressive emulation frontend"
            vendor = "Material Frontend"
            modules("java.instrument", "java.management", "java.naming", "java.net.http", "jdk.unsupported", "jdk.crypto.ec", "jdk.zipfs")
            macOS { bundleID = "app.materialfrontend" }
            windows { menuGroup = "Material Frontend"; perUserInstall = true; upgradeUuid = "4c7d0b8e-7f0b-4a43-9a5e-2f7b0f3b6a11" }
            linux { packageName = "material-frontend" }
        }
    }
}

