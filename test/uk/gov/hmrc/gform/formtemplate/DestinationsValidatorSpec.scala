/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.gform.formtemplate

import cats.data.NonEmptyList
import cats.syntax.eq._
import uk.gov.hmrc.gform.Spec
import uk.gov.hmrc.gform.core.{ Invalid, Valid }
import uk.gov.hmrc.gform.sharedmodel.formtemplate.destinations.Destinations
import uk.gov.hmrc.gform.sharedmodel.formtemplate.generators.PrimitiveGen._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class DestinationsValidatorSpec extends Spec with ScalaCheckDrivenPropertyChecks {
  "validateUniqueDestinationIds" should "return an error when there are duplicate ids" in {
    forAll(destinationIdGen, destinationIdGen) { (id1, id2) =>
      whenever(id1 =!= id2) {
        forAll(
          oneOrMoreGen(destinationWithFixedIdGen(id1)),
          oneOrMoreGen(destinationWithFixedIdGen(id1)),
          oneOrMoreGen(destinationWithFixedIdGen(id2)),
          oneOrMoreGen(destinationWithFixedIdGen(id2)),
          destinationGen.filter(d => d.id =!= id1 && d.id =!= id2)
        ) { (d1WithId1, d2WithId1, d1WithId2, d2WithId2, uniqueD) =>
          whenever(uniqueD.id =!= id1 && uniqueD.id =!= id2) {
            val destinations =
              Destinations
                .DestinationList(uniqueD :: d1WithId1 ::: d2WithId1 ::: d1WithId2 ::: d2WithId2, ackSection, decSection)

            DestinationsValidator.validateUniqueDestinationIds(destinations) should be(
              Invalid(DestinationsValidator.someDestinationIdsAreUsedMoreThanOnce(Set(id1, id2)))
            )
          }
        }
      }
    }
  }

  it should "not return an error when there are no duplicate ids" in {
    forAll(destinationGen, destinationGen) { (d1, d2) =>
      whenever(d1.id != d2.id) {
        DestinationsValidator.validateUniqueDestinationIds(
          Destinations.DestinationList(NonEmptyList.of(d1, d2), ackSection, decSection)
        ) should be(Valid)
      }
    }
  }
}
