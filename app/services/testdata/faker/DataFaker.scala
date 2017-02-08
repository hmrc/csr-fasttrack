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

package services.testdata.faker

import common.Constants.{ No, Yes }
import model.{ EvaluationResults, Scheme }
import model.EvaluationResults.Result
import model.Exceptions.DataFakingException
import org.joda.time.LocalDate
import play.api.Logger
import repositories._
import services.testdata.faker.DataFaker.ExchangeObjects.AvailableAssessmentSlot

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import model.Scheme.Scheme

//scalastyle:off number.of.methods
object DataFaker {
  object ExchangeObjects {
    case class AvailableAssessmentSlot(venue: AssessmentCentreVenue, date: LocalDate, session: String)
  }

  object Random {
    def randOne[T](options: List[T], cannotBe: List[T] = Nil) = {
      val filtered = options.filterNot(cannotBe.contains)
      if (filtered.isEmpty) {
        throw new DataFakingException(s"There were no items left after filtering.")
      } else {
        util.Random.shuffle(filtered).head
      }
    }

    def upperLetter: Char = randOne(('A' to 'Z').toList)

    def bool: Boolean = randOne(List(true, false))

    def number(limit: Option[Int] = None): Int = util.Random.nextInt(limit.getOrElse(2000000000))

    def randDouble(min: Double, max: Double) = {
      val range = (min to max by 0.1).toList
      randOne(range)
    }

    def randNumber = randOne(List(1,2,3,4,5,6,7,8,9))

    def randList[T](options: List[T], size: Int, cannotBe: List[T] = Nil): List[T] = {
      if (size > 0) {

        val filtered = options.filterNot(cannotBe.contains)
        if (filtered.isEmpty) {
          throw DataFakingException(s"There were no items left after filtering.")
        } else {
          val newItem = util.Random.shuffle(filtered).head
          newItem :: randList(options, size - 1, newItem :: cannotBe)
        }
      } else {
        Nil
      }
    }

    def randomStreetSuffix = randOne(StreetSuffixes.list)

    def addressLine: String = s"$randNumber$randNumber$randNumber ${Random.firstName} $randomStreetSuffix,"

    def passmark: Result = randOne(List(EvaluationResults.Green, EvaluationResults.Amber, EvaluationResults.Red))

    def availableAssessmentVenueAndDate: Future[AvailableAssessmentSlot] = {
      AssessmentCentreYamlRepository.assessmentCentreCapacities.flatMap { assessmentCentreLocations =>

        val randomisedVenues = util.Random.shuffle(assessmentCentreLocations.flatMap(_.venues))

        val firstVenueWithSpace = randomisedVenues.foldLeft(Future.successful(Option.empty[AvailableAssessmentSlot])) {
          case (acc, venue) =>
            acc.flatMap {
              case Some(accVenueAndDate) => Future.successful(Some(accVenueAndDate))
              case _ => venueHasFreeSlots(venue)
            }
        }

        firstVenueWithSpace.map { entry =>
          Logger.warn("=============== FVWS = " + entry)
          entry.get
        }
      }
    }

    private def venueHasFreeSlots(venue: AssessmentCentreVenue): Future[Option[AvailableAssessmentSlot]] = {
      applicationAssessmentRepository.applicationAssessmentsForVenue(venue.venueName).map { assessments =>
        val takenSlotsByDateAndSession = assessments.groupBy(slot => slot.date -> slot.session).map {
          case (date, numOfAssessments) => (date, numOfAssessments.length)
        }
        val assessmentsByDate = venue.capacityDates.map(_.date).toSet
        val availableDate = assessmentsByDate.toList.sortWith(_ isBefore _).flatMap { capacityDate =>
          List("AM", "PM").flatMap { possibleSession =>
            takenSlotsByDateAndSession.get(capacityDate -> possibleSession) match {
              // Date with no free slots
              case Some(slots) if slots >= 18 => None
              // Date with no assessments booked or Date with open slots (all dates have 6 slots per session)
              case _ => Some(AvailableAssessmentSlot(venue, capacityDate, possibleSession))
            }
          }
        }
        availableDate.headOption
      }
    }

    def region: Future[String] = {
      AssessmentCentreYamlRepository.locationsAndAssessmentCentreMapping.map { locationsToAssessmentCentres =>
        val locationToRegion = locationsToAssessmentCentres.values.filterNot(_.startsWith("TestAssessment"))
        randOne(locationToRegion.toList)
      }
    }

