package gamestats.analytics

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, TimeUnit}

import scala.jdk.CollectionConverters.*
import scala.util.Using

import gamestats.analytics.analysis.GameAnalyzerRegistry
import gamestats.analytics.analysis.games.{CombatAnalyzer, RacingAnalyzer}
import gamestats.analytics.domain.*
import gamestats.analytics.service.AnalyticsService
import gamestats.analytics.storage.FileAnalysisStore
import gamestats.analytics.TestSupport.*

class FileAnalysisStoreSpec extends munit.FunSuite:

  private val temporaryDirectory = FunFixture[Path](
    setup = _ => Files.createTempDirectory("gamestats-store-"),
    teardown = directory => if Files.exists(directory) then deleteRecursively(directory)
  )

  private def deleteRecursively(directory: Path): Unit =
    Using.resource(Files.walk(directory))(_.iterator().asScala.toList.reverse.foreach(Files.delete))

  private def openStore(directory: Path, problems: ConcurrentLinkedQueue[String] = ConcurrentLinkedQueue()) =
    FileAnalysisStore.open(directory, problems.add(_))

  private def jsonFiles(directory: Path): List[String] =
    Using.resource(Files.list(directory))(_.iterator().asScala.toList).map(_.getFileName.toString).sorted

  temporaryDirectory.test("a saved analysis can be found and listed") { directory =>
    val store = openStore(directory)
    store.save(analysisOf("m1", "toy", Some("p1"), Nil))
    assertEquals(store.find("m1").flatMap(_.winnerPlayerId), Some("p1"))
    assertEquals(store.find("other"), None)
    assertEquals(store.all.map(_.matchId), List("m1"))
  }

  temporaryDirectory.test("analyses survive a restart because a new store loads them from the folder") { directory =>
    val first = openStore(directory)
    first.save(analysisOf("m1", "toy", Some("p1"), List(PlayerAnalysis("p1", 3, Map("score" -> 9.5)))))
    first.save(analysisOf("m2", "toy", None, Nil))

    val afterRestart = openStore(directory)
    assertEquals(afterRestart.all.map(_.matchId), List("m1", "m2"))
    assertEquals(afterRestart.find("m1").map(_.players.head.metrics), Some(Map("score" -> 9.5)))
  }

  temporaryDirectory.test("saving the same match again replaces the stored file and the loaded analysis") { directory =>
    val store = openStore(directory)
    store.save(analysisOf("m1", "toy", Some("a"), Nil))
    store.save(analysisOf("m1", "toy", Some("b"), Nil))
    assertEquals(jsonFiles(directory).size, 1)
    assertEquals(openStore(directory).find("m1").flatMap(_.winnerPlayerId), Some("b"))
  }

  temporaryDirectory.test("no temporary file is left behind after saving") { directory =>
    val store = openStore(directory)
    (1 to 5).foreach(number => store.save(analysisOf(s"m$number", "toy", None, Nil)))
    assert(jsonFiles(directory).forall(_.endsWith(".json")), clue = jsonFiles(directory))
  }

  temporaryDirectory.test("match ids with unusual characters are stored and found again") { directory =>
    val unusual = "match/with spaces:and?symbols 日本"
    openStore(directory).save(analysisOf(unusual, "toy", None, Nil))
    assertEquals(openStore(directory).find(unusual).map(_.matchId), Some(unusual))
  }

  temporaryDirectory.test("a very long match id still produces a valid file name") { directory =>
    val longId = "m" * 400
    openStore(directory).save(analysisOf(longId, "toy", None, Nil))
    assert(jsonFiles(directory).head.length < 255)
    assertEquals(openStore(directory).find(longId).map(_.matchId), Some(longId))
  }

  temporaryDirectory.test("a corrupt file is ignored and reported while the valid ones still load") { directory =>
    openStore(directory).save(analysisOf("good", "toy", None, Nil))
    Files.writeString(directory.resolve("broken.json"), "{ not valid", StandardCharsets.UTF_8)
    Files.writeString(directory.resolve("notes.txt"), "ignored", StandardCharsets.UTF_8)

    val problems = ConcurrentLinkedQueue[String]()
    val store = openStore(directory, problems)
    assertEquals(store.all.map(_.matchId), List("good"))
    assertEquals(problems.asScala.toList.size, 1)
    assert(problems.peek().contains("broken.json"))
  }

  temporaryDirectory.test("a missing folder is created when the store opens") { directory =>
    val nested = directory.resolve("a").resolve("b")
    openStore(nested).save(analysisOf("m1", "toy", None, Nil))
    assert(Files.isDirectory(nested))
  }

  temporaryDirectory.test("a write failure is reported but the analysis is still available in memory") { directory =>
    val problems = ConcurrentLinkedQueue[String]()
    val store = openStore(directory, problems)
    deleteRecursively(directory)
    store.save(analysisOf("m1", "toy", None, Nil))
    assertEquals(store.find("m1").map(_.matchId), Some("m1"))
    assert(!problems.isEmpty)
  }

  temporaryDirectory.test("concurrent saves are all stored and all loaded after a restart") { directory =>
    val store = openStore(directory)
    val executor = Executors.newFixedThreadPool(8)
    (1 to 120).foreach(number => executor.submit(new Runnable { def run(): Unit = store.save(analysisOf(s"m$number", "toy", None, Nil)) }))
    executor.shutdown()
    assert(executor.awaitTermination(20, TimeUnit.SECONDS))
    assertEquals(store.all.size, 120)
    assertEquals(openStore(directory).all.size, 120)
  }

  temporaryDirectory.test("the service keeps results, aggregates and patterns after a restart") { directory =>
    val registry = GameAnalyzerRegistry(List(RacingAnalyzer, CombatAnalyzer))
    val first = AnalyticsService(registry, openStore(directory))
    exampleMatches.foreach((game, name) => assert(first.analyzeBatch(readBatchJson(game, name)).isRight))

    val second = AnalyticsService(registry, openStore(directory))
    assertEquals(second.matchSummaries(None).size, 4)
    assertEquals(second.gameAggregate("racing").map(_.aggregates("matchesAnalyzed")), Some(2.0))
    assertEquals(second.playerAggregate("player-04").map(_.aggregates("wins")), Some(1.0))
    assertEquals(second.patterns(None, Some("KILL_STREAK")).size, 2)
  }
