package gamestats.analytics

import java.util.concurrent.{Executors, TimeUnit}

import gamestats.analytics.storage.InMemoryAnalysisStore
import gamestats.analytics.TestSupport.analysisOf

class InMemoryAnalysisStoreSpec extends munit.FunSuite:

  test("a saved analysis can be found and listed") {
    val store = InMemoryAnalysisStore()
    store.save(analysisOf("m1", "toy", None, Nil))
    assertEquals(store.find("m1").map(_.matchId), Some("m1"))
    assertEquals(store.find("other"), None)
    assertEquals(store.all.map(_.matchId), List("m1"))
  }

  test("saving the same match again replaces the previous analysis") {
    val store = InMemoryAnalysisStore()
    store.save(analysisOf("m1", "toy", Some("a"), Nil))
    store.save(analysisOf("m1", "toy", Some("b"), Nil))
    assertEquals(store.all.size, 1)
    assertEquals(store.find("m1").flatMap(_.winnerPlayerId), Some("b"))
  }

  test("concurrent saves do not lose analyses") {
    val store = InMemoryAnalysisStore()
    val executor = Executors.newFixedThreadPool(8)
    (1 to 400).foreach(number => executor.submit(new Runnable { def run(): Unit = store.save(analysisOf(s"m$number", "toy", None, Nil)) }))
    executor.shutdown()
    assert(executor.awaitTermination(10, TimeUnit.SECONDS))
    assertEquals(store.all.size, 400)
  }