    def location(region: String, cannotBe: List[String] = Nil): Future[String] = {
      AssessmentCentreYamlRepository.locationsAndAssessmentCentreMapping.map { locationsToAssessmentCentres =>
        val locationsInRegion = locationsToAssessmentCentres.filter(_._2 == region).keys.toList
        randOne(locationsInRegion, cannotBe)
      }
    }

    def gender = randOne(List("Male", "Female", "Other", "I don't know/prefer not to say"))
    def sexualOrientation = randOne(List("Heterosexual/straight",
      "Gay/lesbian",
      "Bisexual",
      "Other",
      "I don't know/prefer not to say"))
    def ethnicGroup = randOne(List(
      "English/Welsh/Scottish/Northern Irish/British",
      "Irish",
      "Gypsy or Irish Traveller",
      "Other White background",
      "White and Black Caribbean",
      "White and Black African",
      "White and Asian",
      "Other mixed/multiple ethnic background",
      "Indian",
      "Pakistani",
      "Bangladeshi",
      "Chinese",
      "Other Asian background",
      "African",
      "Caribbean",
      "Other Black/African/Caribbean background",
      "Arab",
      "Other ethnic group",
      "I don't know/prefer not to say"
    ))
    def age14to16School = randOne(List("Blue Bees School", "Green Goblins School", "Magenta Monkeys School", "Zany Zebras School"))
    def age16to18School = randOne(List("Advanced Skills School", "Extremely Advanced School", "A-Level Specialist School", "16 to 18 School"))
    def homePostcode = randOne(List("AB1 2CD", "BC11 4DE", "CD6 2EF", "DE2F 1GH", "I don't know/prefer not to say"))
    def yesNo = randOne(List(Yes, No))
    def employeeOrSelf = randOne(List(
      "Employee",
      "Self-employed with employees",
      "Self-employed/freelancer without employees",
      "I don't know/prefer not to say"))

    def sizeOfPlaceOfWork = randOne(List("Small (1 - 24 employees)", "Large (over 24 employees)", "I don't know/prefer not to say"))

    def parentsOccupation = randOne(List(
      "Unemployed but seeking work",
      "Unemployed",
      "Employed",
      "Unknown"
    ))

    def parentsOccupationDetails = randOne(List(
      "Modern professional",
      "Clerical (office work) and intermediate",
      "Senior managers and administrators",
      "Technical and craft",
      "Semi-routine manual and service",
      "Routine manual and service",
      "Middle or junior managers",
      "Traditional professional"
    ))

    def sizeParentsEmployeer = randOne(List(
      "Small (1 to 24 employees)",
      "Large (over 24 employees)",
      "I don't know/prefer not to say"
    ))

    def firstName = randOne(Firstnames.list)

    def getFirstname(userNumber: Int) = {
      s"$firstName$userNumber"
    }

    def getLastname(userNumber: Int) = {
      val lastName = randOne(Lastnames.list)
      s"$lastName$userNumber"
    }

    def yesNoPreferNotToSay = randOne(List("Yes", "No", "I don't know/prefer not to say"))

    def schemes: List[Scheme] = {
      val size = number(Some(5))
      randList(List(Scheme.Business, Scheme.Commercial, Scheme.DigitalAndTechnology,
        Scheme.Finance, Scheme.ProjectDelivery), size)
    }

    // TODO: we should consider the schemes to generate de Scheme locations
    def schemeLocations: List[String] = {
      val size = number(Some(6))
      randList(List("2648579", "2646914", "2651513", "2654142", "2655603", "2657613"), size)
    }

    def hasDisabilityDescription: String = randOne(List("I am too tall", "I am too good", "I get bored easily"))

    def onlineAdjustmentsDescription: String = randOne(List(
      "I am too sensitive to the light from screens",
      "I am allergic to electronic-magnetic waves",
      "I am a convicted cracker who was asked by the court to be away from computers for 5 years"))

    def assessmentCentreAdjustmentDescription: String = randOne(List(
      "I am very weak, I need constant support",
      "I need a comfortable chair because of my back problem",
      "I need to take a rest every 10 minutes"))
    def postCode: String = {
      s"${Random.upperLetter}${Random.upperLetter}1 2${Random.upperLetter}${Random.upperLetter}"
    }

  }
}
//scalastyle:on number.of.methods
