package models

import org.joda.time.DateTime

case class Application(id: String, name: String, email: String, status: String, reviews: String, _type: String, address: String, date: DateTime, coordinates: Coordinates, phone: Option[String] = None)