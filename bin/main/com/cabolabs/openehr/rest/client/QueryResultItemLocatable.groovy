package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rm_1_0_2.common.archetyped.Locatable

class QueryResultItemLocatable {

   // Locatable uid (object_version_id)
   String type // rm_type_name

   Locatable locatable

   // meta
   String subjectId // only for compositions
   String ehrUid    // only for compositions, folder and ehr_status
   Date timeCreated
}