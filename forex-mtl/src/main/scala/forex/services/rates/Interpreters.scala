package forex.services.rates

import cats.Applicative
import interpreters._
import cats.effect.{Clock, Sync}
import forex.adapters.OneFrameClient
import org.http4s.Uri
import org.http4s.client.Client
import forex.config.{ApplicationConfig, OneFrameConfig}
import forex.services.rates.interpreters.CachedRates

object Interpreters {
  def dummy[F[_]: Applicative]: Algebra[F] = new RatesDummy[F]()

  def live[F[_]: Sync: Clock](
                               httpClient: Client[F],
                               cfg: ApplicationConfig
                             ) = {
    val of: OneFrameConfig = cfg.oneFrame
    val base = Uri.unsafeFromString(of.baseUrl)
    val live = new OneFrameClient[F](httpClient, base, of.token)

    CachedRates.create[F](live, cfg.cache.ttl)
  }
}
