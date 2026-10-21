plugins {
    alias(libs.plugins.android.application)
}

// Mini-application sans dépendance : trois icônes qui ouvrent l'appareil photo,
// la calculatrice et la galerie du système, dont le lanceur Sunmi masque les icônes.
android {
    namespace = "fr.pvlycee.raccourcis"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.pvlycee.raccourcis"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
