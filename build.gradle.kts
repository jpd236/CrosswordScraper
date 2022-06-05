import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    kotlin("js") version "1.6.20"
    kotlin("plugin.serialization") version "1.6.20"
}

group = "com.jeffpdavidson"
version = "1.2.12-SNAPSHOT"

repositories {
    mavenCentral()
    // TODO: Remove ahead of public release.
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("com.github.ajalt.colormath:colormath:3.2.0")

    implementation("com.jeffpdavidson.kotwords:kotwords-js:1.2.10-SNAPSHOT")

    runtimeOnly(npm("webextension-polyfill", "0.8.0"))
    runtimeOnly(npm("jquery", "3.6.0"))
    runtimeOnly(npm("bootstrap", "4.6.0"))

    testImplementation(kotlin("test-js"))
}

kotlin {
    js(IR) {
        browser {
            webpackTask {
                // The default devtool uses eval(), which is forbidden in extensions. And we inline the map so we don't
                // need to configure the extension to load additional resources for the map.
                devtool = org.jetbrains.kotlin.gradle.targets.js.webpack.WebpackDevtool.INLINE_CHEAP_MODULE_SOURCE_MAP
            }
        }
        binaries.executable()
    }
}

// Define developmentVnExtension and productionVnExtension tasks which build in development/production mode.
// n may be "2" for Manifest v2 or "3" for Manifest v3.
tasks {
    val development = "development"
    val production = "production"
    val environments = listOf(development, production)

    val v2 = "v2"
    val v3 = "v3"
    val manifestVersions = listOf(v2, v3)

    val variants = environments.flatMap { env ->
        manifestVersions.map { manifestVersion ->
            env to manifestVersion
        }
    }

    val tasks = variants.associateWith { (env, manifestVersion) ->
        val variantName = "$env${manifestVersion.capitalize()}"
        val extensionFolder = "build/extension/$variantName"

        val browserWebpackTask = getByName("browser${env.capitalize()}Webpack", KotlinWebpack::class)

        val copyBundleFile = register<Copy>("copy${variantName.capitalize()}BundleFile") {
            dependsOn(browserWebpackTask)
            from(browserWebpackTask.destinationDirectory) {
                include("*.js")
            }
            into("$extensionFolder/js")
        }

        val copyResources = register<Copy>("copy${variantName.capitalize()}Resources") {
            from("src/main/resources")
            exclude("manifest.json")
            into(extensionFolder)
        }

        val copyManifest = register<Copy>("copy${variantName.capitalize()}Manifest") {
            from("src/main/resources/manifest.json")
            into(extensionFolder)
            // Replace placeholders in manifest.json based on the manifest version.
            when (manifestVersion) {
                v2 -> filter { line ->
                    line.replace("{MANIFEST_VERSION}", "2")
                        .replace("{ACTION_KEY}", "browser_action")
                        .replace("{PERMISSIONS}", "")
                        .replace("{OPTIONAL_HOST_PERMISSION_KEY}", "optional_permissions")
                        .replace("{MINIMUM_CHROME_VERSION}", "42")
                        .replace("{WEB_ACCESSIBLE_RESOURCES}", """
                            "fonts/*.ttf"
                        """.trimIndent())
                }
                v3 -> filter { line ->
                    line.replace("{MANIFEST_VERSION}", "3")
                        .replace("{ACTION_KEY}", "action")
                        .replace("{PERMISSIONS}", ",\"scripting\"")
                        .replace("{OPTIONAL_HOST_PERMISSION_KEY}", "optional_host_permissions")
                        .replace("{MINIMUM_CHROME_VERSION}", "102")
                        // TODO(#4): Restrict font access to just this extension.
                        .replace("{WEB_ACCESSIBLE_RESOURCES}", """
                            {
                              "resources": ["fonts/*.ttf"],
                              "matches": ["<all_urls>"]
                            }
                        """.trimIndent())
                }
            }
        }

        val copyJsDeps = register<Copy>("copy${variantName.capitalize()}JsDeps") {
            from("build/js/node_modules/jquery/dist") {
                include("jquery.slim.min.js")
                into("js")
            }
            from("build/js/node_modules/bootstrap/dist") {
                include("css/bootstrap.min.css")
                include("js/bootstrap.bundle.min.js")
            }
            from("build/js/node_modules/webextension-polyfill/dist") {
                include("browser-polyfill.min.js")
                into("js")
            }
            into(extensionFolder)
        }

        register<Zip>("${variantName}Extension") {
            dependsOn(copyBundleFile, copyJsDeps, copyResources, copyManifest)
            from(extensionFolder)
            archiveAppendix.set(variantName)
        }
    }

    assemble {
        dependsOn(tasks[production to v2])
    }
}
