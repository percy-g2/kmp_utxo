import com.android.build.api.dsl.ApplicationExtension
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "org.androdevlinux.utxo.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.androdevlinux.utxo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 42
        versionName = "0.4.2"
    }

    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
    }


    val keystorePath = System.getenv("KEYSTORE_FILE")
        ?: keystoreProps.getProperty("storeFile", "")

    signingConfigs {
        create("release") {
            if (keystorePath.isNotEmpty()) {
                storeFile = file(keystorePath)
            }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: keystoreProps.getProperty("storePassword", "")
            keyAlias = System.getenv("KEY_ALIAS")
                ?: keystoreProps.getProperty("keyAlias", "")
            keyPassword = System.getenv("KEY_PASSWORD")
                ?: keystoreProps.getProperty("keyPassword", "")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xskip-prerelease-check")
        }
    }
    
    buildFeatures {
        compose = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../composeApp/proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.composeApp)
    
    // AndroidX dependencies required for MainActivity
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.startup.runtime)
    
    // Compose dependencies (required for MainActivity)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.lifecyle.runtime.compose)
    
    debugImplementation(libs.compose.ui.tooling)
}
