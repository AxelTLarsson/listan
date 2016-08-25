package services

import akka.actor._
import akka.actor.FSM.Event
import play.api.libs.json.{Json, JsValue, JsError, JsSuccess}
import play.Logger
import scala.concurrent.duration._
import javax.inject._
import com.google.inject.assistedinject.Assisted
import scala.util.{Success}
import play.api.Configuration

// States in the FSM
sealed trait State
case object Unauthenticated extends State
case object Authenticated extends State

// State data
sealed trait Data
case object Uninitialized extends Data

class WebSocketActor @Inject() (configuration: Configuration, @Assisted ws: ActorRef)
  extends LoggingFSM[State, Data] {

  startWith(Unauthenticated, Uninitialized)
  ws ! Json.toJson(AuthRequest(): Message)

  def valid(token: String): Boolean = {
    import pdi.jwt.{JwtJson, JwtAlgorithm}
    // val key = configuration.underlying.getString("play.crypto.secret")
    val key = configuration.getString("play.crypto.secret").get
    JwtJson.decodeJson(token, key, Seq(JwtAlgorithm.HS256)) match {
      case Success(json) => {
        Logger.info("token decoded as " + json)
        true
      }
      case _ => {
        Logger.error("could not decode token")
        false
      }
    }
  }

  when(Unauthenticated) {
    case Event(json: JsValue, _) => {
      json.validate[Message] match {
        case s: JsSuccess[Message] => {
          s.get match {
            case Auth(token) => {
              if (valid(token)) {
                ws ! Json.toJson(Response(true, "Authentication success"))
                goto(Authenticated)
              } else {
                stay // or die?
              }
            }
            case _ => {
              val msg = "Invalid message at this state (Unauthenticated)"
              Logger.warn(msg)
              ws ! Json.toJson(Response(false, msg))
              stay // or die?
            }
          }
        }
        case e: JsError => {
          val msg = s"Could not validate json ($json) as Message"
          Logger.error(msg)
          ws ! Json.toJson(Response(false, msg))
          stay // or die?
        }

      }
    }
  }

  onTransition {
    case Unauthenticated -> Authenticated =>
      stateData match {
        case _ => // nothing to do
      }
  }

  when(Authenticated) {
    case Event(json: JsValue, _) =>
      json.validate[Message] match {
        case s: JsSuccess[Message] => {
          s.get match {
            case ADD_ITEM(id, contents) => {
              ws ! Json.toJson(Response(true, "added item"))
              stay
            }
            case EDIT_ITEM(id, contents) => {
              ws ! Json.toJson(Response(true, "edited item"))
              stay
            }
            case _ => {
              Logger.info("Unknown message")
              ws ! Json.toJson(Response(false, "Unknown message"))
              stay
            }
          }
        }
        case e: JsError => {
          Logger.error("Could not validate json as Message")
          ws ! Json.toJson(Response(false, "Invalid message"))
          stay
        }
      }
  }

  override def postStop() = {
    Logger.info("WS closed")
  }
}

object WebSocketActor {
  // def props(ws: ActorRef) = Props(new WebSocketActor(ws))
  trait Factory {
    def apply(ws: ActorRef): Actor
  }
}

