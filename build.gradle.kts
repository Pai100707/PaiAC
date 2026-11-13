plugins {
    java
}

group = "com.paimc"
version = "1.0.0-SNAPSHOT-001"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Compile against the OLDEST supported API (1.21.1) so we never reference newer symbols by accident.
    // The server provides the API at runtime, nothing is shaded into the JAR.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // SQLite JDBC (org.sqlite.JDBC) is bundled with Paper/Spigot servers, it is loaded reflectively
    // by StorageService, so it is intentionally NOT a compile/runtime dependency here.

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    jar {
        archiveBaseName.set("PaiAC")
        // Output: build/libs/PaiAC-<version>.jar
    }
    test {
        useJUnitPlatform()
    }
}
