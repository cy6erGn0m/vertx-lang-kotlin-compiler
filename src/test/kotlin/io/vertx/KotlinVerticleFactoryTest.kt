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
        vertx.deployVerticle("singleVerticle.kt")
    }

    @Test
    fun testMultiple() {
        val l = CountDownLatch(1)
        vertx.deployVerticle("multipleVerticles.kt") {
            l.countDown()
        }

        l.await()
        // all the verticles need to be initialized properly
        for (i in 1..3) {
            assertEquals(i.toString(), vertx.sharedData().getLocalMap<String, String>("M")["M$i"])
        }
    }
}
