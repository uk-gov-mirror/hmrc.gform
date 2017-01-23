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

package uk.gov.hmrc.bforms.repositories
import com.fasterxml.jackson.annotation.JsonValue
import play.api.libs.json._
import reactivemongo.api.DefaultDB
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.ReactiveRepository
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
class SaveAndRetrieveRepositoryImpl(implicit mongo: () => DefaultDB)
    extends ReactiveRepository[JsValue, BSONObjectID]("Save_And_Retrieve", mongo, Format.GenericFormat[JsValue])
    with SaveAndRetrieveRepository {
  private def exists(registrationNumber: String): Future[Boolean] = {
    find("fields.id" -> "registrationNumber", "fields.value" -> registrationNumber).map {
      case Nil => true
      case x => false
    }
  }
  def save(form: JsValue, registrationNumber: String): Future[Either[String, Unit]] = {
    exists(registrationNumber).flatMap {
      case true => insertForm(form)
      case false => updateForm(form, registrationNumber)
    }
  }
  private def insertForm(form: JsValue): Future[Either[String, Unit]] = {
    insert(form).map {
      case x if x.ok =>
        Right(())
      case x =>
        Left(x.message)
    }
  }
  private def updateForm(form: JsValue, registrationNumber: String): Future[Either[String, Unit]] = {
    remove("fields.id" -> "registrationNumber", "fields.value" -> registrationNumber)
    insertForm(form)
  }
  def retrieve(registrationNumber: String): Future[List[JsValue]] = {
    find("fields.id" -> "registrationNumber", "fields.value" -> registrationNumber)
  }
}

trait SaveAndRetrieveRepository {

  def save(form: JsValue, registrationNumber: String): Future[Either[String, Unit]]

  def retrieve(registrationNumber: String): Future[List[JsValue]]

}
