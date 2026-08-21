plugins {
    java
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // WorldGuard / WorldEdit
    maven("https://maven.enginehub.org/repo/")
    // LuckPerms
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    // CoreProtect
    maven("https://maven.playpro.com/")
    // PacketEvents
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    // EssentialsX
    maven("https://repo.essentialsx.net/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // WorldGuard + WorldEdit (resolved from enginehub maven)
    // Pinned to the last releases whose Gradle metadata declares
    // org.gradle.jvm.version=21. WorldGuard 7.0.18 / WorldEdit 7.4.3+
    // are compiled with JDK 25 and advertise jvm.version=25, which Gradle
    // rejects for a Java 21 toolchain. These versions still support
    // MC 1.21.5-1.21.8 (Paper recommended) and Java 21.
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.17")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.2")

    // LuckPerms (compileOnly — resolves from sonatype)
    compileOnly("net.luckperms:api:5.4")

    // CoreProtect (compileOnly — resolves from playpro maven)
    compileOnly("net.coreprotect:coreprotect:24.0")

    // EssentialsX (compileOnly — resolves from essentialsx maven)
    compileOnly("net.essentialsx:EssentialsX:2.22.0")

    // PacketEvents (compileOnly)
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // paper-api needed on test compile and runtime classpath for types referenced
    // through ProtectionConfig record signature (Material fields); nulls are passed
    // in tests so no Bukkit server bootstrap is triggered.
    testCompileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    testRuntimeOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
