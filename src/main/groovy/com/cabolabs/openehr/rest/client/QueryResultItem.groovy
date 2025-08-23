package com.cabolabs.openehr.rest.client

class QueryResultItem {

   // Locatable uid (object_version_id)
   String uid

   // composition result summary
   String category
   Date startTime
   String subjectId
   String ehrUid

   // any locatable summary
   String templateId
   String archetypeId

   boolean lastVersion
   Date timeCommitted
   Date timeCreated
}