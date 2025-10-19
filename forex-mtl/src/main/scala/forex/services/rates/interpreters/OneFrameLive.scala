package forex.services.rates.interpreters

import cats.data.EitherT
import cats.effect.Sync
import cats.syntax.all._
import forex.domain.Rate.Pair
import io.circe.Decoder
import io.circe.generic.semiauto._
import org.http4s._
import org.http4s.Method._
import org.http4s.client.Client
import org.http4s.circe._
import forex.domain._
import forex.services.rates.Algebra
import forex.services.rates.errors.Error
import org.typelevel.ci.CIString

final class OneFrameLive[F[_]: Sync](
    client: Client[F],
    baseUri: Uri,
    token: String
) extends Algebra[F] {

  import OneFrameLive._

  def get(from: Currency, to: Currency): EitherT[F, Error, Rate] = {
    val pair = s"${from.show}${to.show}"

    val req = Request[F](
      method = GET,
      uri = (baseUri / "rates").withQueryParam("pair", pair)
    ).putHeaders(Header.Raw(CIString("token"), token))

    println(req)

    EitherT {
      client
        .expectOr[List[OneFrameQuote]](req) { resp =>
          resp
            .as[String]
            .map(body => Error.Upstream(s"status=${resp.status.code} body=${body}"))

        }
        .attempt
        .map {
          case Left(t) => Left(Error.Exception(t))
          case Right(xs) =>
            xs.headOption
              .toRight(Error.NotFound(from, to))
              .flatMap(_.toRate(from, to).leftMap(msg => Error.Parse(msg)))
        }
    }
  }

  override def get(pair: Rate.Pair): F[Either[Error, Rate]] = get(pair.from, pair.to).value
}

object OneFrameLive {
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
      } yield
        Rate(
          Pair(from = expectedFrom, to = expectedTo),
          price = Price(price),
          timestamp = ts
        )
  }

  implicit val decoder: Decoder[OneFrameQuote]                                  = deriveDecoder
  implicit def entityDecoder[F[_]: Sync]: EntityDecoder[F, List[OneFrameQuote]] = jsonOf[F, List[OneFrameQuote]]
}
