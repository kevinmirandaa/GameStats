package gamestats.analytics.decoding

import scala.util.Try

private[decoding] object JsonReading:

  def parseObject(rawJson: String, description: String): Either[String, ujson.Obj] =
    Try(ujson.read(rawJson)).toEither.left
      .map(_ => s"$description is not valid JSON")
      .flatMap(asObject(_, description))

  def asObject(value: ujson.Value, description: String): Either[String, ujson.Obj] =
    value match
      case objectValue: ujson.Obj => Right(objectValue)
      case _                      => Left(s"$description must be a JSON object")

  def requiredText(fields: ujson.Obj, fieldName: String): Either[String, String] =
    fields.obj.get(fieldName) match
      case Some(ujson.Str(text)) if text.nonEmpty => Right(text)
      case Some(_)                                => Left(s"$fieldName must be a non empty string")
      case None                                   => Left(s"$fieldName is required")

  def optionalText(fields: ujson.Obj, fieldName: String): Option[String] =
    fields.obj.get(fieldName).collect { case ujson.Str(text) if text.nonEmpty => text }

  def requiredArray(fields: ujson.Obj, fieldName: String): Either[String, List[ujson.Value]] =
    fields.obj.get(fieldName) match
      case Some(ujson.Arr(items)) => Right(items.toList)
      case Some(_)                => Left(s"$fieldName must be an array")
      case None                   => Left(s"$fieldName is required")

  def requiredNumber(fields: ujson.Obj, fieldName: String): Either[String, Double] =
    fields.obj.get(fieldName) match
      case Some(ujson.Num(number)) => Right(number)
      case Some(_)                 => Left(s"$fieldName must be a number")
      case None                    => Left(s"$fieldName is required")

  def requiredNumberMap(fields: ujson.Obj, fieldName: String): Either[String, Map[String, Double]] =
    fields.obj.get(fieldName) match
      case Some(ujson.Obj(entries)) =>
        sequence(entries.toList.map(numberEntry(fieldName))).map(_.toMap)
      case Some(_) => Left(s"$fieldName must be an object of numbers")
      case None    => Left(s"$fieldName is required")

  private def numberEntry(fieldName: String)(entry: (String, ujson.Value)): Either[String, (String, Double)] =
    entry match
      case (name, ujson.Num(number)) => Right(name -> number)
      case (name, _)                 => Left(s"$fieldName.$name must be a number")

  def sequence[A](results: List[Either[String, A]]): Either[String, List[A]] =
    results.foldRight(Right(Nil): Either[String, List[A]]) { (result, accumulated) =>
      for
        items <- accumulated
        item <- result
      yield item :: items
    }
