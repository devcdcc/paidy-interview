package forex

import cats.effect.{ConcurrentEffect, Resource, Timer }
import forex.http.rates.RatesHttpRoutes
import forex.programs._
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.middleware.{ AutoSlash, Timeout }

import forex.config.ApplicationConfig
import forex.services.rates.Interpreters
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client

class Module[F[_]: ConcurrentEffect: Timer](config: ApplicationConfig) {

  private val ec = scala.concurrent.ExecutionContext.global

  private val httpClient: Resource[F, Client[F]] =
    BlazeClientBuilder[F](ec)
      .withRequestTimeout(config.oneFrame.timeout)
      .resource

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = http => AutoSlash(http)
  private val appMiddleware: TotalMiddleware      = http => Timeout(config.http.timeout)(http)

  val httpAppResource: Resource[F, HttpApp[F]] =
    for {
      client <- httpClient
      ratesSvc <- Resource.eval(Interpreters.live[F](client, config))
      ratesProg = RatesProgram[F](ratesSvc)
      routes    = new RatesHttpRoutes[F](ratesProg).routes
      httpApp   = appMiddleware(routesMiddleware(routes).orNotFound)
    } yield httpApp

}
