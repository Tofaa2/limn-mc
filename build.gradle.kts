plugins {
    id("net.fabricmc.fabric-loom")
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

base {
    archivesName = providers.gradleProperty("archives_base_name")
}

loom {
    accessWidenerPath = file("src/main/resources/limn-mc.classtweaker")
    runs {
        named("client") {
            vmArg("--enable-native-access=ALL-UNNAMED")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.jar {
    from("LICENSE") { rename { "${it}_${base.archivesName.get()}" } }
}

fun zigBuild(name: String, target: String?, directory: String) = tasks.register<Exec>(name) {
    val install = layout.buildDirectory.dir(directory)
    workingDir = file("native")
    commandLine(
        listOf("zig", "build", "-Doptimize=ReleaseFast", "--prefix", install.get().asFile.path) +
            (target?.let { listOf("-Dtarget=$it") } ?: emptyList())
    )
    inputs.files("native/build.zig", "native/build.zig.zon")
    inputs.dir("native/src")
    inputs.property("dlssSdk", file("native/dlss_sdk/include/nvsdk_ngx.h").exists())
    outputs.dir(install)
}

val zigBuildLinux = zigBuild("zigBuildLinux", "x86_64-linux-gnu", "native/linux-x86_64")
val zigBuildWindows = zigBuild("zigBuildWindows", "x86_64-windows-gnu", "native/windows-x86_64")

tasks.processResources {
    from(zigBuildLinux.map { layout.buildDirectory.dir("native/linux-x86_64/lib") }) {
        include("*.so")
        into("natives/linux-x86_64")
    }
    from(zigBuildWindows.map { layout.buildDirectory.dir("native/windows-x86_64/bin") }) {
        include("*.dll")
        into("natives/windows-x86_64")
    }
}

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "limn-mc-test"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}
