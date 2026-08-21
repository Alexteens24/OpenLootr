plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "me.alexisbinh.openlootr"
version = "0.7.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

val paperVersion = "1.21.11-R0.1-SNAPSHOT"
val sqliteVersion = "3.53.2.1"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("org.xerial:sqlite-jdbc:$sqliteVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.110.0")
    testRuntimeOnly("org.spongepowered:configurate-yaml:4.2.0")
    testRuntimeOnly("com.mojang:datafixerupper:9.0.19")
    testRuntimeOnly("org.xerial:sqlite-jdbc:$sqliteVersion")
}

val spike = sourceSets.create("spike") {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations[spike.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())

tasks.register<Jar>("spikeJar") {
    group = "build"
    description = "Builds the development-only OpenLootr spike plugin."
    archiveBaseName.set("OpenLootrSpikes")
    archiveClassifier.set("dev")
    from(spike.output)
}

tasks.register<Copy>("ciArtifact") {
    group = "build"
    description = "Copies the single production JAR to a stable CI artifact name."
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("ci"))
    rename { "OpenLootr.jar" }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    useJUnitPlatform()
    // Keep userdev's runtime libraries, but MockBukkit must provide the server implementation.
    classpath = classpath.filter { it.name != "mappedServerJar.jar" }
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("paper-plugin.yml") {
        expand("version" to version)
    }
}

tasks.named<ProcessResources>(spike.processResourcesTaskName) {
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.runServer {
    minecraftVersion("1.21.11")
    jvmArgs("-Dcom.mojang.eula.agree=true")
}

tasks.check {
    dependsOn("spikeClasses")
}
