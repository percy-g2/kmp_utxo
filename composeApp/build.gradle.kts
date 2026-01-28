
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)

            implementation(libs.kstore.file)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.startup.runtime)

            implementation(libs.ktor.client.android)
            implementation(libs.ktor.client.cio)

            implementation(libs.kstore.file)
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
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.ktor.client.cio)
            implementation(libs.kstore.file)
            implementation(libs.harawata.appdirs)
            implementation(libs.kotlinx.coroutines.swing)
        }

        wasmJsMain.dependencies {
            implementation(libs.kstore.storage)

            implementation(libs.ktor.client.js)
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

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