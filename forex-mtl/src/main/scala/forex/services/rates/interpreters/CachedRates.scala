package forex.services.rates.interpreters

import cats.data.EitherT
import cats.effect.{ Clock, Sync }
import cats.syntax.all._
import cats.effect.concurrent.Ref
import forex.domain.Rate.Pair

import scala.concurrent.duration._
import forex.domain._
import forex.services.rates.Algebra
import forex.services.rates.errors.Error

final class CachedRates[F[_]: Sync: Clock](
    underlying: Algebra[F],
    ttl: FiniteDuration,
    ref: Ref[F, Map[(Currency, Currency), (Rate, Long)]]
) extends Algebra[F] {

  private def nowMillis(implicit c: Clock[F]): F[Long] = c.realTime(MILLISECONDS)

  def get(from: Currency, to: Currency): EitherT[F, Error, Rate] = EitherT {
    val key = (from, to)
    for {
      now <- nowMillis
      cached <- ref.get.map(_.get(key).filter { case (_, ts) => (now - ts) <= ttl.toMillis })
      out <- cached match {
              case Some((rate, _)) => rate.asRight[Error].pure[F]
              case None =>
                underlying.get(Pair(from, to)).flatTap {
                  case Right(rate) => nowMillis.flatMap(ts => ref.update(_ + (key -> (rate -> ts))))
                  case Left(_)     => Sync[F].unit
                }
            }
    } yield out
  }

  override def get(pair: Rate.Pair): F[Either[Error, Rate]] = get(pair.from, pair.to).value
}

object CachedRates {
  def create[F[_]: Sync: Clock](underlying: Algebra[F], ttl: FiniteDuration): F[CachedRates[F]] =
    Ref.of[F, Map[(Currency, Currency), (Rate, Long)]](Map.empty).map(new CachedRates[F](underlying, ttl, _))
}
