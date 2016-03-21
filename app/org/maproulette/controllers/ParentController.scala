package org.maproulette.controllers

import org.maproulette.models.BaseObject
import org.maproulette.models.dal.ParentDAL
import org.maproulette.exception.MPExceptionUtil
import org.maproulette.session.{User, SessionManager}
import org.maproulette.utils.Utils
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{BodyParsers, Action}

/**
  * Base controller for parent objects, namely Projects and Challenges. This controller helps in
  * building the children object of the parent. The CRUDController handles all the basic operations
  * of the object
  *
  * @author cuthbertm
  */
trait ParentController[T<:BaseObject[Long], C<:BaseObject[Long]] extends CRUDController[T] {
  // The data access layer for the parent
  override protected val dal: ParentDAL[Long, T, C]
  // The CRUD controller of the child, in the case of a Challenge this is technically a ParentController
  protected val childController:CRUDController[C]
  // reads function for the json in the post body to the parent object
  override implicit val tReads: Reads[T]
  // writes function for the parent object to json
  override implicit val tWrites: Writes[T]
  // reads function for the json in the post body to the child object
  protected val cReads:Reads[C]
  // writes function for the child object to json
  protected val cWrites:Writes[C]

  /**
    * Function can be implemented to extract more information than just the default create data,
    * to build other objects with the current object at the core. No data will be returned from this
    * function, it purely does work in the background AFTER creating the current object
    *
    * @param body          The Json body of data
    * @param createdObject The object that was created by the create function
    * @param user the user executing the request
    */
  override def extractAndCreate(body: JsValue, createdObject: T, user:User): Unit = {
    implicit val reads:Reads[C] = cReads
    (body \ "children").asOpt[List[JsValue]] match {
      case Some(children) => children map { child =>
        // add the parent id to the child.
        child.transform(parentAddition(createdObject.id)) match {
          case JsSuccess(value, _) =>
            (value \ "id").asOpt[Long] match {
              case Some(identifier) => childController.internalUpdate(value, user)(identifier)
              case None => Utils.insertJsonID(value).validate[C].fold(
                errors => {
                  throw new Exception(JsError.toJson(errors).toString)
                },
                element => {
                  try {
                    childController.internalCreate(value, element, user)
                  } catch {
                    case e:Exception =>
                      Logger.error(e.getMessage, e)
                      throw e
                  }
                }
              )
            }
          case JsError(errors) =>
            Logger.error(JsError.toJson(errors).toString)
            throw new Exception(JsError.toJson(errors).toString)
        }
      }
      case None => // ignore
    }
  }

  /**
    * Json transformer that will add the parent id into all the child objects that are being
    * created at the same time. It will also overwrite any parent id's that are already there. The
    * parent is defined by the json structure.
    *
    * @param id The id of the parent
    * @return
    */
  def parentAddition(id:Long) = {
    __.json.update(
      __.read[JsObject] map { o => o ++ Json.obj("parent" -> Json.toJson(id)) }
    )
  }

  /**
    * Adds the child json array to the parent object
    *
    * @param children The list of children objects to add
    * @return
    */
  def childrenAddition(children:List[C]) = {
    implicit val writes:Writes[C] = cWrites
    __.json.update(
      __.read[JsObject] map { o => o ++ Json.obj("children" -> Json.toJson(children)) }
    )
  }

  /**
    * This function is very similar to the batch upload, however it implies a object hierarchy by
    * forcing the top level object to be defined. The entire object does not have to be defined if
    * it has already been created.
    * Must be authenticated to perform operation
    *
    * @param id The id of the parent
    * @return 201 Created with no content
    */
  def createChildren(implicit id:Long) = Action.async(BodyParsers.parse.json) { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      dal.retrieveById match {
        case Some(parent) =>
          extractAndCreate(Json.obj("children" -> request.body), parent, user)
          Created
        case None =>
          val message = s"Bad id, no parent found with supplied id [$id]"
          Logger.error(message)
          BadRequest(Json.obj("status" -> "KO", "message" -> message))
      }
    }
  }

  /**
    * Passes off all the work to the createChildren function as that function understands how to
    * update objects as well.
    * Must be authenticated to perform operation
    *
    * @param id The id of the parent
    * @return 201 Created with no content
    */
  def updateChildren(implicit id:Long) = createChildren

  /**
    * Lists all the children of a given parent. This could be very costly, if you are listing all
    * the children of a task with no limit.
    *
    * @param id The parent id
    * @param limit The limit of how many objects to be returned
    * @param offset For paging
    * @return 200 OK with json array of children objects
    */
  def listChildren(id:Long, limit:Int, offset:Int) = Action.async { implicit request =>
    implicit val writes:Writes[C] = cWrites
    sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(dal.listChildren(limit, offset)(id)))
    }
  }

  /**
    * This function will list all the children and then place it in a "children" key under the
    * parent object. Ie. return the parent and it's children. The primary workload is completed
    * by the listChildren function, with this function really just retrieving the information of the
    * parent
    *
    * @param id The parent id
    * @param limit The limit of how many objects to be returned
    * @param offset For paging
    * @return 200 Ok with parent json object containing children objects
    */
  def expandedList(id:Long, limit:Int, offset:Int) = Action.async { implicit request =>
    implicit val writes:Writes[C] = cWrites
    sessionManager.userAwareRequest { implicit user =>
      // first get the parent
      val parent = Json.toJson(dal.retrieveById(id))
      // now list the children
      val children = dal.listChildren(limit, offset)(id)
      // now replace the parent field in the parent with a children array
      parent.transform(childrenAddition(children)) match {
        case JsSuccess(value, _) => Ok(value)
        case JsError(errors) =>
          Logger.error(JsError.toJson(errors).toString)
          InternalServerError(Json.obj("status" -> "KO", "message" -> JsError.toJson(errors)))
      }
    }
  }
}