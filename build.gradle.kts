plugins {
    kotlin("multiplatform") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "com.jeffpdavidson"
version = "1.3.19"

repositories {
    mavenCentral()
    // TODO: Remove ahead of public release.
    // maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
}

kotlin {
    js(IR) {
        browser {
            webpackTask(Action {
                // The default devtool uses eval(), which is forbidden in extensions. And we inline the map so we don't
                // need to configure the extension to load additional resources for the map.
                devtool = org.jetbrains.kotlin.gradle.targets.js.webpack.WebpackDevtool.INLINE_CHEAP_MODULE_SOURCE_MAP
            })
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.10.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-js:1.6.2")
                implementation("com.github.ajalt.colormath:colormath-js:3.3.3")

                implementation("com.jeffpdavidson.kotwords:kotwords-js:1.4.3")

                // TODO: Migrate to kotlinx-datetime if parsing/formatting support is added.
                implementation("com.soywiz.korlibs.klock:klock-js:4.0.10")

                runtimeOnly(npm("webextension-polyfill", "0.10.0"))
                runtimeOnly(npm("bootstrap", "5.3.2"))
            }
        }
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

    fun String.capitalizeAscii() = replaceFirstChar(Char::titlecase)

    val tasks = variants.associateWith { (env, browser) ->
        val variantName = "$env${browser.capitalizeAscii()}"
        val extensionFolder = "build/extension/$variantName"

        // TODO: This seems more complex than it should be.
        // Depending on the WebPack tasks fails in production because it thinks the output belongs to the
        // distribution task, but the distribution task is named inconsistently and not strongly typed.
        val distributionTaskName = when (env) {
            development -> "DevelopmentExecutableDistribution"
            production -> "Distribution"
            else -> throw IllegalArgumentException("Unknown environment $env")
        }
        val browserDistributionTask = getByName("jsBrowser$distributionTaskName")

        val copyBundleFile = register<Copy>("copy${variantName.capitalizeAscii()}BundleFile") {
            dependsOn(browserDistributionTask)
            from(browserDistributionTask.outputs.files.singleFile) {
                include("*.js")
            }
            into("$extensionFolder/js")
        }

        val copyResources = register<Copy>("copy${variantName.capitalizeAscii()}Resources") {
            from("src/jsMain/resources")
            exclude("manifest.json")
            exclude("browser-js/")
            into(extensionFolder)
        }

        val copyManifest = register<Copy>("copy${variantName.capitalizeAscii()}Manifest") {
            from("src/jsMain/resources/manifest.json")
            into(extensionFolder)
            // Replace placeholders in manifest.json based on the browser.
            when (browser) {
                firefox -> filter { line ->
                    line.replace("{MANIFEST_VERSION}", "2")
                        .replace("{ACTION_KEY}", "browser_action")
                        .replace("{PERMISSIONS}", "")
                        .replace("{OPTIONAL_HOST_PERMISSION_KEY}", "optional_permissions")
                        .replace(
                            "{BROWSER_SPECIFIC_SETTINGS}",
                            """"browser_specific_settings": {
                                "gecko": {
                                  "id": "{d48182db-7419-4305-8f09-e886fbd4d74d}"
                                },
				"gecko_android": {
                                  "id": "{d48182db-7419-4305-8f09-e886fbd4d74d}",
                                  "strict_min_version": "130.0"
                                }
                            }
                            """.trimIndent()
                        )
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

        val copyJsDeps = register<Copy>("copy${variantName.capitalizeAscii()}JsDeps") {
            from("build/js/node_modules/bootstrap/dist") {
                include("css/bootstrap.min.css")
                include("js/bootstrap.bundle.min.js")
            }
            from("build/js/node_modules/webextension-polyfill/dist") {
                include("browser-polyfill.min.js")
                into("js")
            }
            from("src/jsMain/resources/browser-js") {
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
