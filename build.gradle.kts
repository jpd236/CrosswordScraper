plugins {
    kotlin("js") version "1.5.20"
    kotlin("plugin.serialization") version "1.5.0"
}

group = "com.jeffpdavidson"
version = "1.2.1-SNAPSHOT"

repositories {
    mavenCentral()
    // TODO: Remove ahead of public release.
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
    implementation("com.github.ajalt.colormath:colormath:2.1.0")

    implementation("com.jeffpdavidson.kotwords:kotwords-js:1.2.1-SNAPSHOT")

    runtimeOnly(npm("webextension-polyfill", "0.7.0"))
    runtimeOnly(npm("jquery", "3.5.1"))
    runtimeOnly(npm("bootstrap", "4.5.3"))

    testImplementation(kotlin("test-js"))
}

kotlin {
    js {
        browser {
            webpackTask {
                // The default devtool uses eval(), which is forbidden in extensions. And we inline the map so we don't
                // need to configure the extension to load additional resources for the map.
                devtool = org.jetbrains.kotlin.gradle.targets.js.webpack.WebpackDevtool.INLINE_CHEAP_MODULE_SOURCE_MAP
            }
        }
    }
}

// Define developmentExtension and productionExtension tasks which build the extension in development/production mode.
tasks {
    val development = "development"
    val production = "production"

    val tasks = listOf(development, production).associateWith { env ->
        val extensionFolder = "build/extension"

        val browserWebpackTask = getByName("browser${env.capitalize()}Webpack")

        val copyBundleFile = register<Copy>("copy${env.capitalize()}BundleFile") {
            dependsOn(browserWebpackTask)
            from("build/distributions") {
                include("*.js", "*.js.LICENSE.txt")
            }
            into("$extensionFolder/js")
        }

        val copyResources = register<Copy>("copy${env.capitalize()}Resources") {
            from("src/main/resources")
            into(extensionFolder)
        }

        val copyJsDeps = register<Copy>("copy${env.capitalize()}JsDeps") {
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

        register<Zip>("${env}Extension") {
            dependsOn(copyBundleFile, copyJsDeps, copyResources)
            from(extensionFolder)
            archiveAppendix.set(env)
        }
    }

    assemble {
        dependsOn(tasks[production])
    }
}
