/*
 * Copyright 2018 HM Revenue & Customs
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

package config

import common.Constants.{ No, Yes }
import org.scalatest.mock.MockitoSugar
import play.api.Play
import services.AuditService

import scala.util.Random

abstract class TestFixtureBase extends MockitoSugar {
  val mockAuditService: AuditService = mock[AuditService]
  val unit: Unit = ()

  def rnd(prefix: String) = s"$prefix${Random.nextInt(100)}"
  def maybe[A](value: => A): Option[A] = if (Random.nextBoolean()) Some(value) else None
  def maybeRnd(prefix: String): Option[String] = maybe(rnd(prefix))
  def someRnd(prefix: String) = Some(rnd(prefix))
  def yesNoRnd: Some[String] = if (Random.nextBoolean()) Some(Yes) else Some(No)
}
