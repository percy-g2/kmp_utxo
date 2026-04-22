
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val generateAppVersion by tasks.registering {
    val versionFile = rootProject.file("version.properties")
    val outputDir = layout.buildDirectory.dir("generated/source/version/commonMain/kotlin")
    inputs.file(versionFile)
    outputs.dir(outputDir)
    doLast {
        val props = Properties().apply { versionFile.inputStream().use { load(it) } }
        val name = props.getProperty("versionName")
        val code = props.getProperty("versionCode").toInt()
        val out = outputDir.get().file("buildinfo/Version.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            """
            // Auto-generated from version.properties. Do not edit.
            package buildinfo

            internal const val APP_VERSION_NAME: String = "$name"
            internal const val APP_VERSION_CODE: Int = $code

            """.trimIndent()
        )
    }
}

val javafxPlatform: String = run {
    val os = OperatingSystem.current()
    when {
        os.isWindows -> "win"
        os.isMacOsX -> if (System.getProperty("os.arch") == "aarch64") "mac-aarch64" else "mac"
        else -> "linux"
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
                "-XXLanguage:+ExplicitBackingFields",
                "-Xexplicit-backing-fields"
            )
        )
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    androidLibrary {
        namespace = "org.androdevlinux.utxo"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        
        androidResources {
            enable = true
        }
        
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    jvm("desktop")
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        val desktopMain by getting

        val wasmJsMain by getting

        commonMain {
            kotlin.srcDir(generateAppVersion)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)

            implementation(libs.kstore.file)

            implementation(libs.compose.webview.multiplatform)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.startup.runtime)

            implementation(libs.ktor.client.android)
            implementation(libs.ktor.client.cio)

            implementation(libs.kstore.file)

            implementation(libs.compose.webview.multiplatform)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.lifecyle.runtime.compose)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.atomicfu)

            implementation(libs.kotlinx.datetime)

            implementation(libs.navigation.compose)

            api(libs.kstore)

            implementation(libs.lifecycle.viewmodel.compose)

            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.kermit)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.cio)
            implementation(libs.kstore.file)
            implementation(libs.harawata.appdirs)
            implementation(libs.kotlinx.coroutines.swing)
            val javafxVersion = libs.versions.javafx.get()
            implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
            implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
            implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")
            implementation("org.openjfx:javafx-web:$javafxVersion:$javafxPlatform")
            implementation("org.openjfx:javafx-swing:$javafxVersion:$javafxPlatform")
            implementation("org.openjfx:javafx-media:$javafxVersion:$javafxPlatform")
        }

        wasmJsMain.dependencies {
            implementation(libs.kstore.storage)

            implementation(libs.ktor.client.js)

            implementation(libs.compose.webview.multiplatform)
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        // JavaFX WebView runs on its own JavaFX Application Thread inside a Swing interop panel.
        jvmArgs += listOf(
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "UTXO"
            packageVersion = "1.0.0"
            macOS {
                iconFile.set(project.file("src/macosMain/resources/AppIcon.icns"))
            }
            windows {
                iconFile.set(project.file("src/windowsMain/resources/AppIcon.ico"))
            }
            linux {
                iconFile.set(project.file("src/linuxMain/resources/AppIcon.png"))
            }
        }

        buildTypes.release.proguard {
            version.set("7.4.0")
            obfuscate.set(true)
            configurationFiles.from(project.file("desktop/proguard-rules.pro"))
        }
    }
}