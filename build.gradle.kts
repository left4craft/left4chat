plugins {
    java
    alias(libs.plugins.shadow)
}

group = "me.sisko"
version = "2.0.1"
description = "Cross-server chat, private messages, AFK and Discord sync for Left4Craft"

java {
    // Paper 26.2's API class files are major version 69 (Java 25), so the
    // compiler has to be at least 25 to read them.
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

dependencies {
    // Provided by the server. Also puts Adventure, Gson and Guava on the
    // compile classpath, none of which get shaded.
    compileOnly(libs.paper.api)

    // Permissions, primary groups and chat prefixes. Replaces Vault, which was
    // only ever a thin wrapper over this.
    compileOnly(libs.luckperms.api)

    // Nicknames. Built from the sibling left4craft/Nicky repository; refresh
    // with `./gradlew -p ../Nicky build && cp ../Nicky/build/libs/Nicky-*.jar libs/`.
    compileOnly(files("libs/Nicky-2.0.0.jar"))

    implementation(libs.jedis)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveClassifier = ""

    // Several plugins on this network ship Redis clients, connection pools and
    // Postgres drivers of their own. Keeping ours under our package stops them
    // fighting over the classpath.
    listOf(
        "redis.clients",
        "org.apache.commons.pool2",
        "org.json",
        "io.github.resilience4j",
        "io.vavr",
        "com.zaxxer.hikari",
        "org.postgresql",
    ).forEach { relocate(it, "me.sisko.left4chat.lib.$it") }

    dependencies {
        // All provided by Paper.
        exclude(dependency("org.slf4j:.*"))
        exclude(dependency("com.google.code.gson:.*"))
        // Compile-time-only annotation stubs pulled in transitively.
        exclude(dependency("com.google.errorprone:.*"))
        exclude(dependency("org.checkerframework:.*"))
    }

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/maven/**")
    exclude("com/google/errorprone/**")

    // The service-file transformer has to see every copy of
    // META-INF/services/java.sql.Driver before it can merge them.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    // Only the shaded jar is installable; keep the thin one out of the way.
    archiveClassifier = "thin"
}
