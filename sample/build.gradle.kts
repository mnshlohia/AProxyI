plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.networkinspector.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.networkinspector.sample"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // The whole point: the real inspector in debug, hollow stubs in release.
    debugImplementation(project(":library"))
    releaseImplementation(project(":library-no-op"))

    implementation(libs.androidx.appcompat)
    implementation(libs.okhttp)
}
