package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rm_1_0_2.common.archetyped.Locatable
import com.cabolabs.openehr.rm_1_0_2.data_types.basic.DataValue

class QueryResultProjection {

    String type // dv

    // NOTE: all these could be in a headers structure instead of inside each
    // individual datatype, though the issue is if we miss some types of values
    // from some rows, maybe because of null_favours.

    // TODO: we can have the archetype and path here
    // We also need the name because we don't know what the data represents

    Object value // We need to set Object because this can be DataValue or String which is not a DV.

    String archetypeId
    String path
    String dataPath // helps grouping individual nodes into RM subtrees
}