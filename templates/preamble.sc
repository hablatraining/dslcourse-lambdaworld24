import fs2.{Pipe, Pull, Stream}
import fs2.io.net.Network
import cats.effect.{IO, Async}
import cats.effect.unsafe.IORuntime
import org.http4s.*, org.http4s.implicits._
import io.circe.Json
import io.circe.{`export` as _, Json, *}
import io.circe.parser._
import io.circe.syntax._
import io.circe.optics.JsonPath._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
given LoggerFactory[IO] = Slf4jFactory.create[IO]

object IsArray: 
    def unapply(v: Json): Option[Vector[Json]] = 
        v.asArray
        
object IsObject: 
    def unapply(v: Json): Option[JsonObject] = 
        v.asObject
        
object IsString: 
    def unapply(v: Json): Option[String] = 
        v.asString
        
def stream[F[_]: Async: Network: LoggerFactory](u: org.http4s.Uri): Stream[F, Json] = 
    import org.http4s.ember.client.EmberClientBuilder
    import io.circe.jawn.CirceSupportParser
    import org.http4s.headers.Authorization
    import org.http4s.{Method, Request}
    import org.typelevel.jawn.Facade
    import org.typelevel.jawn.fs2._

    given Facade[Json] = new CirceSupportParser(None, false).facade
    val bearerToken = scala.util.Properties.envOrElse("BEARER_GITHUB_TOKEN", "undefined")
    val authHeader =  Authorization(Credentials.Token(AuthScheme.Bearer, bearerToken))
    Stream.resource(EmberClientBuilder.default[F].build).flatMap{
        _.stream(Request[F](Method.GET, u).withHeaders(authHeader))
         .flatMap(_.body.chunks.parseJsonStream)
    }
    
extension [A](st: Stream[IO, A])
    def run(using IORuntime): List[A] = 
        st.compile.toList.unsafeRunSync()

def allCommitPages[F[_]: Async: Network: LoggerFactory](repo: String): Stream[F, Json] =

    def newPage(i: Int): Stream[F, Json] = 
        log(s"> get page $i") >> 
        stream(Uri.unsafeFromString(s"$repo/commits?page=$i"))
    
    def go(i: Int, s: Stream[F,Json]): Pull[F, Json, Unit] =
        s.pull.uncons.flatMap:
            case Some((hd,tl)) =>
                hd(0) match
                    case IsArray(Vector()) => Pull.done
                    case _ => Pull.output(hd) >> go(i+1, tl ++ newPage(i))
            case None => Pull.done

    go(1, newPage(0)).stream
    
def log[F[_]: Async](msg: String): Stream[F, Unit] =
    Stream.eval(Async[F].delay(println(msg)))
    
extension [F[_], A](s1: Stream[F, A])
    def cross[B](s2: Stream[F, B]): Stream[F, (A, B)] = 
        s2 flatMap: b => 
            s1 map: a => 
                (a, b)
                
extension [A](i1: Iterator[A])
    def crossI[B](i2: Iterator[B]): Iterator[(A, B)] = 
        i2 flatMap: b => 
            i1 map: a => 
                (a, b)
        
