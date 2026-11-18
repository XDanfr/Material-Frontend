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
            // OpenGL contexts for hardware-rendered libretro cores (CGL on macOS, GLFW elsewhere).
            implementation(libs.lwjgl.opengl)
            implementation(libs.lwjgl.glfw)
            lwjglNatives.forEach { n ->
                listOf("lwjgl", "lwjgl-sdl", "lwjgl-opengl", "lwjgl-glfw").forEach { m -> runtimeOnly("org.lwjgl:$m:${libs.versions.lwjgl.get()}:$n") }
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
        mainClass = "cc.xdan.frontend.MainKt"
        // Bundle the same JDK the code is compiled for (not whatever JVM runs Gradle).
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmToolchain.get().toInt()))
        }.get().metadata.installationPath.asFile.absolutePath
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Xss4m")

        nativeDistributions {
            // Only this OS's formats: listing AppImage on macOS makes packageDmg collide with packageAppImage's output.
            val hostOs = System.getProperty("os.name").lowercase()
            when {
                hostOs.contains("mac") -> targetFormats(TargetFormat.Dmg)
                hostOs.contains("win") -> targetFormats(TargetFormat.Msi, TargetFormat.Exe)
                else -> targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            }
            packageName = "Material Frontend"
            packageVersion = "1.0.0" // jpackage needs major >= 1; the app itself reports 0.1 (WIP)
            description = "Material 3 Expressive emulation frontend"
            vendor = "XDan"
            copyright = "© 2026 XDan"
            modules("java.instrument", "java.management", "java.naming", "java.net.http", "jdk.unsupported", "jdk.crypto.ec", "jdk.zipfs")
            macOS {
                bundleID = "cc.xdan.frontend"
                // Fallback .icns; the Liquid Glass icon (Assets.car) is added after jpackage below.
                iconFile.set(rootProject.file("packaging/macos/AppIcon.icns"))
                infoPlist { extraKeysRawXml = "<key>CFBundleIconName</key><string>AppIcon</string>" }
            }
            windows {
                menuGroup = "Material Frontend"; perUserInstall = true; upgradeUuid = "4c7d0b8e-7f0b-4a43-9a5e-2f7b0f3b6a11"
                iconFile.set(rootProject.file("packaging/windows/icon.ico"))
            }
            linux {
                packageName = "material-frontend"
                iconFile.set(rootProject.file("packaging/linux/icon.png"))
            }
        }
    }
}

// macOS: add the Icon Composer output (Assets.car → Liquid Glass icon on macOS 26) to the bundle,
// then ad-hoc sign so Apple Silicon accepts the modified bundle. The .dmg is built from this app image.
tasks.matching { it.name == "createDistributable" }.configureEach {
    // Plain Files captured at configuration time (configuration-cache safe).
    val assetsCar: File = rootProject.file("packaging/macos/Assets.car")
    val appDir: File = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
    doLast {
        if (!System.getProperty("os.name").lowercase().contains("mac")) return@doLast
        val app = appDir.listFiles()?.firstOrNull { it.name.endsWith(".app") } ?: return@doLast
        assetsCar.copyTo(app.resolve("Contents/Resources/Assets.car"), overwrite = true)
        val p = ProcessBuilder("codesign", "--force", "--deep", "--sign", "-", app.absolutePath).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "codesign failed: $out" }
    }
}

// Tests construct several AppControllers against one scratch data folder: no start-up scans of the real machine.
tasks.withType<Test>().configureEach { systemProperty("mf.noStartupScan", "true") }
