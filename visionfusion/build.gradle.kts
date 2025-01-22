// build.gradle.kts (Raíz)

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
