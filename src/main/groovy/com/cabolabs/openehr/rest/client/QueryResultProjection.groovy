package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rm_1_0_2.common.archetyped.Locatable
import com.cabolabs.openehr.rm_1_0_2.data_types.basic.DataValue

class QueryResultProjection {

    String type // dv

    // TODO: we can have the archetype and path here

    DataValue value
}