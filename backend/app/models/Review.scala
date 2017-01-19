package models

import org.joda.time.DateTime

case class Review(creationDate: DateTime, favorable: Boolean, comment: String)