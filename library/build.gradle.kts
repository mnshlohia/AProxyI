plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}


android {
    namespace = "com.aproxyi"
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

    publishing {
        singleVariant("release") { withSourcesJar() }
    }

    buildFeatures { viewBinding = true }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // `api` so consumers see the shared models without declaring them.
    api(project(":library-api"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.gson)

    // compileOnly: AProxyIInterceptor is optional. Consumers that do
    // not use OkHttp never get it pulled in, and those that do keep their own
    // version rather than having this library force one.
    compileOnly(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // The interceptor is compileOnly in the main source set; tests need it real.
    testImplementation(libs.okhttp)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = project.name

                pom {
                    name.set(project.name)
                    description.set("On-device network and analytics inspector for Android.")
                    url.set(providers.gradleProperty("POM_URL").get())
                    licenses {
                        license {
                            name.set(providers.gradleProperty("POM_LICENSE_NAME").get())
                            url.set(providers.gradleProperty("POM_LICENSE_URL").get())
                        }
                    }
                    developers {
                        developer {
                            id.set(providers.gradleProperty("POM_DEVELOPER_ID").get())
                            name.set(providers.gradleProperty("POM_DEVELOPER_NAME").get())
                        }
                    }
                    scm {
                        val repo = providers.gradleProperty("POM_URL").get()
                        url.set(repo)
                        connection.set("scm:git:$repo.git")
                        developerConnection.set("scm:git:$repo.git")
                    }
                }
            }
        }
    }
}
