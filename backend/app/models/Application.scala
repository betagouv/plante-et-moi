package models

import org.joda.time.DateTime

case class Application(id: String,
                       city: String,
                       status: String,
                       firstname: String,
                       lastname: String,
                       email: String,
                       _type: String,
                       address: String,
                       creationDate: DateTime,
                       coordinates: Coordinates,
                       phone: Option[String] = None,
                       fields: Map[String, String] = Map(),
                       files: List[String] = List()) {
   val name = s"${firstname.capitalize} ${lastname.capitalize}"
}