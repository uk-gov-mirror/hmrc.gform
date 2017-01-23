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

package uk.gov.hmrc.bforms.services
import play.api.libs.json.JsValue
import uk.gov.hmrc.bforms.repositories.SaveAndRetrieveRepository
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
trait Save[A, B] {
  def apply(a: A, b: B): Future[Either[String, Unit]]
}
object Save {
  private def getTaxFormSave[A, B](f: (A, B) => Future[Either[String, Unit]]): Save[A, B] = {
    new Save[A, B] {
      override def apply(params: A, rn: B): Future[Either[String, Unit]] = f(params, rn)
    }
  }
  implicit def saveData(implicit repository: SaveAndRetrieveRepository): Save[JsValue, String] = {
    getTaxFormSave((r: JsValue, rn: String) => repository.save(r, rn))
  }
}
object SaveService {
  def save(formData: JsValue, registrationNumber: String)(implicit save: Save[JsValue, String]): Future[Either[String, Unit]] = {
    save(formData, registrationNumber).map {
      case Left(x) => Left(x)
      case Right(()) => Right(())
    }
  }
}
trait Retrieve[A, B] {
  def apply(a: A): Future[List[B]]
}
object Retrieve {
  private def retrieveData[A, B](f: A => Future[List[B]]): Retrieve[A, B] = {
    new Retrieve[A, B] {
      def apply(params: A): Future[List[B]] = f(params)
    }
  }
  implicit def retrieveFormData(implicit repository: SaveAndRetrieveRepository): Retrieve[String, JsValue] = {
    retrieveData((f: String) => repository.retrieve(f))
  }
}
object RetrieveService {
  def retrieve[A, B <: JsValue](registrationNumber: A)(implicit retrieve: Retrieve[A, B]): Future[Either[JsValue, Unit]] = {
    retrieve(registrationNumber).map {
      case Nil =>
        Right(())
      case h :: tail =>
        Left(h)
    }
  }
}
