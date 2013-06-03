/*
* This file is part of the sohva project.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*couch.http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package gnieh.sohva

/** The configuration object of a couchdb instance
 *
 *  @author Lucas Satabin
 */
case class Configuration(sections: Map[String, Map[String, String]]) {

  def query_server_config: Map[String, String] =
    sections("query_server_config")

  def couchdb: Map[String, String] =
    sections("couchdb")

  def attachments: Map[String, String] =
    sections("attachments")

  def uuids: Map[String, String] =
    sections("uuids")

  def daemons: Map[String, String] =
    sections("daemons")

  def stats: Map[String, String] =
    sections("stats")

  def httpd: Map[String, String] =
    sections("httpd")

  def query_servers: Map[String, String] =
    sections("query_servers")

  def couch_httpd_auth: Map[String, String] =
    sections("couch_httpd_auth")

  def httpd_db_handlers: Map[String, String] =
    sections("httpd_db_handlers")

  def replicator: Map[String, String] =
    sections("replicator")

  def log: Map[String, String] =
    sections("log")

  def httpd_design_handlers: Map[String, String] =
    sections("httpd_design_handlers")

  def httpd_global_handlers: Map[String, String] =
    sections("httpd_global_handlers")

  def section(name: String): Map[String, String] =
    sections(name)

}
