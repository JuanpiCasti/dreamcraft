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
    // WorldGuard / WorldEdit  (fallback to enginehub when online)
    maven("https://maven.enginehub.org/repo/")
    // LuckPerms
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    // CoreProtect
    maven("https://maven.playpro.com/")
    // PacketEvents
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // WorldGuard + WorldEdit — use the JARs already present in data/plugins
    // (enginehub maven is unreachable in offline/CI; JARs are the ground truth)
    compileOnly(files("data/plugins/worldguard-bukkit-7.0.18.jar"))
    compileOnly(files("data/plugins/worldedit-bukkit-7.4.5.jar"))

    // LuckPerms (compileOnly — resolves from sonatype)
    compileOnly("net.luckperms:api:5.4")

    // CoreProtect (compileOnly — resolves from playpro maven)
    compileOnly("net.coreprotect:coreprotect:24.0")

    // EssentialsX — use JAR from data/plugins
    compileOnly(files("data/plugins/EssentialsX-2.22.0.jar"))

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
