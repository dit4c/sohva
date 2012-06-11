/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva

import util._

import dispatch._
import dispatch.liftjson.Js._

import scala.util.DynamicVariable

import net.liftweb.json._

/**
 * A CouchDB instance.
 * Allows users to access the different databases and information.
 * This is the key class to start with when one wants to work with couchdb.
 * Through this one you will get access to the databases.
 *
 * @author Lucas Satabin
 *
 */
case class CouchDB(val host: String = "localhost",
                   val port: Int = 5984,
                   private val admin: Option[(String, String)] = None,
                   private val cookie: Option[String] = None) {

  // the base request to this couch instance
  private[sohva] val request = (cookie, admin) match {
    case (Some(c), _) => :/(host, port) <:< Map("Cookie" -> c, "Referer" -> "http://localhost")
    case (_, Some((name, pwd))) => :/(host, port).as_!(name, pwd) <:< Map("Referer" -> "http://localhost")
    case _ => :/(host, port) <:< Map("Referer" -> "http://localhost")
  }

  /** Returns this couchdb instance as the user authenticated by the given session id */
  def as(cookie: String) =
    this.copy(cookie = Some(cookie), admin = None)

  /** Returns this couchdb instance as the user authenticated by the given name and password */
  def as(name: String, password: String) =
    this.copy(admin = Some((name, password)), cookie = None)

  /** Returns the database on the given couch instance */
  def database(name: String) =
    new Database(name, this)

  /** Indicates whether this couchdb instance contains the given database */
  def contains(dbName: String) =
    http(request / "_all_dbs" ># containsName(dbName))()

  // helper methods

  private def containsName(name: String)(json: JValue) =
    json.extract[List[String]].contains(name)

}

/**
 * Gives the user access to the different operations available on a database.
 * Among other operation this is the key class to get access to the documents
 * of this database.
 *
 * @author Lucas Satabin
 */
case class Database(val name: String,
                    private val couch: CouchDB) {

  /** Returns the information about this database */
  def info = http(request ># infoResult)()

  /** Indicates whether this database exists */
  @inline
  def exists_? = couch.contains(name)

  /**
   * Creates this database in the couchdb instance if it does not already exist.
   * Returns <code>true</code> iff the database was actually created.
   */
  def create = if (exists_?) {
    false
  } else {
    http(request.PUT ># simpleResult)() match {
      case OkResult(res) =>
        res
    }
  }

  /**
   * Deletes this database in the couchdb instance if it exists.
   * Returns <code>true</code> iff the database was actually deleted.
   */
  def delete = if (exists_?) {
    http(request.DELETE ># simpleResult)() match {
      case OkResult(res) =>
        res
    }
  } else {
    false
  }

  /** Returns the document identified by the given id if it exists */
  def getDocById[T: Manifest](id: String): Option[T] =
    http((request / id) ># docResult[T])()

  /**
   * Creates or updates the given object as a document into this database
   * The given object must have an `_id' and an optional `_rev' fields
   * to conform to the couchdb document structure.
   */
  def saveDoc[T: Manifest](doc: T with Doc) =
    http((request / doc._id <<< compact(render(Extraction.decompose(doc)))) ># docUpdateResult)() match {
      case DocUpdate(true, id, _) =>
        getDocById[T](id)
      case DocUpdate(false, _, _) =>
        None
    }

  /** Deletes the document from the database */
  def deleteDoc[T: Manifest](doc: T with Doc) = {
    http((request / doc._id).DELETE <<? Map("rev" -> doc._rev.getOrElse("")) ># simpleResult)() match {
      case OkResult(ok) => ok
    }
  }

  /** Returns the security document of this database if any defined */
  def securityDoc =
    http(request / "_security" ># SecurityDoc)()

  /**
   * Creates or updates the security document.
   * Security documents are special documents with no `_id' nor `_rev' fields.
   */
  def saveSecurityDoc(doc: SecurityDoc) = {
    http((request / "_security" <<< compact(render(Extraction.decompose(doc)))) ># docUpdateResult)() match {
      case DocUpdate(true, id, _) =>
        getDocById[SecurityDoc](id)
      case DocUpdate(false, _, _) =>
        None
    }
  }

  /** Returns a design object that allows user to work with views */
  def design(designName: String) =
    Design(this, designName)

  // helper methods

  private[sohva] val request =
    couch.request / name

  private def infoResult(json: JValue) =
    json.extractOpt[InfoResult]

  private def simpleResult(json: JValue) =
    json.extract[OkResult]

  private def docResult[T: Manifest](json: JValue) =
    json.extractOpt[T]

  private def docUpdateResult(json: JValue) =
    json.extract[DocUpdate]

}

/**
 * A security document is a special document for couchdb. It has no `_id' or
 * `_rev' field.
 *
 * @author Lucas Satabin
 */
case class SecurityDoc(admins: SecurityList, readers: SecurityList)
object SecurityDoc extends (JValue => Option[SecurityDoc]) {
  def apply(json: JValue) = json.extractOpt[SecurityDoc]
}
case class SecurityList(names: List[String], roles: List[String])

/**
 * A design gives access to the different views.
 * Use this class to get or create new views.
 *
 * @author Lucas Satabin
 */
case class Design(db: Database, val name: String) {

  private[sohva] lazy val request = db.request / "_design" / name

  /** Adds or update the view with the given name, map function and reduce function */
  def updateView(viewName: String, mapFun: String, reduceFun: Option[String]) = {

    http(request ># designDoc) {
      case (404, _) => None
    } match {
      case Some(design) =>
        val view = ViewDoc(mapFun, reduceFun)
        // the updated design
        val newDesign = design.copy(views = design.views + (viewName -> view))
        db.saveDoc(newDesign).isDefined
      case None =>
        // this is not a design document or it does not exist...
        false
    }

  }

  /**
   * Returns the (typed) view in this design document.
   * The different types are:
   *  - Key: type of the key for this view
   *  - Value: Type of the value returned in the result
   *  - Doc: Type of the full document in the case where the view is queried with `include_docs` set to `true`
   */
  def view[Key: Manifest, Value: Manifest, Doc: Manifest](viewName: String) =
    View[Key, Value, Doc](this, viewName)

  // helper methods

  private def designDoc(json: JValue) =
    json.extractOpt[DesignDoc]

}

/**
 * A view can be queried to get the result.
 *
 * @author Lucas Satabin
 */
case class View[Key: Manifest, Value: Manifest, Doc: Manifest](design: Design,
                                                               view: String) {

  private lazy val request = design.request / "_view" / view

  /**
   * Queries the view on the server and returned the typed result.
   * BE CAREFUL: If the types given to the constructor are not correct,
   * strange things may happen! By 'strange', I mean exceptions
   */
  def query(key: Option[Key] = None,
            keys: List[Key] = Nil,
            startkey: Option[Key] = None,
            startkey_docid: Option[String] = None,
            endkey: Option[Key] = None,
            endkey_docid: Option[String] = None,
            limit: Int = -1,
            stale: Option[String] = None,
            descending: Boolean = false,
            skip: Int = 0,
            group: Boolean = false,
            group_level: Int = -1,
            reduce: Boolean = true,
            include_docs: Boolean = false,
            inclusive_end: Boolean = true,
            update_seq: Boolean = false) = {

    def toJsonString(a: Any) =
      compact(render(Extraction.decompose(a)))

    // build options
    val options = List(
      key.map(k => "key" -> toJsonString(k)),
      if (keys.nonEmpty) Some("keys" -> toJsonString(keys)) else None,
      startkey.map(k => "startkey" -> toJsonString(k)),
      startkey_docid.map("startkey_docid" -> _),
      endkey.map(k => "endkey" -> toJsonString(k)),
      endkey_docid.map("endkey_docid" -> _),
      if (limit > 0) Some("limit" -> limit) else None,
      stale.map("stale" -> _),
      if (descending) Some("descending" -> true) else None,
      if (skip > 0) Some("skip" -> skip) else None,
      if (group) Some("group" -> true) else None,
      if (group_level >= 0) Some("group_level" -> group_level) else None,
      if (reduce) None else Some("reduce" -> false),
      if (include_docs) Some("include_docs" -> true) else None,
      if (inclusive_end) None else Some("inclusive_end" -> false),
      if (update_seq) Some("update_seq" -> true) else None)
      .flatten
      .filter(_ != null) // just in case somebody gave Some(null)...
      .map {
        case (name, value) => (name, value.toString)
      }

    http(request <<? options ># viewResult)()

  }

  // helper methods

  private def viewResult(json: JValue) =
    json.extract[ViewResult[Key, Value, Doc]]

}

// the different object that may be returned by the couchdb server

private[sohva] case class DesignDoc(_id: String,
                                    _rev: Option[String],
                                    language: String,
                                    views: Map[String, ViewDoc])

private[sohva] case class ViewDoc(map: String,
                                  reduce: Option[String])

final case class OkResult(ok: Boolean)
object OkResult extends (JValue => OkResult) {
  def apply(json: JValue) = json.extract[OkResult]
}

final case class InfoResult(compact_running: String,
                            db_name: String,
                            disk_format_version: Int,
                            disk_size: Int,
                            doc_count: Int,
                            doc_del_count: Int,
                            instance_start_time: Long,
                            purge_seq: Int,
                            update_seq: Int)
object InfoResult {
  def apply(json: JValue) = json.extract[InfoResult]
}

final case class DocUpdate(ok: Boolean,
                           id: String,
                           rev: String)
object DocUpdate {
  def apply(json: JValue) = json.extract[DocUpdate]
}

final case class ViewResult[Key, Value, Doc](total_rows: Option[Int],
                                             offset: Option[Int],
                                             rows: List[Row[Key, Value, Doc]])
object ViewResult {
  def apply[Key: Manifest, Value: Manifest, Doc: Manifest](json: JValue) =
    json.extract[ViewResult[Key, Value, Doc]]
}

final case class Row[Key, Value, Doc](id: String,
                                      key: Key,
                                      value: Value,
                                      doc: Option[Doc])
object Row {
  def apply[Key: Manifest, Value: Manifest, Doc: Manifest](json: JValue) =
    json.extract[Row[Key, Value, Doc]]
}

final case class ErrorResult(error: String, reason: String)
object ErrorResult {
  def apply(json: JValue) = json.extract[ErrorResult]
}

case class CouchException(val status: Int, val error: String, val reason: String)
  extends Exception("status: " + status + "\nerror: " + error + "\nbecause: " + reason)
