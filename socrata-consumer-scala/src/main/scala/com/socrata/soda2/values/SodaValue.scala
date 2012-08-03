package com.socrata.soda2.values

import java.net.URI

import com.rojoma.json.ast._
import com.rojoma.json.matcher._
import org.joda.time.LocalDateTime

import com.rojoma.json.codec.JsonCodec
import com.rojoma.json.matcher.PObject
import scala.Some
import com.rojoma.json.ast.JArray
import com.rojoma.json.ast.JString
import com.rojoma.json.matcher.POption
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormatter}

sealed abstract class SodaValue {
  def sodaType: SodaType
  def asJson: JValue
}

sealed abstract class SodaType {
  def convertFrom(value: JValue): Option[SodaValue]
}

case class SodaString(value: String) extends SodaValue {
  def sodaType = SodaString
  def asJson = JString(value)
}

case object SodaString extends SodaType with (String => SodaString) {
  def convertFrom(value: JValue) = JsonCodec[String].decode(value).map(SodaString)
}

case class SodaBlob(uri: URI) extends SodaValue {
  def sodaType = SodaBlob
  def asJson = JString(uri.toString)
}

case object SodaBlob extends SodaType with (URI => SodaBlob) {
  def convertFrom(value: JValue) = JsonCodec[String].decode(value).map { linkStr =>
    val uri = URI.create(linkStr) // TODO: Catch exceptions
    SodaBlob(uri)
  }
}

case class SodaLink(uri: URI) extends SodaValue {
  def sodaType = SodaLink
  def asJson = JString(uri.toString)
}

case object SodaLink extends SodaType with (URI => SodaLink) {
  def convertFrom(value: JValue) = JsonCodec[String].decode(value).map { linkStr =>
    val uri = URI.create(linkStr) // TODO: Catch exceptions
    SodaLink(uri)
  }
}

case class SodaNumber(value: BigDecimal) extends SodaValue {
  def sodaType = SodaNumber
  def asJson = JString(value.toString) // FIXME: is this right?  It's certainly how we receive it!
}

case object SodaNumber extends SodaType with (BigDecimal => SodaNumber) {
  def convertFrom(value: JValue) = JsonCodec[String].decode(value).map { numStr =>
    val num = new java.math.BigDecimal(numStr) // TODO: Catch exceptions
    SodaNumber(num)
  }
}

case class SodaDouble(value: Double) extends SodaValue {
  def sodaType = SodaDouble
  def asJson = JNumber(value)
}

case object SodaDouble extends SodaType with (Double => SodaDouble) {
  def convertFrom(value: JValue) = JsonCodec[Double].decode(value).map(SodaDouble)
}

case class SodaMoney(value: BigDecimal) extends SodaValue {
  def sodaType = SodaMoney
  def asJson = JString(value.toString) // FIXME: is this right?  It's certainly how we receive it!
}

case object SodaMoney extends SodaType with (BigDecimal => SodaMoney) {
  def convertFrom(value: JValue) = JsonCodec[String].decode(value).map { numStr =>
    val num = new java.math.BigDecimal(numStr) // TODO: Catch exceptions
    SodaMoney(num)
  }
}

case class SodaGeospatial(value: JValue) extends SodaValue {
  def sodaType = SodaGeospatial
  def asJson = value
}

case object SodaGeospatial extends SodaType with (JValue => SodaGeospatial) {
  def convertFrom(value: JValue) = if(value == JNull) None else Some(SodaGeospatial(value))
}

case class SodaLocation(address: Option[String], city: Option[String], state: Option[String], zip: Option[String], coordinates: Option[(Double, Double)]) extends SodaValue {
  def sodaType = SodaLocation
  def asJson = SodaLocation.jsonCodec.encode(this)
}

case object SodaLocation extends SodaType with ((Option[String], Option[String], Option[String], Option[String], Option[(Double, Double)]) => SodaLocation) {
  private val address = Variable[String]
  private val city = Variable[String]
  private val state = Variable[String]
  private val zip = Variable[String]
  private val latitude = Variable[String]
  private val longitude = Variable[String]
  private val Pattern = PObject(
    "human_address" -> POption(PObject(
      "address" -> POption(address).orNull,
      "city" -> POption(city).orNull,
      "state" -> POption(state).orNull,
      "zip" -> POption(zip).orNull)),
    "latitude" -> POption(latitude).orNull,
    "longitude" -> POption(longitude).orNull)

  implicit val jsonCodec = new JsonCodec[SodaLocation] {
    def encode(x: SodaLocation) =
      Pattern.generate(address :=? x.address, city :=? x.city, state :=? x.state, zip :=? x.zip,
        latitude :=? x.coordinates.map(_._1.toString),
        longitude :=? x.coordinates.map(_._2.toString))

    def decode(v: JValue) = Pattern.matches(v).map { results =>
      val coords = for {
        lat <- latitude.get(results)
        lon <- latitude.get(results)
      } yield (lat.toDouble, lon.toDouble) // TODO: Error handling
      SodaLocation(address.get(results), city.get(results), state.get(results), zip.get(results), coords)
    }
  }

  def convertFrom(value: JValue) = JsonCodec[SodaLocation].decode(value)
}

case class SodaBoolean(value: Boolean) extends SodaValue {
  def sodaType = SodaBoolean
  def asJson = JBoolean(value)
}

case object SodaBoolean extends SodaType with (Boolean => SodaBoolean) {
  def convertFrom(value: JValue) = JsonCodec[Boolean].decode(value).map(SodaBoolean)
}

case class SodaTimestamp(value: LocalDateTime) extends SodaValue {
  def sodaType = SodaTimestamp
  def asJson = JString(SodaTimestamp.formatSodaTimestamp(value))
}

case object SodaTimestamp extends SodaType with (LocalDateTime => SodaTimestamp) {
  def convertFrom(value: JValue) = for {
    str <- JsonCodec[String].decode(value)
    localdatetime <- parseSodaTimestamp(str)
  } yield new SodaTimestamp(localdatetime)

  private val Format = """(\d\d\d\d)-(\d\d)-(\d\d)T(\d\d):(\d\d):(\d\d)""".r
  private def parseSodaTimestamp(s: String) = s match {
    case Format(year,mon,day,hour,min,sec) =>
      Some(new LocalDateTime(year.toInt, mon.toInt, day.toInt, hour.toInt, min.toInt, sec.toInt))
    case _ =>
      None
  }

  private def formatSodaTimestamp(ts: LocalDateTime) =
    ISODateTimeFormat.dateTimeNoMillis.print(ts)
}

case class SodaArray(value: JArray) extends SodaValue {
  def sodaType = SodaArray
  def asJson = value
}

case object SodaArray extends SodaType with (JArray => SodaArray) {
  def convertFrom(value: JValue) = value.cast[JArray].map(SodaArray)
}

case class SodaObject(value: JObject) extends SodaValue {
  def sodaType = SodaObject
  def asJson = value
}

case object SodaObject extends SodaType with (JObject => SodaObject) {
  def convertFrom(value: JValue) = value.cast[JObject].map(SodaObject)
}