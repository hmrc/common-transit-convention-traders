/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.transitmovements.generators

import cats.data.NonEmptyList
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen.choose
import org.scalacheck.Gen.listOfN
import org.scalacheck.Gen.numChar
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

trait BaseGenerators {

  def listWithMaxLength[A](maxLength: Int)(implicit a: Arbitrary[A]): Gen[List[A]] =
    for {
      length <- choose(1, maxLength)
      seq    <- listOfN(length, arbitrary[A])
    } yield seq

  def nonEmptyListOfMaxLength[A: Arbitrary](maxLength: Int): Gen[NonEmptyList[A]] =
    listWithMaxLength(maxLength).map(NonEmptyList.fromListUnsafe)

  def intWithMaxLength(maxLength: Int): Gen[Int] =
    for {
      length        <- choose(1, maxLength)
      listOfCharNum <- listOfN(length, numChar)
    } yield listOfCharNum.mkString.toInt

}
