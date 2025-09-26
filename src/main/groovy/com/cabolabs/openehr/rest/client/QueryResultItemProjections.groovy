package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rm_1_0_2.common.archetyped.Locatable

class QueryResultItemProjections {

   List projections // QueryResultProjection

   // meta
   String type      // rm type of the container/owner of the dvs
   String ownerUid  // container object uid
   String subjectId // only for EHR classes
   String ehrUid    // only for EHR classes
}