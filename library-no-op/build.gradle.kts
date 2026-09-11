plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.networkinspector.noop"
    compileSdk = 35

    defaultConfig { minSdk = 21 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":library-api"))

    // Only to satisfy the Interceptor signature. Nothing is ever captured.
    compileOnly(libs.okhttp)
}
