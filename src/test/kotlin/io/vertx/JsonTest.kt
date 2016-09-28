package io.vertx

import io.vertx.core.json.*
import io.vertx.lang.kotlin.*
import org.junit.*
import kotlin.test.*

class JsonTest {
    @Test
    fun smoke() {
        val result = json {
            obj(
                    "a" to array(1, 2, 3),
                    "obj" to obj("b1" to 1, "b2" to "2"),
                    "imperative-loop" to obj {
                        for (i in 1..3) {
                            put("k_$i", i)
                        }
                    },
                    "map" to obj((1..3).map { "k_$it" to it }),
                    "d" to "d"
            )
        }

        assertTrue { result is JsonObject }
        assertEquals("""{"a":[1,2,3],"obj":{"b1":1,"b2":"2"},"imperative-loop":{"k_1":1,"k_2":2,"k_3":3},"map":{"k_1":1,"k_2":2,"k_3":3},"d":"d"}""", result.toString())
    }

    @Test
    fun testMapToObj() {
        val result = json {
            obj(mapOf("k" to "v"))
        }

        assertTrue { result is JsonObject }
        assertEquals("{\"k\":\"v\"}", result.toString())
    }

    @Test
    fun testArrays() {
        val result = json {
            array(
                    array(1, 2, 3),
                    array(listOf(4, 5, 6)),
                    array(setOf(7)),
                    array {
                        for (i in 8..10) {
                            add(i)
                        }
                    }
            )
        }

        assertTrue { result is JsonArray }
        assertEquals("[[1,2,3],[4,5,6],[7],[8,9,10]]", result.toString())
    }

    @Test
    fun typeInference() {
        val r1: JsonObject = json { obj() }
        val r2: JsonArray = json { array() }

        assertNotNull(r1)
        assertNotNull(r2)
    }

    @Test
    fun testIndexAccessor() {
        assertEquals(1, json { array(1) }[0])
    }

    @Test
    fun testKeyAccessor() {
        assertEquals("v", json { obj("k" to "v") }["k"])
    }
}