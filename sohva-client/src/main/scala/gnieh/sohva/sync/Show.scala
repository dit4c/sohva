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
package sync

import gnieh.sohva.async.{
  Show => AShow
}

import net.liftweb.json.{
  JValue,
  JObject
}

import spray.httpx.unmarshalling.Unmarshaller

class Show(val wrapped: AShow) extends gnieh.sohva.Show[Identity] {

  def exists: Boolean =
    synced(wrapped.exists)

  def query[T: Unmarshaller](docId: Option[String] = None, format: Option[String] = None): T =
    synced(wrapped.query[T](docId, format))

  override def toString =
    wrapped.toString

}
