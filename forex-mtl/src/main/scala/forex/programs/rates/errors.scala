package forex.programs.rates

import forex.services.rates.errors.{ Error => RatesServiceError }

object errors {

  sealed trait Error extends Exception
  object Error {
    final case class RateLookupFailed(msg: String) extends Error
  }

  def toProgramError(error: RatesServiceError): Error = {
    println(error)
    error match {
      case RatesServiceError.OneFrameLookupFailed(msg) => Error.RateLookupFailed(msg)
      case RatesServiceError.NotFound(from, to) => Error.RateLookupFailed(s"Rate not found for pair: $from to $to")
      case RatesServiceError.Upstream(msg) => Error.RateLookupFailed(s"Upstream error: $msg")
      case RatesServiceError.Parse(msg) => Error.RateLookupFailed(s"Parse error: $msg")
      case RatesServiceError.Exception(t) => Error.RateLookupFailed(s"Exception: ${t.getMessage}")
    }
  }
}
