package models

import org.joda.time.DateTime

case class Application(id: String,
                       name: String,
                       email: String,
                       `type`: String,
                       address: String,
                       creationDate: DateTime,
                       coordinates: Coordinates,
                       phone: Option[String] = None,
                       fields: Map[String, String] = Map(),
                       files: List[String] = List())