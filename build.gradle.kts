plugins {
    `java-library`
    alias(libs.plugins.run.paper)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

// MiniGami's plugin jar, only to run it on the test server.
val miniGamiPlugin by configurations.creating {
    isTransitive = false
}

dependencies {
    // MiniGami ships api and core and shares them through join-classpath, so nothing of ours is bundled.
    compileOnly(libs.minigami.api)
    // Optional at runtime: placeholders only when PlaceholderAPI is installed.
    compileOnly(libs.placeholderapi) { isTransitive = false }

    testImplementation(libs.minigami.api)
    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.sqlite.jdbc)

    miniGamiPlugin(libs.minigami.plugin)
}

base {
    archivesName = "KingOfTheHill"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    useJUnitPlatform()
    // The SQLite driver loads its native library.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("paper-plugin.yml") { expand("version" to version) }
}

tasks.runServer {
    minecraftVersion("26.3")
    pluginJars(miniGamiPlugin)
    downloadPlugins { modrinth("placeholderapi", libs.versions.placeholderapi.get()) }
}
