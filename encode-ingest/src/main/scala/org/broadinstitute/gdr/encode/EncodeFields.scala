package org.broadinstitute.gdr.encode

/** Bucket for field names used while transforming raw metadata pulled from ENCODE. */
object EncodeFields {

  val EncodeIdField = "@id"
  val EncodeAuditField = "audit"

  def joinedName(fieldName: String, joinedPrefix: String): String =
    s"${joinedPrefix}__$fieldName"
}
