plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.roborazzi)
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
val releaseStorePassword = System.getenv("STORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val requireProductionSigning = providers.gradleProperty("requireProductionSigning")
  .orNull
  ?.equals("true", ignoreCase = true) == true

val missingReleaseSigningInputs = buildList {
  if (releaseKeystorePath.isNullOrBlank()) add("KEYSTORE_PATH")
  if (releaseStorePassword.isNullOrBlank()) add("STORE_PASSWORD")
  if (releaseKeyAlias.isNullOrBlank()) add("KEY_ALIAS")
  if (releaseKeyPassword.isNullOrBlank()) add("KEY_PASSWORD")
}

val hasReleaseSigning = missingReleaseSigningInputs.isEmpty() &&
  releaseKeystorePath?.let { file(it).isFile } == true

if (requireProductionSigning && !hasReleaseSigning) {
  val missing = if (missingReleaseSigningInputs.isEmpty()) {
    "KEYSTORE_PATH (file does not exist)"
  } else {
    missingReleaseSigningInputs.joinToString(", ")
  }
  throw GradleException(
    "Production signing was explicitly required but the controlled signing configuration is incomplete: $missing"
  )
}

val instrumentedBuildType = providers.gradleProperty("instrumentedBuildType").orElse("debug").get()

android {
  namespace = "com.example"
  compileSdk = 35
  testBuildType = instrumentedBuildType

  defaultConfig {
    // Mantido por compatibilidade de upgrade/save nesta fase. A identidade visível do produto é Pro Football.
    applicationId = "com.aistudio.brasfutretro.djuxzt"
    minSdk = 24
    targetSdk = 35
    versionCode = 32
    versionName = "3.0.1"
    multiDexEnabled = true

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(requireNotNull(releaseKeystorePath))
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
    create("debugConfig") {
      val rootKeystore = file("${rootDir}/debug.keystore")
      val userKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
      storeFile = when {
        rootKeystore.exists() -> rootKeystore
        userKeystore.exists() -> userKeystore
        else -> rootKeystore
      }
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // Keep resource shrinking explicitly disabled for 3.0.1. R8 remains enabled; introducing
      // resource removal after the final functional certification would create a new release variable.
      isShrinkResources = false
      // Release is intentionally minified so every normal release certification executes R8 and
      // validates the checked-in shrinker rules instead of merely selecting them by path.
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "src/main/proguard-rules.pro")
      // The release AndroidTest APK is minified as well. Its own narrow rules cover compile-time
      // annotation metadata that is not part of the Android runtime while keeping R8 fail-closed.
      testProguardFiles("src/androidTest/proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    animationsDisabled = true
    unitTests { isIncludeAndroidResources = true }
  }
  sourceSets {
    getByName("androidTest").assets.srcDir("$projectDir/schemas")
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.svg)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation("com.google.code.gson:gson:2.10.1")
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.runner)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)
  testImplementation(libs.turbine)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.core)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.room.testing)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  ksp(libs.androidx.room.compiler)
  ksp(libs.hilt.compiler)
}

// CI can request the normal regression suite without the intentionally long 20/100-season stress tests.
// The default local task remains unchanged and still executes the complete suite.
if (providers.gradleProperty("excludeStressTests").isPresent) {
  tasks.withType<Test>().configureEach {
    exclude("**/*StressTest*")

    // The top-level com.example Phase 10.6 save/recovery tests have a mandatory dedicated
    // certification invocation. Keep only those direct-package classes out of the unfiltered core
    // pass; nested Phase106 tests (for example ui.viewmodel publication ordering) remain covered
    // by Core Regression instead of being dropped by an over-broad recursive pattern.
    doFirst {
      if (filter.includePatterns.isEmpty()) {
        exclude("com/example/Phase106*")
      }
    }
  }
}
