package com.cabolabs.openehr.rest.client

class QueryResultItem {

   // Locatable uid (object_version_id)
   String uid
   String type // rm_type_name

   // composition result summary
   String category
   Date startTime
   String subjectId
   String ehrUid

   // party_relationship
   String source
   String target

   // role
   String performer

   // any locatable summary
   String templateId
   String archetypeId

   boolean lastVersion
   Date timeCommitted
   Date timeCreated
}