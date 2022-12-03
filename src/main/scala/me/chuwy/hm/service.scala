package me.chuwy.hm
import cats.effect._
import cats.syntax.all._
import fs2.Stream
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.blaze.server._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

case class Counter(counter: Int)

object service extends IOApp {
  def counter(ref: Ref[IO, Int]): HttpRoutes[IO] = {
    HttpRoutes.of[IO] {
      case GET -> Root / "counter" =>
        for {
          x <- ref.modify(x => (x + 1, x))
          resp <- Ok(Counter(x).asJson)
        } yield resp
    }
  }

  def slow: HttpRoutes[IO] = {
    def soSlow(chunk: Int, total: Int, time: Int): Stream[IO, String] = {
      (Stream.awakeEvery[IO](time.second) *>
        Stream.emits('0' to '9')).map(_.toString).repeat.take(total).chunkN(chunk).map(v => v.toList.fold("")(_ + _) + "\n")
    }

    HttpRoutes.of[IO] {
      case GET -> Root / "slow" / chunk / total / time =>
        try {
          val ch = chunk.toInt
          val to = total.toInt
          val ti = time.toInt

          if (ch <= 0 || to <= 0 || ti <= 0) BadRequest("Значения должны быть больше 0")
          else Ok(soSlow(ch, to, ti))
        }
        catch {
          case _: Throwable => BadRequest("А это точно число?")
        }
    }
  }

  def run(args: List[String]): IO[ExitCode] = {
    for {
      ref <- Ref[IO].of(1)
      wt = counter(ref) <+> slow

      exit <- BlazeServerBuilder[IO](global)
        .bindHttp(8080, "localhost")
        .withHttpApp(wt.orNotFound)
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    } yield exit
  }
}