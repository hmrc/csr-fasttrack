/*
 * Copyright 2019 HM Revenue & Customs
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

package services.onlinetesting

import factories.DateTimeFactory
import model.OnlineTestCommands.OnlineTestApplication
import model.{ ApplicationStatuses, FirstReminder, ProgressStatuses, SecondReminder }
import org.joda.time.DateTime
import repositories._
import repositories.application.{ GeneralApplicationRepository, OnlineTestRepository }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object OnlineTestExtensionService extends OnlineTestExtensionServiceImpl(onlineTestRepository, applicationRepository, DateTimeFactory)

trait OnlineTestExtensionService {
  def extendExpiryTimeForExpiredTests(application: OnlineTestApplication, extraDays: Int): Future[Unit]
  def extendExpiryTime(application: OnlineTestApplication, extraDays: Int): Future[Unit]
}

class OnlineTestExtensionServiceImpl(
    otRepository: OnlineTestRepository,
    appRepository: GeneralApplicationRepository,
    dateTime: DateTimeFactory
) extends OnlineTestExtensionService {

  override def extendExpiryTimeForExpiredTests(application: OnlineTestApplication, extraDays: Int): Future[Unit] = {
    val extendFromStatuses = List(ApplicationStatuses.OnlineTestExpired)

    for {
      _ <- extendTestCommon(
        applicationId = application.applicationId,
        userId = application.userId,
        extraDays = extraDays,
        userWasInExpired = true,
        extendFromStatuses = extendFromStatuses
      )
      _ <- appRepository.updateStatus(application.applicationId, ApplicationStatuses.OnlineTestStarted)
    } yield ()
  }

  private def extendTestCommon(applicationId: String, userId: String, extraDays: Int, userWasInExpired: Boolean,
                               extendFromStatuses: List[ApplicationStatuses.EnumVal]): Future[Unit] = {
    for {
      expiryDate <- getExpiryDate(userId)
      newExpiryDate = calculateNewExpiryDate(expiryDate, extraDays)
      _ <- otRepository.updateExpiryTime(userId, newExpiryDate, extendFromStatuses)
      _ <- progressStatusesToRemove(newExpiryDate, userWasInExpired).fold(NoOp)(p =>
        appRepository.removeProgressStatuses(applicationId, p)
      )
    } yield ()
  }

  override def extendExpiryTime(application: OnlineTestApplication, extraDays: Int): Future[Unit] = {
    val extendFromStatuses = List(ApplicationStatuses.OnlineTestInvited,
      ApplicationStatuses.OnlineTestStarted)

    for {
      _ <- extendTestCommon(
          applicationId = application.applicationId,
          userId = application.userId,
          extraDays = extraDays,
          userWasInExpired = false,
          extendFromStatuses = extendFromStatuses
        )
    } yield ()
  }

  private def getExpiryDate(userId: String): Future[DateTime] =
    otRepository.getCubiksTestProfile(userId).map(_.expirationDate)

  private def calculateNewExpiryDate(expiryDate: DateTime, extraDays: Int): DateTime =
    max(dateTime.nowLocalTimeZone, expiryDate).plusDays(extraDays)

  private def max(dateTime1: DateTime, dateTime2: DateTime): DateTime = {
    implicit val dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
    List(dateTime1, dateTime2).max
  }

  private def progressStatusesToRemove(newExpiryDate: DateTime, userWasInExpired: Boolean): Option[List[ProgressStatuses.ProgressStatus]] = {
    val today = DateTime.now()
    val hasToBeNotified72hBefore = newExpiryDate.minusHours(FirstReminder.hoursBeforeReminder).isAfter(today)
    val hasToBeNotified24hBefore = newExpiryDate.minusHours(SecondReminder.hoursBeforeReminder).isAfter(today)

    val maybeExpiredStatusToRemove = if (userWasInExpired) { List(ProgressStatuses.OnlineTestExpiredProgress) } else { Nil }

    val reminderStatusesToRemove = (hasToBeNotified24hBefore, hasToBeNotified72hBefore) match {
      case (_, true) =>
        Some(ProgressStatuses.OnlineTestFirstExpiryNotification :: ProgressStatuses.OnlineTestSecondExpiryNotification :: Nil)
      case (true, false) =>
        Some(ProgressStatuses.OnlineTestSecondExpiryNotification :: Nil)
      case (false, false) =>
        None
    }

    reminderStatusesToRemove.map(x => x ++ maybeExpiredStatusToRemove).orElse(Some(maybeExpiredStatusToRemove))
  }

  private val NoOp: Future[Unit] = Future.successful(())

}
