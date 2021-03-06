package com.socrata.soda2.http
package impl

import com.rojoma.json.ast.JObject
import com.rojoma.json.util.SimpleJsonCodecBuilder
import com.rojoma.json.codec.JsonCodec

import com.socrata.soda2.exceptions.soda1.Soda1Exception
import com.socrata.soda2.exceptions.ServerException
import com.socrata.http.{BodyConsumer, HeadersConsumer, Headers}
import com.socrata.soda2.{Resource, InvalidResponseJsonException}

import HeadersConsumerUtils._

private[http] class ErrorHeadersConsumer(resource: Resource, code: Int) extends HeadersConsumer[Nothing] {
  import ErrorHeadersConsumer._
  def apply(headers: Headers): Either[BodyConsumer[Nothing], Nothing] = {
    val codec = jsonCodec(headers)
    Left(new SingleJValueBodyConsumer(codec).map {
      case value: JObject =>
        if(headers.contains("x-error-code") || headers.contains("x-error-message")) processLegacyError(resource, code, value)
        else processError(value)
      case other =>
        throw new InvalidResponseJsonException(other, "Error response body was not an object")
    })
  }
}

private[http] object ErrorHeadersConsumer {
  case class Error(code: String, message: String, data: Option[JObject])
  implicit val errorCodec = SimpleJsonCodecBuilder[Error].build(
    "code", _.code,
    "message", _.message,
    "data", _.data)

  def processError(errorObject: JObject): Nothing = {
    JsonCodec.fromJValue[Error](errorObject) match {
      case Some(Error(code, message, data)) =>
        throw ServerException(code, message, data)
      case None =>
        badErrorBody(errorObject)
    }
  }

  case class LegacyError(code: Option[String], message: Option[String])
  implicit val legacyCodec = SimpleJsonCodecBuilder[LegacyError].build("code", _.code, "message", _.message)

  def processLegacyError(resource: Resource, status: Int, errorObject: JObject): Nothing = {
    JsonCodec.fromJValue[LegacyError](errorObject) match {
      case Some(LegacyError(code, message)) =>
        throw Soda1Exception(resource, status, code.getOrElse("internal error"), message)
      case None =>
        badErrorBody(errorObject)
    }
  }

  def badErrorBody(errorObject: JObject): Nothing =
    throw new InvalidResponseJsonException(errorObject, "Response body was not interpretable as an error")
}
