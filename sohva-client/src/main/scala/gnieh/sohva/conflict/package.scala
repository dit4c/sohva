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

import net.liftweb.json._

package object conflict {

  type Pointer = List[String]

  implicit def s2path(s: String) = List(s)

  implicit def i2path(i: Int) = List(i.toString)

  private[conflict] val allError: PartialFunction[(JValue, String), JValue] = {
    case (value, pointer) =>
      throw new PointerException("Non existent value '" + pointer + "' in " + pp(value))
  }

  val nothingHandler: PartialFunction[(JValue, String), JValue] = {
    case (value, elem) =>
      JNothing
  }

  val pointer = new JsonPointer(nothingHandler)

  private[conflict] def pp(v: JValue) = v match {
    case JNothing => "<nothing>"
    case _        => pretty(render(v))
  }

  def pointerString(path: Pointer): String =
    path.map(_.replace("~", "~0").replace("/", "~1")).mkString("/", "/", "")

}
