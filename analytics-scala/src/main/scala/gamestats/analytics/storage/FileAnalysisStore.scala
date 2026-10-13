package gamestats.analytics.storage

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try, Using}

import gamestats.analytics.decoding.AnalysisDecoder
import gamestats.analytics.domain.MatchAnalysis
import gamestats.analytics.encoding.AnalysisEncoder

final class FileAnalysisStore private (
    directory: Path,
    initialAnalyses: Map[String, MatchAnalysis],
    reportProblem: String => Unit
) extends AnalysisStore:

  private val analysesByMatchId = AtomicReference(initialAnalyses)

  def save(analysis: MatchAnalysis): Unit =
    Try(writeAtomically(analysis)) match
      case Failure(failure) => reportProblem(s"analysis of ${analysis.matchId} could not be written: ${failure.getMessage}")
      case Success(_)       => ()
    analysesByMatchId.updateAndGet(_.updated(analysis.matchId, analysis))
    ()

  def find(matchId: String): Option[MatchAnalysis] = analysesByMatchId.get().get(matchId)

  def all: List[MatchAnalysis] = analysesByMatchId.get().values.toList.sortBy(_.matchId)

  private def writeAtomically(analysis: MatchAnalysis): Unit =
    val target = directory.resolve(FileAnalysisStore.fileNameFor(analysis.matchId))
    val temporary = directory.resolve(target.getFileName.toString + ".tmp")
    Files.writeString(temporary, ujson.write(AnalysisEncoder.matchAnalysis(analysis), indent = 2), StandardCharsets.UTF_8)
    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    ()

object FileAnalysisStore:

  private val fileExtension = ".json"
  private val longestReadableName = 180

  def open(directory: Path, reportProblem: String => Unit): FileAnalysisStore =
    Files.createDirectories(directory)
    new FileAnalysisStore(directory, loadAll(directory, reportProblem), reportProblem)

  def fileNameFor(matchId: String): String =
    val readable = URLEncoder.encode(matchId, StandardCharsets.UTF_8)
    if readable.length <= longestReadableName then readable + fileExtension
    else MessageDigest.getInstance("SHA-256").digest(matchId.getBytes(StandardCharsets.UTF_8)).map("%02x".format(_)).mkString + fileExtension

  private def loadAll(directory: Path, reportProblem: String => Unit): Map[String, MatchAnalysis] =
    Using.resource(Files.list(directory))(_.iterator().asScala.toList)
      .filter(_.getFileName.toString.endsWith(fileExtension))
      .flatMap(path => loadOne(path, reportProblem))
      .map(analysis => analysis.matchId -> analysis)
      .toMap

  private def loadOne(path: Path, reportProblem: String => Unit): Option[MatchAnalysis] =
    Try(Files.readString(path, StandardCharsets.UTF_8)).toEither.left.map(_.getMessage).flatMap(AnalysisDecoder.decode) match
      case Right(analysis) => Some(analysis)
      case Left(reason) =>
        reportProblem(s"file ${path.getFileName} was ignored: $reason")
        None
