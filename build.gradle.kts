import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    kotlin("js") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
}

group = "com.jeffpdavidson"
version = "1.3.4"

repositories {
    mavenCentral()
    // TODO: Remove ahead of public release.
    // maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("com.github.ajalt.colormath:colormath:3.2.1")

    implementation("com.jeffpdavidson.kotwords:kotwords-js:1.3.4")

    runtimeOnly(npm("webextension-polyfill", "0.10.0"))
    runtimeOnly(npm("jquery", "3.6.3"))
    runtimeOnly(npm("bootstrap", "4.6.2"))

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

// Define development{Browser}Extension and production{Browser}Extension tasks which build in development/production
// mode. {Browser} may be "Chrome" (manifest V3) or "Firefox" (manifest V2).
tasks {
    val development = "development"
    val production = "production"
    val environments = listOf(development, production)

    val chrome = "chrome"
    val firefox = "firefox"
    val browsers = listOf(chrome, firefox)

    val variants = environments.flatMap { env ->
        browsers.map { browser ->
            env to browser
        }
    }

    val tasks = variants.associateWith { (env, browser) ->
        val variantName = "$env${browser.capitalize()}"
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
            exclude("browser-js/")
            into(extensionFolder)
        }

        val copyManifest = register<Copy>("copy${variantName.capitalize()}Manifest") {
            from("src/main/resources/manifest.json")
            into(extensionFolder)
            // Replace placeholders in manifest.json based on the browser.
            when (browser) {
                firefox -> filter { line ->
                    line.replace("{MANIFEST_VERSION}", "2")
                        .replace("{ACTION_KEY}", "browser_action")
                        .replace("{PERMISSIONS}", "")
                        .replace("{OPTIONAL_HOST_PERMISSION_KEY}", "optional_permissions")
                        .replace("{BROWSER_SPECIFIC_SETTINGS}",
                            """"browser_specific_settings": {
                                "gecko": {
                                   "id": "{d48182db-7419-4305-8f09-e886fbd4d74d}"
                                }
                            }
                            """.trimIndent())
                }
                chrome -> filter { line ->
                    line.replace("{MANIFEST_VERSION}", "3")
                        .replace("{ACTION_KEY}", "action")
                        .replace("{PERMISSIONS}", ",\"scripting\"")
                        .replace("{OPTIONAL_HOST_PERMISSION_KEY}", "optional_host_permissions")
                        .replace("{BROWSER_SPECIFIC_SETTINGS}", "\"minimum_chrome_version\": \"102\"")
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
            from("src/main/resources/browser-js") {
                include("$browser.js")
                rename { "browser.js" }
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
        dependsOn(tasks[production to chrome], tasks[production to firefox])
    }
}
