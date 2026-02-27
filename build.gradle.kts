plugins {
    id("fabric-loom") version "1.9-SNAPSHOT"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

loom {
    splitEnvironmentSourceSets()
    mods {
        create("pokermc") {
            sourceSet("main")
            sourceSet("client")
        }
    }

    // Second client for 2-player local testing
    // Usage: run both  gradlew runClient  AND  gradlew runClient2  at the same time,
    //        then in the first client create a world and do "Open to LAN",
    //        the second client will connect automatically.
    runs {
        create("client2") {
            client()
            runDir("run2")
            programArgs("--username", "TestPlayer2")
            ideConfigGenerated(true)
        }
    }
}

repositories {
    // Extra repos if needed
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
}

fabricApi {
    configureDataGeneration()
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

val targetJavaVersion = 21

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    withSourcesJar()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
