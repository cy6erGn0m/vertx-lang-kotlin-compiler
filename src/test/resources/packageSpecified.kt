package my.test

import io.vertx.core.*

class V : AbstractVerticle() {
    override fun start(startFuture: Future<Void>) {
        start()
        vertx.sharedData().getLocalMap<String, String>("P").put("V", "started")
        startFuture.complete()
    }
}
