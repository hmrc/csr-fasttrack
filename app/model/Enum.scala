/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package model

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader
import play.api.libs.json.{ Format, JsString, JsSuccess, JsValue }
import reactivemongo.bson.{ BSON, BSONHandler, BSONString }

import scala.language.implicitConversions
/*
 * Taken from https://gist.github.com/viktorklang/1057513
 * Credit to viktorlang
 */

trait Enum { //DIY enum type

  import java.util.concurrent.atomic.AtomicReference //Concurrency paranoia

  type EnumVal <: Value //This is a type that needs to be found in the implementing class

  private val _values = new AtomicReference(Vector[EnumVal]()) //Stores our enum values

  //Adds an EnumVal to our storage, uses CCAS to make sure it's thread safe, returns the ordinal
  private final def addEnumVal(newVal: EnumVal): Int = { import _values.{get, compareAndSet => CAS}
    val oldVec = get
    val newVec = oldVec :+ newVal
    if((get eq oldVec) && CAS(oldVec, newVec)) newVec.indexWhere(_ eq newVal) else addEnumVal(newVal)
  }

  def values: Vector[EnumVal] = _values.get //Here you can get all the enums that exist for this type

  //This is the trait that we need to extend our EnumVal type with, it does the book-keeping for us
  protected trait Value { self: EnumVal => //Enforce that no one mixes in Value in a non-EnumVal type
  final val ordinal = addEnumVal(this) //Adds the EnumVal and returns the ordinal

    def name: String //All enum values should have a name

    override def toString = name //And that name is used for the toString operation
    override def equals(other: Any) = this eq other.asInstanceOf[AnyRef]
    override def hashCode = 31 * (this.getClass.## + name.## + ordinal)


  }

  case class NoEnumWithNameException(e: String) extends Exception(s"No Enumeration value found with name $e")

  implicit val enumFormat = new Format[EnumVal] {
    def reads(json: JsValue) = JsSuccess(values.find(_.name == json.as[String].toUpperCase)
      .getOrElse(throw NoEnumWithNameException(json.as[String])))

    def writes(o: EnumVal) = JsString(o.name)
  }

  implicit object BSONEnumHandler extends BSONHandler[BSONString, EnumVal] {
    def read(doc: BSONString) = values.find(_.name == doc.value.toUpperCase) .getOrElse(throw NoEnumWithNameException(doc.value.toUpperCase))
    def write(o: EnumVal) = BSON.write(o.name)
  }

  implicit object configReader extends ValueReader[EnumVal] {
    override def read(config: Config, path: String): EnumVal = {
      val key = config.getString(path)
      values.find(_.name == key) .getOrElse(throw NoEnumWithNameException(key))
    }
  }

  implicit def enumToString(o: EnumVal): String = o.name
  implicit def stringToEnum(s: String): EnumVal = values.find(_.name == s).getOrElse(throw NoEnumWithNameException(s))
}

