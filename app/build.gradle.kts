plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun resolveConfigValue(propertyName: String, envNames: List<String>, fallback: String = ""): String {
    val gradleValue = providers.gradleProperty(propertyName).orNull?.trim().orEmpty()
    if (gradleValue.isNotEmpty()) return gradleValue

    for (envName in envNames) {
        val envValue = System.getenv(envName)?.trim().orEmpty()
        if (envValue.isNotEmpty()) return envValue
    }

    return fallback
}

android {
    namespace = "com.prumo.androidclient"
    compileSdk = providers.gradleProperty("COMPILE_SDK").get().toInt()

    defaultConfig {
        applicationId = providers.gradleProperty("APP_ID").get()
        minSdk = providers.gradleProperty("MIN_SDK").get().toInt()
        targetSdk = providers.gradleProperty("TARGET_SDK").get().toInt()
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val supabaseProjectRef = resolveConfigValue(
            propertyName = "SUPABASE_PROJECT_REF",
            envNames = listOf("SUPABASE_PROJECT_REF", "VITE_SUPABASE_PROJECT_REF"),
            fallback = "awkvzbpnihtgceqdwisc"
        )
        val supabaseUrl = resolveConfigValue(
            propertyName = "SUPABASE_URL",
            envNames = listOf("SUPABASE_URL", "VITE_SUPABASE_URL"),
            fallback = "https://${supabaseProjectRef}.supabase.co"
        )
        val supabaseAnonKey = resolveConfigValue(
            propertyName = "SUPABASE_ANON_KEY",
            envNames = listOf(
                "SUPABASE_ANON_KEY",
                "VITE_SUPABASE_PUBLISHABLE_KEY",
                "VITE_SUPABASE_ANON_KEY",
                "SUPABASE_PUBLISHABLE_KEY"
            )
        )

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    val keystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
    val keyAlias = System.getenv("KEY_ALIAS")
    val keyStorePassword = System.getenv("KEYSTORE_PASSWORD")
    val keyPassword = System.getenv("KEY_PASSWORD")
    val hasSigningSecrets = !keystoreFile.isNullOrBlank() &&
        !keyAlias.isNullOrBlank() &&
        !keyStorePassword.isNullOrBlank() &&
        !keyPassword.isNullOrBlank()

    if (hasSigningSecrets) {
        signingConfigs {
            create("ciRelease") {
                storeFile = file(keystoreFile!!)
                storePassword = keyStorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningSecrets) {
                signingConfig = signingConfigs.getByName("ciRelease")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":feature-auth"))
    implementation(project(":feature-obras"))
    implementation(project(":feature-pedidos"))
    implementation(project(":feature-estoque"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
