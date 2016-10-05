package io.vertx

import io.vertx.core.*
import io.vertx.lang.kotlin.*
import org.junit.*
import java.util.concurrent.*
import kotlin.test.*

class KotlinVerticleFactoryTest {
    val factory = KotlinVerticleFactory()
    val vertx = Vertx.vertx()!!

    @Before
    fun setUp() {
        vertx.registerVerticleFactory(factory)
    }

    @After
    fun tearDown() {
        vertx.close()
    }

    @Test
    fun testSingle() {
        vertx.deployVerticleBlocking("singleVerticle.kt")
    }

    @Test
    fun testMultiple() {
        vertx.deployVerticleBlocking("multipleVerticles.kt")

        // all the verticles need to be initialized properly
        for (i in 1..3) {
            assertEquals(i.toString(), vertx.sharedData().getLocalMap<String, String>("M")["M$i"])
        }
    }

    @Test
    fun testIndirectInheritance() {
        vertx.deployVerticleBlocking("singleNonDirectInheritance.kt")
        assertEquals("true", vertx.sharedData().getLocalMap<String, String>("V2")["started"])
    }

    private fun Vertx.deployVerticleBlocking(name: String) {
        val latch = CountDownLatch(1)
        var e: Throwable? = null

        deployVerticle(name) {
            if (it.failed()) {
                e = it.cause()
            }

            latch.countDown()
        }

        if (!latch.await(10L, TimeUnit.SECONDS)) {
            throw TimeoutException("Verticle $name deployment timeout ")
        }

        e?.let { throw it }
    }
}
