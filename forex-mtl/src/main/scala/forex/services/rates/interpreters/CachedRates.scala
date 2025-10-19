package forex.services.rates.interpreters


import cats.data.{EitherT, NonEmptyList}
import cats.effect.{Clock, Sync}
import cats.effect.concurrent.Ref
import cats.syntax.all._
import forex.adapters.OneFrameClient

import scala.concurrent.duration._
import forex.domain._
import forex.services.rates.Algebra
import forex.services.rates.errors.Error

final class CachedRates[F[_]: Sync: Clock](
                                            client: OneFrameClient[F],
                                            ttl: FiniteDuration,
                                            ref: Ref[F, Map[(Currency, Currency), (Rate, Long)]]
                                          ) extends Algebra[F] {


  private def nowMillis(implicit C: Clock[F]) = C.realTime(MILLISECONDS)
  private def fresh(now: Long, ts: Long): Boolean = (now - ts) <= ttl.toMillis


  def get(from: Currency, to: Currency): EitherT[F, Error, Rate] =
    EitherT(getMany(NonEmptyList.one((from -> to))).map(_.apply((from, to))))


  def getMany(pairs: NonEmptyList[(Currency, Currency)]): F[Map[(Currency, Currency), Either[Error, Rate]]] = {
    val uniq = pairs.toList.distinct
    for {
      now <- nowMillis
      cached <- ref.get
      (hits, misses) = uniq.partitionMap { p =>
        cached.get(p) match {
          case Some((rate, ts)) if fresh(now, ts) => Left(p -> Right[Error, Rate](rate))
          case _ => Right(p)
        }
      }

      fetched <- NonEmptyList.fromList(misses) match {
        case None => Map.empty[(Currency, Currency), Either[Error, Rate]].pure[F]
        case Some(nemiss) => client.fetchMany(nemiss)
      }
      _ <- fetched.toList.traverse_ {
        case (pair, Right(rate)) => nowMillis.flatMap(ts => ref.update(_ + (pair -> (rate -> ts))))
        case _ => Sync[F].unit
      }
    } yield hits.toMap ++ fetched
  }

  override def get(pair: Rate.Pair): F[Either[Error, Rate]] = get(pair.from, pair.to).value
}


object CachedRates {
  def create[F[_]: Sync: Clock](client: OneFrameClient[F], ttl: FiniteDuration): F[CachedRates[F]] =
    Ref.of[F, Map[(Currency, Currency), (Rate, Long)]](Map.empty).map(new CachedRates[F](client, ttl, _))
}