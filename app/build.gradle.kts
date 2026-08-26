import java.util.Properties

// fixed: imports must precede the plugins block in Kotlin DSL scripts.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseProperties = Properties()
val releasePropertiesFile = rootProject.file("keystore.properties")
if (releasePropertiesFile.exists()) {
    releasePropertiesFile.inputStream().use(releaseProperties::load)
}

android {
    namespace = "ir.mtlink.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.mtlink.client"
        minSdk = 24
        targetSdk = 35
        versionCode = 25
        versionName = "1.3.13"
    }

    signingConfigs {
        if (releasePropertiesFile.exists()) {
            create("release") {
                storeFile = file(requireNotNull(releaseProperties.getProperty("storeFile")))
                storePassword = requireNotNull(releaseProperties.getProperty("storePassword"))
                keyAlias = requireNotNull(releaseProperties.getProperty("keyAlias"))
                keyPassword = requireNotNull(releaseProperties.getProperty("keyPassword"))
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
        create("installCheck") {
            initWith(getByName("release"))
            applicationIdSuffix = ".installcheck"
            versionNameSuffix = "-installcheck"
            signingConfig = signingConfigs.findByName("release")
            resValue("string", "app_name", "MTLink Install Check")
        }
        create("clean") {
            initWith(getByName("release"))
            applicationIdSuffix = ".clean"
            versionNameSuffix = "-clean"
            signingConfig = signingConfigs.findByName("release")
            resValue("string", "app_name", "MTLink")
        }
        create("safe") {
            initWith(getByName("release"))
            applicationIdSuffix = ".safe"
            versionNameSuffix = "-safe"
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release")
            resValue("string", "app_name", "MTLink")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0") {
        exclude(group = "androidx.profileinstaller", module = "profileinstaller")
    }
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}
