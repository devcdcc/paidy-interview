package forex.adapters

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.all._
import forex.domain._
import forex.services.rates.errors.Error
import io.circe.Decoder
import io.circe.generic.semiauto._
import org.http4s.Method._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.Client
import org.typelevel.ci.CIString

final class OneFrameClient[F[_]: Sync](
    client: Client[F],
    baseUri: Uri,
    token: String
) {
  import OneFrameClient._

  def fetchMany(pairs: NonEmptyList[(Currency, Currency)]): F[Map[(Currency, Currency), Either[Error, Rate]]] = {
    val uriWithPairs = pairs.toList.foldLeft(baseUri / "rates") {
      case (u, (f, t)) => u.withQueryParam("pair", s"${f.show}${t.show}")
    }

    val req = Request[F](GET, uriWithPairs)
      .putHeaders(Header.Raw(CIString("token"), token))

    client
      .expectOr[List[OneFrameQuote]](req) { resp =>
        resp.as[String].map(body => Error.Upstream(s"status=${resp.status.code} body=${body}"))
      }
      .attempt
      .map {
        case Left(t) => pairs.toList.map(_ -> Left[Error, Rate](Error.Exception(t))).toMap
        case Right(quotes) =>
          val byPair = quotes.iterator.map(q => ((q.from.toUpperCase, q.to.toUpperCase), q)).toMap
          pairs.toList.map {
            case (f, t) =>
              val keyU = (f.show.toUpperCase, t.show.toUpperCase)
              val res: Either[Error, Rate] =
                byPair
                  .get(keyU)
                  .toRight[Error](Error.NotFound(f, t))
                  .flatMap(_.toRate(f, t).leftMap(Error.Parse))
              (f -> t) -> res
          }.toMap
      }
  }
}

object OneFrameClient {
  final case class OneFrameQuote(
      from: String,
      to: String,
      price: BigDecimal,
      time_stamp: String
  ) {
    def toRate(expectedFrom: Currency, expectedTo: Currency): Either[String, Rate] =
      for {
        _ <- Either.cond(from.equalsIgnoreCase(expectedFrom.show), (), s"Unexpected from=$from")
        _ <- Either.cond(to.equalsIgnoreCase(expectedTo.show), (), s"Unexpected to=$to")
        ts <- Timestamp.parse(time_stamp)
      } yield Rate(Rate.Pair(expectedFrom, expectedTo), Price(price), ts)
  }

  implicit val decoder: Decoder[OneFrameQuote]                                  = deriveDecoder
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, List[OneFrameQuote]] = jsonOf[F, List[OneFrameQuote]]
}
