package models

case class Agent(id: String,
                 name: String,
                 qualite: String,
                 email: String,
                 admin: Boolean,
                 instructor: Boolean,
                 finalReview: Boolean)

