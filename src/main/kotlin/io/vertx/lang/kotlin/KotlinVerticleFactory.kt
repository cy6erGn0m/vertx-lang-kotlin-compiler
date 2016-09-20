package io.vertx.lang.kotlin

import com.intellij.openapi.*
import io.vertx.core.*
import io.vertx.core.spi.*
import org.jetbrains.kotlin.cli.common.*
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.config.*
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.codegen.state.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.js.descriptorUtils.*
import org.jetbrains.kotlin.load.java.*
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.resolve.*
import java.io.*
import java.net.*
import java.nio.file.*
import java.util.*
import java.util.jar.*

class KotlinVerticleFactory : VerticleFactory {
    override fun prefix() = "kt"

    override fun createVerticle(verticleName: String, classLoader: ClassLoader): Verticle {
        val resourceName = VerticleFactory.removePrefix(verticleName)

        var url = classLoader.getResource(resourceName)
        if (url == null) {
            var f = File(resourceName)
            if (!f.isAbsolute) {
                f = File(System.getProperty("user.dir"), resourceName)
            }
            if (f.exists() && f.isFile) {
                url = f.toURI().toURL()
            }
        }
        if (url == null) {
            throw IllegalStateException("Cannot find verticle script: $verticleName on classpath")
        }

//        val url = File("/home/cy/projects/vertx-lang-kotlin/src/main/kotlin/io/vertx/lang/kotlin/KotlinVerticleFactory.kt").toURI().toURL()

        val temp = Files.createTempDirectory(KotlinVerticleFactory::class.simpleName + "-")

        println("temp dir is $temp")

        val configuration = CompilerConfiguration()
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false))
        configuration.put(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE, false)
        configuration.put(CLIConfigurationKeys.REPORT_PERF, false)

        configuration.put(CommonConfigurationKeys.LANGUAGE_FEATURE_SETTINGS, LanguageVersion.LATEST)

        configuration.put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_1_6)
        configuration.put(JVMConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME)

        val classPath = (
                classLoader.classPath()
                        + ClassLoader.getSystemClassLoader().classPath()
                        + (Thread.currentThread().contextClassLoader?.classPath() ?: emptyList())
                        + propertyClassPath("java.class.path")
                        + propertyClassPath("sun.boot.class.path")
                ).distinct().filter { Files.exists(it) }

        for (item in classPath) {
            configuration.add(JVMConfigurationKeys.CONTENT_ROOTS, JvmClasspathRoot(item.toFile()))
        }
        configuration.add(JVMConfigurationKeys.CONTENT_ROOTS, KotlinSourceRoot(Paths.get(url.toURI()).toString()))

        val collectedVerticles = HashSet<String>()

        val environment = KotlinCoreEnvironment.createForProduction(Disposable { }, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val finalState = KotlinToJVMBytecodeCompiler.analyzeAndGenerate(environment, GenerationStateEventCallback { state ->
            collectedVerticles += state.factory.getClassFiles().toList()
                    .map { it.relativePath.removeSuffix(".class").replace("/", ".") }
                    .filter { it !in collectedVerticles }
                    .mapNotNull { state.bindingContext.get(BindingContext.FQNAME_TO_CLASS_DESCRIPTOR, FqNameUnsafe(it)) }
                    .filter { it.defaultType.constructor.supertypes.any { it.getJetTypeFqName(false) == "io.vertx.core.Verticle" } }
                    .map { it.defaultType.getJetTypeFqName(false) }
        })

        if (collectedVerticles.isEmpty()) {
            throw IllegalStateException("No verticle classes found in the file")
        }

        val compilerClassLoader = GeneratedClassLoader(finalState!!.factory, classLoader)
        return when (collectedVerticles.size) {
            0 -> throw IllegalStateException("No verticle classes found in the file")
            1 -> compilerClassLoader.loadClass(collectedVerticles.single()).verticle()
            else -> CompositeVerticle(collectedVerticles.map { compilerClassLoader.loadClass(it).verticle() })
        }
    }

    private fun Class<*>.verticle(): Verticle = newInstance() as Verticle

    private fun ClassLoader.classPath() = (classPathImpl() + manifestClassPath()).distinct()

    private fun ClassLoader.classPathImpl(): List<Path> {
        val parentUrls = parent?.classPathImpl() ?: emptyList()

        return when {
            this is URLClassLoader -> urLs.filterNotNull().map(URL::toURI).mapNotNull { ifFailed(null) { Paths.get(it) } } + parentUrls
            else -> parentUrls
        }
    }

    private fun ClassLoader.manifestClassPath() =
            getResources("META-INF/MANIFEST.MF")
                    .asSequence()
                    .mapNotNull { ifFailed(null) { it.openStream().use { Manifest().apply { read(it) } } } }
                    .flatMap { it.mainAttributes?.getValue("Class-Path")?.splitToSequence(" ")?.filter(String::isNotBlank) ?: emptySequence() }
                    .mapNotNull { ifFailed(null) { Paths.get(URI.create(it)) } }
                    .toList()

    fun propertyClassPath(key: String) = System.getProperty(key)
            ?.split(File.pathSeparator)
            ?.filter { it.isNotEmpty() }
            ?.map { Paths.get(it) }
            ?: emptyList()

    private inline fun <R> ifFailed(default: R, block: () -> R) = try {
        block()
    } catch (t: Throwable) {
        default
    }

    private class CompositeVerticle(val children: List<Verticle>) : Verticle {
        private lateinit var vertx: Vertx

        override fun init(vertx: Vertx, context: Context) {
            this.vertx = vertx
        }

        override fun start(startFuture: Future<Void>) {
            CompositeFuture.all(
                    children.map { verticle ->
                        Future.future<String>().apply {
                            vertx.deployVerticle(verticle, completer())
                        }
                    }).setHandler { startFuture.complete() }
        }

        override fun stop(stopFuture: Future<Void>) {
        }

        override fun getVertx() = vertx
    }
}
