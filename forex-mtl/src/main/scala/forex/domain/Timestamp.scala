package forex.domain

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.util.Try

case class Timestamp(value: OffsetDateTime) extends AnyVal

object Timestamp {
  def now: Timestamp = Timestamp(OffsetDateTime.now)
  def parse(timestamp: String): Either[String, Timestamp] =
    Try {
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
      Timestamp(OffsetDateTime.parse(timestamp, formatter))
    }.toOption.toRight(s"Invalid time_stamp: $timestamp")

}
