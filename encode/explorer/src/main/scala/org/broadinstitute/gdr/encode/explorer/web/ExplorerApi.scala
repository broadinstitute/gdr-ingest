package org.broadinstitute.gdr.encode.explorer.web

import cats.data.Validated.{Invalid, Valid}
import cats.effect.IO
import io.circe.syntax._
import org.broadinstitute.gdr.encode.explorer.ExplorerApp
import org.broadinstitute.gdr.encode.explorer.export.ExportRequest
import org.broadinstitute.gdr.encode.explorer.fields.FieldFilter
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.io._
import org.http4s.implicits._

class ExplorerApi(app: ExplorerApp) {

  private implicit val filterQueryDecoder: QueryParamDecoder[FieldFilter.Filters] =
    queryParam =>
      FieldFilter
        .parseFilters(queryParam.value.split('|').toList, app.fields)
        .leftMap(_.map(err => ParseFailure(err, err)))

  // Ugly as objects, but http4s has somehow designed their syntax
  // to make it not work as a val.
  private object FilterQueryDecoder
      extends OptionalValidatingQueryParamDecoderMatcher[FieldFilter.Filters]("filter")
  // FIXME: Validate this doesn't include "bad" characters.
  // Only alphanumeric chars, underscore, and hyphen are allowed.
  private object CohortQueryDecoder
      extends OptionalQueryParamDecoderMatcher[String]("cohortName")

  def routes: Http[IO, IO] =
    HttpRoutes
      .of[IO] {
        case GET -> Root / "api" / "dataset" =>
          Ok(app.datasetController.datasetInfo.asJson)

        case GET -> Root / "api" / "facets" =>
          Ok(app.facetsController.getFacets.map(_.asJson))

        case GET -> Root / "api" / "count" :? FilterQueryDecoder(maybeFilters) =>
          maybeFilters match {
            case None                => Ok(app.countController.count(Map.empty).map(_.asJson))
            case Some(Invalid(errs)) => BadRequest(errs.map(_.sanitized).asJson)
            case Some(Valid(filters)) =>
              Ok(app.countController.count(filters).map(_.asJson))
          }

        case GET -> Root / "api" / "export"
              :? FilterQueryDecoder(maybeFilters)
                +& CohortQueryDecoder(maybeCohort) =>
          maybeFilters match {
            case None =>
              Ok(app.exportController.export(ExportRequest(maybeCohort, Map.empty)))
            case Some(Invalid(errs)) =>
              BadRequest(errs.map(_.sanitized).asJson)
            case Some(Valid(filters)) =>
              Ok(app.exportController.export(ExportRequest(maybeCohort, filters)))
          }
      }
      .orNotFound
}
