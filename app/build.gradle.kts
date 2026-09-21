import java.net.URI

plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val valhallaBaseUrl = providers.gradleProperty("busnavValhallaBaseUrl")
val basemapStyleUrl = providers.gradleProperty("busnavBasemapStyleUrl")

android {
    namespace = "net.nobu0707.busnav"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.nobu0707.busnav"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val debugUrl = valhallaBaseUrl.orElse("http://10.0.2.2:8002").get()
            val debugBasemapUrl = basemapStyleUrl
                .orElse("http://10.0.2.2:8080/styles/busnav-kanto/style.json")
                .get()
            require(debugBasemapUrl.isEmpty() || listOf("busnav", "busnav-kanto", "busnav-chubu").any {
                debugBasemapUrl.endsWith("/styles/$it/style.json")
            }) {
                "Debug basemap URL must use /styles/busnav[-kanto|-chubu]/style.json; edit the server base URL in Developer Connections"
            }
            buildConfigField(
                "String",
                "BASEMAP_STYLE_URL",
                "\"" + debugBasemapUrl.replace("\"", "\\\"") + "\"",
            )
            buildConfigField("String", "VALHALLA_BASE_URL", "\"${debugUrl.replace("\"", "\\\"")}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "BASEMAP_STYLE_URL", "\"\"")
            val releaseUrl = providers.gradleProperty("busnavReleaseValhallaBaseUrl").orElse("").get()
            require(releaseUrl.isEmpty() || (releaseUrl.startsWith("https://") &&
                URI(releaseUrl).host?.let { it != "localhost" && it.contains('.') &&
                    !it.all { c -> c.isDigit() || c == '.' } } == true &&
                URI(releaseUrl).rawUserInfo == null &&
                URI(releaseUrl).rawQuery == null && URI(releaseUrl).rawFragment == null)) {
                "Release routing requires a public HTTPS hostname without credentials/query/fragment"
            }
            buildConfigField("String", "VALHALLA_BASE_URL", "\"${releaseUrl.replace("\"", "\\\"")}\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.maplibre.android)

    testImplementation(libs.junit)
    testImplementation("androidx.datastore:datastore-core-okio:1.2.1")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
