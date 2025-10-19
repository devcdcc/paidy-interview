package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    oneFrame: OneFrameConfig,
    cache: CacheConfig
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

final case class OneFrameConfig(
  baseUrl: String,
  token: String,
  timeout: FiniteDuration,
  batchWindow: FiniteDuration
)

final case class CacheConfig(
  ttl: FiniteDuration
)
