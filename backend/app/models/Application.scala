package models

case class Application(id: String, name: String, email: String, status: String, reviews: String, _type: String, address: String, date: String, phone: Option[String] = None)