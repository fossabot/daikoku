package fr.maif.otoroshi.daikoku.ctrls

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.util.FastFuture
import fr.maif.otoroshi.daikoku.actions.{DaikokuAction, DaikokuActionContext}
import fr.maif.otoroshi.daikoku.audit.AuditTrailEvent
import fr.maif.otoroshi.daikoku.ctrls.authorizations.async._
import fr.maif.otoroshi.daikoku.domain._
import fr.maif.otoroshi.daikoku.env.Env
import fr.maif.otoroshi.daikoku.utils.{ApiService, IdGenerator, OtoroshiClient}
import org.joda.time.DateTime
import play.api.libs.json.{JsArray, JsError, JsSuccess, Json}
import play.api.mvc.{AbstractController, ControllerComponents}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.duration.FiniteDuration

class UsersController(DaikokuAction: DaikokuAction,
                      apiService: ApiService,
                      env: Env,
                      otoroshiClient: OtoroshiClient,
                      cc: ControllerComponents)
    extends AbstractController(cc) {

  implicit val ec = env.defaultExecutionContext
  implicit val ev = env

  def allTenantUsers() = DaikokuAction.async { ctx =>
    DaikokuAdminOnly(
      AuditTrailEvent("@{user.name} has accessed all users list"))(ctx) {
      env.dataStore.userRepo.findAllNotDeleted().map { users =>
        Ok(JsArray(users.map(_.asJson)))
      }
    }
  }

  def findUserById(id: String) = DaikokuAction.async { ctx =>
    DaikokuAdminOnly(AuditTrailEvent(
      "@{user.name} has accessed user profile of @{u.email} (@{u.id})"))(ctx) {
      env.dataStore.userRepo.findByIdOrHrId(id).map {
        case Some(user) =>
          ctx.setCtxValue("u.email", user.email)
          ctx.setCtxValue("u.id", user.id.value)
          Ok(user.asJson)
        case None => NotFound(Json.obj("error" -> "user not found"))
      }
    }
  }

  def updateUserById(id: String) = DaikokuAction.async(parse.json) { ctx =>
    (ctx.request.body \ "_id").asOpt[String].map(UserId.apply) match {
      case Some(userId) =>
        DaikokuAdminOrSelf(
          AuditTrailEvent(
            "@{user.name} has updated user profile of @{u.email} (@{u.id})"))(
          userId,
          ctx) {
          json.UserFormat.reads(ctx.request.body) match {
            case JsSuccess(newUser, _) => {
              env.dataStore.userRepo.findByIdNotDeleted(id).flatMap {
                case Some(user) =>
                  ctx.setCtxValue("u.email", user.email)
                  ctx.setCtxValue("u.id", user.id.value)
                  val userToSave =
                    if (ctx.user.isDaikokuAdmin) newUser
                    else newUser.copy(metadata = user.metadata)
                  env.dataStore.userRepo.save(userToSave).map { _ =>
                    Ok(userToSave.asJson)
                  }
                case None =>
                  FastFuture.successful(
                    NotFound(Json.obj("error" -> "user not found")))
              }
            }
            case e: JsError => {
              FastFuture.successful(BadRequest(JsError.toJson(e)))
            }
          }
        }
      case None =>
        FastFuture.successful(
          Unauthorized(Json.obj("error" -> "You're not a Daikoku admin")))
    }
  }

  def deleteUserById(id: String) = DaikokuAction.async { ctx =>
    DaikokuAdminOnly(
      AuditTrailEvent(
        "@{user.name} has deleted user profile of @{u.email} (@{u.id})"))(ctx) {
      env.dataStore.userRepo.findByIdNotDeleted(id).flatMap {
        case Some(user) =>
          ctx.setCtxValue("u.email", user.email)
          ctx.setCtxValue("u.id", user.id.value)
          env.dataStore.userRepo.save(user.copy(deleted = true)).flatMap { _ =>
            env.dataStore.userSessionRepo
              .delete(Json.obj(
                "userId" -> user.id.value
              ))
              .map { _ =>
                Ok(user.asJson)
              }
          }
        case None =>
          FastFuture.successful(NotFound(Json.obj("error" -> "user not found")))
      }
    }
  }

  def deleteSelfUser() = DaikokuAction.async { ctx =>
    PublicUserAccess(
      AuditTrailEvent("@{user.name} has deleted his own profile)"))(ctx) {
      env.dataStore.userRepo.save(ctx.user.copy(deleted = true)).flatMap { _ =>
        env.dataStore.userSessionRepo
          .delete(
            Json.obj(
              "userId" -> ctx.user.id.value
            ))
          .map { _ =>
            Ok(ctx.user.asJson)
          }
      }
    }
  }

  def createUser() = DaikokuAction.async(parse.json) { ctx =>
    DaikokuAdminOnly(
      AuditTrailEvent(
        "@{user.name} has created user profile of @{u.email} (@{u.id})"))(ctx) {
      json.UserFormat.reads(ctx.request.body) match {
        case JsSuccess(newUser, _) =>
          ctx.setCtxValue("u.email", newUser.email)
          ctx.setCtxValue("u.id", newUser.id.value)
          env.dataStore.userRepo.findByIdNotDeleted(newUser.id).flatMap {
            case Some(_) =>
              FastFuture.successful(
                Conflict(Json.obj("error" -> "User id already exists")))
            case None =>
              env.dataStore.userRepo.save(newUser).map { _ =>
                Created(newUser.asJson)
              }
          }
        case e: JsError => FastFuture.successful(BadRequest(JsError.toJson(e)))
      }
    }
  }

  def impersonate(userId: String) = DaikokuAction.async { ctx =>
    DaikokuAdminOnly(
      AuditTrailEvent(
        "@{user.name} has impersonated user profile of @{u.email} (@{u.id})"))(
      ctx) {
      env.dataStore.userRepo.findByIdNotDeleted(userId).flatMap {
        case Some(user) => {
          val session = UserSession(
            id = MongoId(BSONObjectID.generate().stringify),
            userId = user.id,
            userName = user.name,
            userEmail = user.email,
            impersonatorId = Some(ctx.user.id),
            impersonatorName = Some(ctx.user.name),
            impersonatorEmail = Some(ctx.user.email),
            impersonatorSessionId = Some(ctx.session.sessionId),
            sessionId = UserSessionId(IdGenerator.token),
            created = DateTime.now(),
            expires = DateTime.now().plusSeconds(3600),
            ttl = FiniteDuration(3600, TimeUnit.SECONDS)
          )
          env.dataStore.userSessionRepo.save(session).map { _ =>
            Redirect(ctx.request.session.get("redirect").getOrElse("/"))
              .removingFromSession("sessionId", "redirect")(ctx.request)
              .withSession(
                "sessionId" -> session.sessionId.value
              )
          }
        }
        case None =>
          FastFuture.successful(
            BadRequest(Json.obj("error" -> "User not found")))
      }
    }
  }

  def deImpersonate() = DaikokuAction.async { ctx =>
    DaikokuImpersonatorAdminOnly(AuditTrailEvent(
      "@{user.name} (@{user.id}) is leaving impersonation of user profile @{u.email} (@{u.id})"))(
      ctx) {
      ctx.session.impersonatorSessionId match {
        case None => FastFuture.successful(Redirect("/logout"))
        case Some(sessionId) => {
          env.dataStore.userSessionRepo
            .findOne(Json.obj("sessionId" -> sessionId.value))
            .flatMap {
              case Some(session) if session.expires.isAfter(DateTime.now()) => {
                env.dataStore.userSessionRepo.save(session).map { _ =>
                  Redirect(ctx.request.session.get("redirect").getOrElse("/"))
                    .removingFromSession("sessionId", "redirect")(ctx.request)
                    .withSession(
                      "sessionId" -> session.sessionId.value
                    )
                }
              }
              case None => FastFuture.successful(Redirect("/logout"))
            }
        }
      }
    }
  }
}
