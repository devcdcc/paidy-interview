package forex.services.rates

import forex.domain.Currency

object errors {
  sealed trait Error extends Throwable with Product with Serializable
  object Error {
    final case class OneFrameLookupFailed(msg: String) extends Error
    final case class NotFound(from: Currency, to: Currency) extends Error
    final case class Upstream(msg: String) extends Error
    final case class Parse(msg: String) extends Error
    final case class Exception(t: Throwable) extends Error
  }
}
