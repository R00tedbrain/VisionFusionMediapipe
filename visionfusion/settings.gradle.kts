pluginManagement {
    repositories {
        // Aquí es donde Gradle buscará los plugins
        // (com.android.application, org.jetbrains.kotlin.android, etc.)
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    // (Opcional) Declarar versiones de plugins aquí
    plugins {
        // Por ejemplo:
        // id("com.android.application") version "7.4.2"
        // id("org.jetbrains.kotlin.android") version "1.8.10"
        // etc.
    }
}

dependencyResolutionManagement {
    // Para que Gradle use SOLO estos repos y falle si un subproyecto
    // intenta declarar repositorios propios
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Nombre raíz del proyecto
rootProject.name = "visionfusion"

// Incluye módulos
include(":app")
