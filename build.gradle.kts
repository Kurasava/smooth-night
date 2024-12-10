import org.gradle.kotlin.dsl.accessors.runtime.addDependencyTo

plugins {
	id("fabric-loom") version "1.9-SNAPSHOT"
	id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
	archivesName.set(project.property("archives_base_name") as String)
}

configurations {
	create("addtojar")
}

repositories {
	mavenCentral()
	maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
	minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
	mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
	modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
	modApi("com.terraformersmc:modmenu:11.0.2")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
	implementation("org.json:json:20240303")
	"addtojar"("org.json:json:20240303")
}

tasks.processResources {
	inputs.property("version", project.version)

	filesMatching("fabric.mod.json") {
		expand("version" to project.version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 21
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	from("LICENSE") {
		rename { "${it}_${project.base.archivesName.get()}" }
	}
	from(configurations["addtojar"].filter { it.name.endsWith("jar") }.flatMap { zipTree(it) })

}

// configure the maven publication
publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			artifactId = project.property("archives_base_name") as String
			from(components["java"])
		}
	}
	repositories {}
}

