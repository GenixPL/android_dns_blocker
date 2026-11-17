import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from env vars (CI) or local.properties. Debug builds always
// use Gradle's default debug key, so the release keystore is not needed locally.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env) ?: localProps.getProperty(prop)

android {
    namespace = "com.themis.dns_blocker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.themis.dns_blocker"
        minSdk = 31
        targetSdk = 37
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePath = signingValue("KEYSTORE_PATH", "keystore.path")
    if (keystorePath != null) {
        signingConfigs {
            create("shared") {
                storeFile = file(keystorePath)
                storePassword = signingValue("KEYSTORE_PASSWORD", "keystore.password")
                keyAlias = signingValue("KEY_ALIAS", "key.alias")
                keyPassword = signingValue("KEY_PASSWORD", "key.password")
            }
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("shared")?.let { signingConfig = it }
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
