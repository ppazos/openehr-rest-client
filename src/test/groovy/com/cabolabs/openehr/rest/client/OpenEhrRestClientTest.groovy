/*
 * This Spock specification was generated by the Gradle 'init' task.
 */
package com.cabolabs.openehr.rest.client

import spock.lang.Specification

import com.cabolabs.openehr.rm_1_0_2.support.identification.GenericId
import com.cabolabs.openehr.rm_1_0_2.support.identification.PartyRef
import com.cabolabs.openehr.rm_1_0_2.common.generic.PartySelf
import com.cabolabs.openehr.rm_1_0_2.ehr.EhrStatus
import com.cabolabs.openehr.rm_1_0_2.data_types.text.DvText


class OpenEhrRestClientTest extends Specification {

   def client

   def setup()
   {
      // read values from config file
      def properties = new Properties()

      this.getClass().getResource(File.separator + 'application.properties').withInputStream {
         properties.load(it)
      }

      //println properties.sut_api_url

      this.client = new OpenEhrRestClient(properties.sut_api_url, properties.sut_api_auth_url)
      this.client.auth("admin@cabolabs.com", "admin") // TODO: set on config file
   }

   def "B.1.a. create ehr no payload"()
   {
      //setup:
      //   def client = new OpenEhrRestClient("http://localhost:8090/openehr/v1", "http://localhost:8090/rest/v1")

      when:
         def ehr = client.createEhr()

      then:
         ehr != null
         ehr.ehr_status != null
   }

   def "B.1.a. create ehr with payload"()
   {
      when:
         def status = new EhrStatus()
         status.name = new DvText(value:"EHR Status")
         status.archetype_node_id = "openEHR-EHR-EHR_STATUS.generic.v1"
         status.is_modifiable = is_modifiable
         status.is_queryable = is_queryable

         if (subject_id)
         {
            status.subject = new PartySelf(
               external_ref: new PartyRef(
                  namespace: "DEMOGRAPHIC",
                  type: "PERSON",
                  id: new GenericId(
                     value: subject_id,
                     scheme: "CABOLABS_MPI"
                  )
               )
            )
         }
         
         def ehr
         
         if (ehr_id)
         {
            ehr = client.createEhr(status, ehr_id) // TODO
         }
         else
         {
            ehr = client.createEhr(status)
         }

         // TODO: add ehr_status.other_details

      then:
         ehr != null
         ehr.ehr_status != null

         // ehr_status is an object_ref
         ehr.ehr_status.id != null
         ehr.ehr_status.type == 'EHR_STATUS'

      where:
         data_set_no | is_queryable | is_modifiable | subject_id | other_details | ehr_id
         1           | true         | true          | '12345'    | null          | null
         2           | true         | false         | '12345'    | null          | null
         3           | false        | true          | '12345'    | null          | null
         4           | false        | false         | '12345'    | null          | null
         9           | true         | true          | '12345'    | null          | '11111'
         10          | true         | false         | '12345'    | null          | '22222'
         11          | false        | true          | '12345'    | null          | '33333'
         12          | false        | false         | '12345'    | null          | '44444'
         17          | true         | true          | null       | null          | null
         18          | true         | false         | null       | null          | null
         19          | false        | true          | null       | null          | null
         20          | false        | false         | null       | null          | null
         25          | true         | true          | null       | null          | '55555'
         26          | true         | false         | null       | null          | '66666'
         27          | false        | true          | null       | null          | '77777'
         28          | false        | false         | null       | null          | '88888'
   }
}
