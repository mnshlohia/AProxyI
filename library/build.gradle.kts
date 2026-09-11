plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.networkinspector"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { viewBinding = true }
}

dependencies {
    // `api` so consumers see the shared models without declaring them.
    api(project(":library-api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.gson)

    // compileOnly: NetworkInspectorInterceptor is optional. Consumers that do
    // not use OkHttp never get it pulled in, and those that do keep their own
    // version rather than having this library force one.
    compileOnly(libs.okhttp)
}
