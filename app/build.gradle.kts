import org.gradle.api.GradleException
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.smartphone_lock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smartphone_lock"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val envProperties = Properties().apply {
            val propertiesFile = project.rootProject.file("local.properties")
            if (propertiesFile.exists()) {
                propertiesFile.inputStream().use(::load)
            } else {
                project.logger.lifecycle(
                    "local.properties is not present. Falling back to environment variables for secrets."
                )
            }
        }

        fun String.toBuildConfigStringLiteral(): String =
            replace("\\", "\\\\")
                .replace("\"", "\\\"")

        data class SecretKey(
            val buildConfigName: String,
            val propertyKey: String,
            val envVarName: String
        )

        fun SecretKey.resolve(): String {
            val propertyValue = envProperties.getProperty(propertyKey)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            val envValue = System.getenv(envVarName)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            return propertyValue
                ?: envValue
                ?: throw GradleException(
                    "Missing secret for `$propertyKey`. " +
                        "Provide it in local.properties or set the `$envVarName` environment variable."
                )
        }

        val secretKeys = listOf(
            SecretKey("SUPABASE_URL", "supabase.url", "SUPABASE_URL"),
            SecretKey("SUPABASE_ANON_KEY", "supabase.anonKey", "SUPABASE_ANON_KEY"),
            SecretKey(
                "ALARM_MANAGER_LOCK_INTENT_ACTION",
                "alarmManager.lockIntentAction",
                "ALARM_MANAGER_LOCK_INTENT_ACTION"
            ),
            SecretKey(
                "ALARM_MANAGER_UNLOCK_INTENT_ACTION",
                "alarmManager.unlockIntentAction",
                "ALARM_MANAGER_UNLOCK_INTENT_ACTION"
            )
        )

        secretKeys.forEach { secret ->
            val value = secret.resolve()
            buildConfigField("String", secret.buildConfigName, "\"${value.toBuildConfigStringLiteral()}\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}