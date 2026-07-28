plugins {
    id("fabric-loom") version "1.14-SNAPSHOT"
    java
}

import org.gradle.api.tasks.bundling.AbstractArchiveTask

val minecraftVersion = property("minecraft_version") as String
val yarnMappings = property("yarn_mappings") as String
val loaderVersion = property("loader_version") as String
val meteorVersion = property("meteor_version") as String
val archivesBaseName = property("archives_base_name") as String
val modVersion = property("mod_version") as String
val mavenGroup = property("maven_group") as String

base {
    archivesName.set(archivesBaseName)
}

version = modVersion
group = mavenGroup

repositories {
    mavenCentral()
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

val yguard by configurations.creating

dependencies {
    // Fabric
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")

    // Meteor Client
    modImplementation("meteordevelopment:meteor-client:$meteorVersion")

    yguard("com.yworks:yguard:5.0.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "minecraft_version" to minecraftVersion,
            "jdk_version" to "21",
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${base.archivesName.get()}" }
        }
    }
}

val obfuscatedJar = layout.buildDirectory.file("libs/${base.archivesName.get()}-${project.version}-obfuscated.jar")
val yguardMap = layout.buildDirectory.file("yguard/yguard-map.xml")

tasks.register("obfuscateJar") {
    group = "build"
    description = "Creates a rename-only yGuard obfuscated JAR from the remapped Fabric artifact."

    val remapJar = tasks.named<AbstractArchiveTask>("remapJar")
    dependsOn(remapJar)

    val inputJar = remapJar.flatMap { it.archiveFile }
    inputs.file(inputJar)
    outputs.file(obfuscatedJar)
    outputs.file(yguardMap)

    doLast {
        val inJar = inputJar.get().asFile
        val outJar = obfuscatedJar.get().asFile
        val mapFile = yguardMap.get().asFile

        if (!inJar.isFile) {
            throw GradleException("Remapped JAR not found for yGuard input: ${inJar.absolutePath}")
        }

        outJar.parentFile.mkdirs()
        mapFile.parentFile.mkdirs()

        ant.withGroovyBuilder {
            "taskdef"(
                "name" to "yguard",
                "classname" to "com.yworks.yguard.YGuardTask",
                "classpath" to yguard.asPath
            )
            "yguard" {
                "inoutpair"("in" to inJar.absolutePath, "out" to outJar.absolutePath)
                "externalclasses" {
                    "pathelement"("path" to configurations.compileClasspath.get().asPath)
                }
                "attribute"(
                    "name" to listOf(
                        "SourceFile",
                        "LineNumberTable",
                        "LocalVariableTable",
                        "LocalVariableTypeTable",
                        "RuntimeVisibleAnnotations",
                        "RuntimeInvisibleAnnotations",
                        "RuntimeVisibleParameterAnnotations",
                        "RuntimeInvisibleParameterAnnotations",
                        "AnnotationDefault",
                        "Signature",
                        "InnerClasses",
                        "EnclosingMethod",
                        "MethodParameters",
                        "Deprecated",
                        "NestHost",
                        "NestMembers",
                        "Record",
                        "PermittedSubclasses"
                    ).joinToString(", ")
                )
                "rename"(
                    "logfile" to mapFile.absolutePath,
                    "conservemanifest" to "true",
                    "replaceClassNameStrings" to "false"
                ) {
                    "property"("name" to "naming-scheme", "value" to "small")
                    "property"("name" to "language-conformity", "value" to "compatible")
                    "keep"(
                        "sourcefile" to "keep",
                        "linenumbertable" to "keep",
                        "localvariabletable" to "keep",
                        "localvariabletypetable" to "keep"
                    ) {
                        "class"("name" to "com.kami.order.KamiOrderAddon", "classes" to "private", "methods" to "protected", "fields" to "public")
                        "class"("name" to "com.kami.order.mixin.MouseLockMixin", "classes" to "private")
                        "class"("classes" to "private", "methods" to "public", "fields" to "public") {
                            "patternset" {
                                "include"("name" to "com.kami.order.modules.**")
                            }
                        }
                    }
                }
            }
        }

        logger.lifecycle("Created obfuscated JAR: ${outJar.absolutePath}")
        logger.lifecycle("Created yGuard mapping: ${mapFile.absolutePath}")
    }
}
