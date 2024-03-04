/*
 * This Spock specification was generated by the Gradle 'init' task.
 */
package com.cabolabs.openehr.rest.client

import spock.lang.Specification
import spock.lang.Unroll

import com.cabolabs.openehr.rm_1_0_2.support.identification.*
import com.cabolabs.openehr.rm_1_0_2.ehr.EhrStatus
import com.cabolabs.openehr.rm_1_0_2.data_types.text.*
import com.cabolabs.openehr.rm_1_0_2.data_structures.item_structure.ItemTree
import com.cabolabs.openehr.rm_1_0_2.data_structures.item_structure.representation.Element
import com.cabolabs.openehr.rm_1_0_2.common.archetyped.*
import com.cabolabs.openehr.rm_1_0_2.common.generic.PartySelf

import com.cabolabs.openehr.formats.OpenEhrJsonParserQuick
import com.cabolabs.openehr.rest.client.auth.*

import groovy.json.JsonSlurper

import java.time.*

class OpenEhrRestClientTest extends Specification {

   static def client

   static Random rand = new Random()

   static Properties properties


   def setupSpec()
   {
      // read values from config file
      properties = new Properties()
      this.getClass().getResource('/application.properties').withInputStream {
         properties.load(it)
      }


      // Authentication factory
      def authType = AuthTypeEnum.fromString(getProperty("api_auth"))
      def auth
      switch (authType)
      {
         case AuthTypeEnum.BASIC:

            def user = getProperty("api_username")
            def pass = getProperty("api_password")

            if (!user)
            {
               throw new Exception("api_username is not set and it's required when api_auth='basic'")
            }

            if (!pass)
            {
               throw new Exception("api_password is not set and it's required when api_auth='basic'")
            }

            auth = new BasicAuth(user, pass)
         break
         case AuthTypeEnum.TOKEN:

            def token = getProperty("api_access_token")
            if (!token)
            {
               throw new Exception("api_access_token is not set and it's required when api_auth='token'")
            }

            auth = new TokenAuth(token)
         break
         case AuthTypeEnum.CUSTOM:
            def aapi = getProperty("api_auth_url")
            def user = getProperty("api_username")
            def pass = getProperty("api_password")

            if (!aapi)
            {
               throw new Exception("api_auth_url is not set and it's required when api_auth='custom'")
            }

            if (!user)
            {
               throw new Exception("api_username is not set and it's required when api_auth='custom'")
            }

            if (!pass)
            {
               throw new Exception("api_password is not set and it's required when api_auth='custom'")
            }

            auth = new CustomAuth(aapi, user, pass)
         break
         case AuthTypeEnum.NONE:
            auth = new NoAuth()
         break
      }

      // TODO: check config file for prefered content type or prefer, if no config is present, default values will apply
      client = new OpenEhrRestClient(
         getProperty("base_url"),
         //getProperty("sut_api_auth_url"),
         //getProperty("sut_api_admin_url"),
         auth,
         //Boolean.parseBoolean(getProperty("sut_api_perform_db_truncation")),
         ContentTypeEnum.JSON
      )

      // TODO: make committer values configurable
      client.setCommitterHeader('name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"')

      // Instant now = Instant.now()
      // ZonedDateTime zdt = ZonedDateTime.ofInstant(now, ZoneOffset.UTC) //ZoneId.systemDefault()
      // System.out.println( "Date is: " + zdt )

      // println zdt.format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

      ZonedDateTime.metaClass.static.nowFormatted = {
         def ext_datetime_utc = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
         ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
         return zdt.format(ext_datetime_utc)
      }

      /* can't use date.format since Groovy 2.5 groovy-dateutil module was removed from groovy-core
      Date.metaClass.static.nowFormatted = {
         def ext_datetime_utc = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
         return new Date().format(ext_datetime_utc, TimeZone.getTimeZone("UTC"))
      }

      Date.metaClass.formatted = {
         def ext_datetime_utc = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
         return delegate.format(ext_datetime_utc, TimeZone.getTimeZone("UTC"))
      }
      */
   }

   String getProperty(String propertyName) {
      return System.getenv(propertyName.toUpperCase()) ?: properties[propertyName]
   }


   /**
    * Test EHR creation with all possible combinations of valid EHR_STATUS.
    * This test case doesn't focus on data validation against OPT constraints.
    */
   @Unroll
   def "A. create new ehr"()
   {
      when:
         def ehr = create_ehr(data_set_no, is_queryable, is_modifiable, has_status, subject_id, other_details, ehr_id)

      then:
         if (!ehr) println client.lastError

         ehr != null
         ehr.ehr_status != null
         client.lastResponseCode == 201

         // ehr_status is an object_ref
         ehr.ehr_status.uid != null

         if (subject_id)
         {
            ehr.ehr_status.subject.external_ref.id.value == subject_id
         }

      // cleanup:
      //    // server cleanup
      //    client.truncateServer()


      // NOTE: all subject_ids should be different to avoid the "patient already have an EHR error", which is expected when you create two EHRs for the same patient
      where:
         [data_set_no, is_queryable, is_modifiable, has_status, subject_id, other_details, ehr_id] << valid_cases()
   }

   def "A.1. create ehr no host"()
   {
      when:
         def myclient = new OpenEhrRestClient(
            'http://wrongurl6699.com',
            new NoAuth(),
            ContentTypeEnum.JSON
         )

         myclient.setCommitterHeader('name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"')

         def ehr = myclient.createEhr()

      then:
         println myclient.lastError
         //thrown(java.net.UnknownHostException)
   }

   def "A.2. create ehr no connection"()
   {
      when:
         def myclient = new OpenEhrRestClient(
            'http://localhost:9999/wrong/url',
            new NoAuth(),
            ContentTypeEnum.JSON
         )

         myclient.setCommitterHeader('name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"')

         def ehr = myclient.createEhr()

      then:
         thrown(java.net.ConnectException)
   }

   def "B. upload template"()
   {
      when:
         String opt = this.getClass().getResource('/minimal_evaluation.opt').text
         def result = client.uploadTemplate(opt)

      then:
         if (!result)
         {
            if (client.lastError.error == 'Conflict: template already exists')
            {
               // This one is accepted, means the template already exists on the server
            }
            else
            {
               // This should be a real error
               println client.getLastError()
               assert false // make it fail on purpose
            }
         }

      // cleanup:
      //    // server cleanup
      //    client.truncateServer()
   }

   def "B.1. upload template no host"()
   {
      when:
         def myclient = new OpenEhrRestClient(
            'http://wrongurl6699.com',
            new NoAuth(),
            ContentTypeEnum.JSON
         )

         myclient.setCommitterHeader('name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"')

         String opt = this.getClass().getResource('/minimal_evaluation.opt').text
         myclient.uploadTemplate(opt)

      then:
         thrown(java.net.UnknownHostException)
   }

   def "B.2. upload template no connection"()
   {
      when:
         def myclient = new OpenEhrRestClient(
            'http://localhost:9999/wrong/url',
            new NoAuth(),
            ContentTypeEnum.JSON
         )

         myclient.setCommitterHeader('name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"')

         String opt = this.getClass().getResource('/minimal_evaluation.opt').text
         myclient.uploadTemplate(opt)

      then:
         thrown(java.net.ConnectException)
   }

   def "C. create new event composition"()
   {
      when:
         String opt        = this.getClass().getResource('/minimal_evaluation.opt').text
         String json_compo = this.getClass().getResource('/minimal_evaluation.en.v1_20230205.json').text

         client.uploadTemplate(opt)

         def parser = new OpenEhrJsonParserQuick()
         def compo = parser.parseJson(json_compo)
         def ehr = client.createEhr()

         def out_composition = client.createComposition(ehr.ehr_id.value, compo)

         // check the compo exists in the server
         def get_composition = client.getComposition(ehr.ehr_id.value, out_composition.uid.value)

      then:
         out_composition != null
         out_composition.uid.value != null
         get_composition != null
         get_composition.uid.value == out_composition.uid.value


      // cleanup:
      //    // server cleanup
      //    client.truncateServer()
   }


   private def create_ehr(data_set_no, is_queryable, is_modifiable, has_status, subject_id, other_details, ehr_id)
   {
      def ehr

      if (has_status)
      {
         def status = new EhrStatus()
         status.name = new DvText(value:"Generic Status")
         status.archetype_node_id = "openEHR-EHR-EHR_STATUS.generic.v1"
         status.is_modifiable = is_modifiable
         status.is_queryable = is_queryable
         status.archetype_details = new Archetyped(
            rm_version: '1.0.2',
            archetype_id: new ArchetypeId(
               value: "openEHR-EHR-EHR_STATUS.generic.v1"
            ),
            template_id: new TemplateId(
               value: "ehr_status_any_en_v1"
            )
         )

         // subject is mandatory in the RM, subject.external_ref is optional
         status.subject = new PartySelf()

         if (subject_id)
         {
            status.subject.external_ref = new PartyRef(
               namespace: "DEMOGRAPHIC",
               type: "PERSON",
               id: new GenericId(
                  value: subject_id,
                  scheme: "CABOLABS_MPI"
               )
            )
         }

         if (other_details)
         {
            status.other_details = new ItemTree(
               name: new DvText(
                  value: "tree"
               ),
               archetype_node_id: 'at0001',
               items: [
                  new Element(
                     name: new DvText(
                        value: "coded"
                     ),
                     archetype_node_id: 'at0002',
                     value: new DvCodedText(
                        value: 'some value',
                        defining_code: new CodePhrase(
                           code_string: '55501',
                           terminology_id: new TerminologyId(
                              value: 'coolterm'
                           )
                        )
                     )
                  )
               ]
            )
         }

         if (ehr_id)
         {
            ehr = client.createEhr(status, ehr_id) // TODO: not supported by the API yet
         }
         else
         {
            ehr = client.createEhr(status)
         }
      }
      else // without payload
      {
         if (ehr_id)
         {
            ehr = client.createEhr(ehr_id)
         }
         else
         {
            ehr = client.createEhr()
         }
      }

      return ehr
   }

   static List valid_cases()
   {
      // data_set_no | is_queryable | is_modifiable | has_status | subject_id | other_details | ehr_id
      return [
         [ null,       true,          true,           false,       null,            null,           null    ],
         [ null,       true,          true,           false,       null,            null,           randomUUID() ],
         [ 1,          true,          true,           true,        randomUUID(),    null,           null    ],
         [ 2,          true,          false,          true,        randomUUID(),    null,           null    ],
         [ 3,          false,         true,           true,        randomUUID(),    null,           null    ],
         [ 4,          false,         false,          true,        randomUUID(),    null,           null    ],
         [ 5,          true,          true,           true,        randomUUID(),    true,           null    ],
         [ 6,          true,          false,          true,        randomUUID(),    true,           null    ],
         [ 7,          false,         true,           true,        randomUUID(),    true,           null    ],
         [ 8,          false,         false,          true,        randomUUID(),    true,           null    ],
         [ 9,          true,          true,           true,        randomUUID(),    null,           randomUUID() ],
         [ 10,         true,          false,          true,        randomUUID(),    null,           randomUUID() ],
         [ 11,         false,         true,           true,        randomUUID(),    null,           randomUUID() ],
         [ 12,         false,         false,          true,        randomUUID(),    null,           randomUUID() ],
         [ 13,         true,          true,           true,        randomUUID(),    true,           randomUUID() ],
         [ 14,         true,          false,          true,        randomUUID(),    true,           randomUUID() ],
         [ 15,         false,         true,           true,        randomUUID(),    true,           randomUUID() ],
         [ 16,         false,         false,          true,        randomUUID(),    true,           randomUUID() ],
         [ 17,         true,          true,           true,        null,            null,           null    ],
         [ 18,         true,          false,          true,        null,            null,           null    ],
         [ 19,         false,         true,           true,        null,            null,           null    ],
         [ 20,         false,         false,          true,        null,            null,           null    ],
         [ 21,         true,          true,           true,        null,            true,           null    ],
         [ 22,         true,          false,          true,        null,            true,           null    ],
         [ 23,         false,         true,           true,        null,            true,           null    ],
         [ 24,         false,         false,          true,        null,            true,           null    ],
         [ 25,         true,          true,           true,        null,            null,           randomUUID()],
         [ 26,         true,          false,          true,        null,            null,           randomUUID()],
         [ 27,         false,         true,           true,        null,            null,           randomUUID()],
         [ 28,         false,         false,          true,        null,            null,           randomUUID()],
         [ 29,         true,          true,           true,        null,            true,           randomUUID()],
         [ 30,         true,          false,          true,        null,            true,           randomUUID()],
         [ 31,         false,         true,           true,        null,            true,           randomUUID()],
         [ 32,         false,         false,          true,        null,            true,           randomUUID()   ]
      ]
   }

   static randomUUID() {
      return java.util.UUID.randomUUID().toString()
   }


   // TODO: these two 'tests' should really be data load scripts that use the rest client, maybe putting them in loadEHR
   /*
   def "LOAD. create composition minimal evaluation 100 times"()
   {
      when:
         String opt        = this.getClass().getResource('/minimal_evaluation.opt').text
         String json_compo = this.getClass().getResource('/minimal_evaluation.en.v1_20230205.json').text

         //println json_compo

         client.uploadTemplate(opt)

         // if (client.lastError)
         // {
         //    println client.lastError
         // }

         def parser = new OpenEhrJsonParserQuick()
         def compo = parser.parseJson(json_compo)
         def ehr = client.createEhr()

         def results = (1..100).collect {
            client.createComposition(ehr.ehr_id.value, compo)
         }

         // def compo_out = client.createComposition(ehr.ehr_id.value, compo)

         // println compo_out

         // if (!compo_out)
         // {
         //    println client.lastError
         // }

         //client.lastError.result.code == 'EHRSERVER::API::RESPONSE_CODES::99213'
         //client.lastError.result.message

         //println results


      then:
         results.each {
            it != null
         }


      // cleanup:
      //    // server cleanup
      //    client.truncateServer()
   }

   def "LOAD. create demographic family trees"()
   {
      when:
         //String opt_person    = this.getClass().getResource('/generic_person.opt').text
         //String sample_person = this.getClass().getResource('/generic_person.json').text // FIXME: add person complete OPT and example

         // provides definition to the roles included in thet generic_person.json
         String opt_role      = this.getClass().getResource('/generic_role_complete.opt').text
         String opt_person    = this.getClass().getResource('/person_complete.opt').text
         String sample_person = this.getClass().getResource('/person_complete.json').text

         // this relationship is a "natural child" relationship, so the source is the child and the target is the parent.
         String opt_relationship    = this.getClass().getResource('/generic_relationship.opt').text
         String sample_relationship = this.getClass().getResource('/generic_relationship.json').text

         // OPT for EHR_STATUS
         String opt_ehr_status      = this.getClass().getResource('/ehr_status_any_en_v1.opt').text

         // OPTs for COMPOSITIONS
         String opt_demographics    = this.getClass().getResource('/demographics.opt').text
         String opt_encounter       = this.getClass().getResource('/encounter_with_coded_diagnosis.opt').text
         String opt_vital_signs     = this.getClass().getResource('/vital_signs_monitoring.opt').text

         // Sample COMPOSITIONS
         String sample_demographics = this.getClass().getResource('/demographics.json').text
         String sample_vital_signs  = this.getClass().getResource('/vital_signs_monitoring.json').text
         String sample_encounter    = this.getClass().getResource('/encounter_with_coded_diagnosis.json').text // NOTE: the diagnosis is dynamic below


         // Demographic data
         String demographic_data    = this.getClass().getResource('/demographic_data.json').text

         // JSON demographic data
         def json_parser = new JsonSlurper()
         def parsed_demographics = json_parser.parseText(demographic_data)

         // demographic
         client.uploadTemplate(opt_person)
         client.uploadTemplate(opt_role)
         client.uploadTemplate(opt_relationship)

         // ehr
         client.uploadTemplate(opt_ehr_status)
         client.uploadTemplate(opt_encounter)
         client.uploadTemplate(opt_vital_signs)
         client.uploadTemplate(opt_demographics)

         sleep(5000) // allow OPTS to be indexed


         def parser = new OpenEhrJsonParserQuick()
         parser.setSchemaFlavorAPI()


         // prototype person and relationship that will be customized below before committing to the server
         def person       = parser.parseActorDto(sample_person)
         def relationship = parser.parseJson(sample_relationship)

         def demographics = parser.parseJson(sample_demographics)
         def vital_signs  = parser.parseJson(sample_vital_signs)
         def encounter    = parser.parseJson(sample_encounter)

         //println person.identities[0].details.items[0].value.value
         // println relationship.source.id.value
         // println relationship.target.id.value

         // __PERSON_ID__
         // __FULL_NAME__
         // __DOB__
         // __SEX_VALUE__: Mascuilne, Female, Unknown
         // __SEX_CODE__: at0033, at0034, at0035

         def results = []
         def out_person

         def systolic_range = (110..160)
         def diastolic_range = (50..100)
         def diabetes_diagnosis = [ // parent: 73211009 | Diabetes mellitus (disorder) |
            '190447002':          'Steroid-induced diabetes',
            '427089005':          'Diabetes mellitus due to cystic fibrosis',
            '31321000119102':     'Diabetes mellitus type 1 without retinopathy',
            '23045005':           'Insulin dependent diabetes mellitus type IA',
            '237599002':          'Insulin treated type 2 diabetes mellitus',
            '46635009 ':          'Diabetes mellitus type 1'
         ]
         def allergy_diagnosis = [ // parent: 419076005 | Allergic reaction (disorder) |
            '139841000119108':    'Anaphylaxis caused by allergy skin test',
            '241933001':          'Peanut-induced anaphylaxis',
            '15920161000119108':  'Allergic reaction caused by egg protein',
            '419884005':          'Allergic reaction caused by flea bite'
         ]
         def hypertension_diagnosis = [ // 38341003 | Hypertensive disorder, systemic arterial (disorder)
            '59621000':           'Essential hypertension',
            '56218007':           'Systolic hypertension',
            '74451002':           'Secondary diastolic hypertension'
         ]
         def diagnosis = [ // list of maps
            diabetes_diagnosis,
            allergy_diagnosis,
            hypertension_diagnosis
         ]

         parsed_demographics.people.eachWithIndex { data_person, data_set_num ->

            person.details.items[0].items[0].value.id = data_person.nid

            //println data_person.name
            // change the name of the sample person
            person.identities[0].details.items[0].value.value = data_person.name
            person.identities[0].details.items[1].value.value = data_person.dob

            if (data_person.sex == 'M')
            {
               person.identities[0].details.items[2].value.value = 'Male'
               person.identities[0].details.items[2].value.defining_code.code_string = 'at0033'
            }
            else if (data_person.sex == 'F')
            {
               person.identities[0].details.items[2].value.value = 'Female'
               person.identities[0].details.items[2].value.defining_code.code_string = 'at0034'
            }
            else
            {
               person.identities[0].details.items[2].value.value = 'Unknown'
               person.identities[0].details.items[2].value.defining_code.code_string = 'at0035'
            }


            // ========================================================
            // creates the person in the server
            changeCommitHeaders()
            out_person = client.createActor(person)

            // save the uid so we can create the relationship for the parent
            // 0e488aa3-8686-4248-9b28-0d8f97a04c69::ATOMIK::1
            // since the value contains the system_id and version_tree_id, we need to extract the uuid part
            data_person.uid = out_person.uid.value.split("::")[0]

            // ========================================================
            // create the EHR for that person
            def ehr = create_ehr(data_set_num, true, true, true, data_person.uid, false, false)

            sleep(500) // adds delay to see different commti times

            // ========================================================
            // create demographic compo
            demographics.context.start_time.value = ZonedDateTime.nowFormatted()

            // sex: at0003=Male, at0004=Female, at0005=Unknown
            if (data_person.sex == 'M')
            {
               demographics.content[0].data.items[0].value.value = 'Male'
               demographics.content[0].data.items[0].value.defining_code.code_string = 'at0003'
            }
            else if (data_person.sex == 'F')
            {
               demographics.content[0].data.items[0].value.value = 'Female'
               demographics.content[0].data.items[0].value.defining_code.code_string = 'at0004'
            }
            else
            {
               demographics.content[0].data.items[0].value.value = 'Unknown'
               demographics.content[0].data.items[0].value.defining_code.code_string = 'at0005'
            }

            // dob (need to add the time since this is DV_DATE_TIME)
            demographics.content[0].data.items[1].value.value = data_person.dob +'T00:00:00.000Z'

            changeCommitHeaders()
            client.createComposition(ehr.ehr_id.value, demographics)

            sleep(500) // adds delay to see different commti times


            // ========================================================
            // create vital signs monitoring compo (X times)
            //
            10.times { // 100 vital signs compos per patient

               vital_signs.context.start_time.value = ZonedDateTime.nowFormatted()

               // systolic blood pressure
               vital_signs.content[0].data.events[0].data.items[0].value.magnitude = rand.nextInt(systolic_range.to - systolic_range.from) + systolic_range.from

               // diastolic blood pressure
               vital_signs.content[0].data.events[0].data.items[1].value.magnitude = rand.nextInt(diastolic_range.to - diastolic_range.from) + diastolic_range.from

               // temperature
               vital_signs.content[1].data.events[0].data.items[0].value.magnitude = rand.nextInt(39 - 36) + 36 + (rand.nextInt(10) / 10) // 37.1
               vital_signs.content[1].data.events[0].data.items[0].value.units = 'Cel' // Celsius

               // pulse
               vital_signs.content[2].data.events[0].data.items[1].value.magnitude = rand.nextInt(200 - 40) + 40

               // pulse oxymetry
               vital_signs.content[3].data.events[0].data.items[0].value.numerator = rand.nextInt(100 - 60) + 60

               // respiration rate
               vital_signs.content[4].data.events[0].data.items[1].value.magnitude = rand.nextInt(100 - 10) + 10

               changeCommitHeaders()
               client.createComposition(ehr.ehr_id.value, vital_signs)

               sleep(500) // adds delay to see different commti times
            }


            // ========================================================
            // create encounter with coded diagnosis compo
            encounter.context.start_time.value = ZonedDateTime.nowFormatted()

            def diagnostic_type_index = rand.nextInt(diagnosis.size())
            def diagnosis_map = diagnosis[diagnostic_type_index]

            def diagnosis_code = (diagnosis_map.keySet() as List)[rand.nextInt(diagnosis_map.size())]
            def diagnosis_text = diagnosis_map[diagnosis_code]

            // println diagnosis_code
            // println diagnosis_text

            encounter.content[1].data.items[0].value.value = diagnosis_text
            encounter.content[1].data.items[0].value.defining_code.code_string = diagnosis_code

            changeCommitHeaders()
            client.createComposition(ehr.ehr_id.value, encounter)

            sleep(100) // adds delay to see different commit times


            results << out_person
         }


         // create family relationships
         def parent
         def results_relationships = []
         parsed_demographics.people.each { data_person ->

            data_person.parents.each { parent_id ->

               relationship.source.id.value = data_person.uid // this is the version uid

               parent = parsed_demographics.people.find{ it.id == parent_id } // find one parent

               relationship.target.id.value = parent.uid // this is the version uid

               changeCommitHeaders()
               results_relationships << client.createRelationship(relationship)
            }
         }


      then:
         // results.each {
         //    it != null
         // }
         true


      //cleanup:
         // server cleanup
         //client.truncateServer()
   }
   */

   private void changeCommitHeaders()
   {
      def committer_names = [
         '1d0e25ca-3858-4b92-a460-11c77689e8e5': "Hayley Byrd",
         'a71647c7-4328-4361-8f70-7059a6af7398': "Claude Rivera",
         'f86f242a-6053-4bbe-b624-aaf3100b06c2': "Aine Bonner",
         '177cf0fb-7b64-4175-84a1-2fd48d67384b': "Kira Bright",
         '22b9ae6a-b25b-4e0d-b3e8-1f0d4c7fc1c6': "Krish Gould",
         '3a123211-ed4d-4ff2-a2be-922af1301ea8': "Ellis Hilton",
         'f0938104-e8c9-403b-8718-b4305091ac1a': "Betty Wheeler",
         '1e9ec637-0d3a-46f5-bf80-2be7c1190908': "Oskar Wells",
         'a7dcb6e7-b13c-4e4c-bf7a-a9d00a2b0063': "Aiza Sutherland",
         '2dc3fc27-8405-417a-b16d-8566f3df794c': "Laila Gill",
         '27ad20ee-01ee-4779-b13b-bb550ed5dd75': "Byron Cummings",
         '80764ab1-5d6c-43eb-944f-a21e3d02e275': "Mila Sims",
         'e82ae122-287c-4754-9d72-fa6d466f3341': "Callie Kennedy",
         'cd8a4ec9-0cfd-48ba-adb5-bd74df57bc26': "Md Moore",
         '8964e2ec-c074-4ca7-a7e6-16f9e4d98c92': "Ronan John",
         '0b697852-91ec-4938-86a6-2ccba6a83fee': "Ameer Owens",
         'd2c5e0d4-e27f-40cb-a036-11c7be3ef9d2': "Tanya Matthams",
         'f909ee96-fd28-4df3-9d35-24ed054dd234': "Jago Gross",
         '839fd8f1-3603-43ae-89ac-8ed65a61eef8': "Louie Edwards",
         'fba9382a-19a4-4651-a941-0faf3a42239f': "Hamzah Scott",
      ]

      def uid = (committer_names.keySet() as List)[rand.nextInt(committer_names.size())]
      def name = committer_names[uid]

      client.setCommitterHeader('name="'+ name +'", external_ref.id="'+ uid +'", external_ref.namespace="demographic", external_ref.type="PERSON"')
   }


   /*
   def "test list queries"()
   {
      when:
         // TODO: create stored queries to get them
         def queries = client.listQueries()

         def out_composition = client.createComposition(ehr.ehr_id.value, compo)

         // there is a problem with the update if it comes microseconds after the create for updating the created compo, there is a race condition when indexing.
         //sleep(5000)

         // NOTE: the compo should be updated but is not needed for this test so we use the same compo as the create
         def update_composition = client.updateComposition(ehr.ehr_id.value, compo, out_composition.uid.value)

      then:
         out_composition != null
         out_composition.uid.value != null
         update_composition != null
         update_composition.uid.value.split("::")[0] == out_composition.uid.value.split("::")[0]
         update_composition.uid.value.split("::")[1] == out_composition.uid.value.split("::")[1]
         Integer.parseInt(update_composition.uid.value.split("::")[2]) == Integer.parseInt(out_composition.uid.value.split("::")[2]) + 1


      //cleanup:
         // server cleanup
         //client.truncateServer()
   }
   */



   // UTIL METHODS

   /**
    * Replaces arguments in error messages: "This {0} is an error for {1}", [a, b] => "This a is an error for b"
    */
   private error_message_replace_values(String error_msg, List values)
   {
      for (int i = 0; i < values.size(); i++)
      {
         error_msg = error_msg.replace("{$i}", values[i].toString())
      }

      return error_msg
   }

   /**
    * object is a JSON parsed as a Map, we support JSON only for now!
    */
   private get_at_path(Map object, String path)
   {
      // path = a.b.c
      // path_quoted = "a"."b"."c"
      def path_quoted = '"'+ path.replaceAll('\\.', '"."') + '"'
      def command = ' o.'+ path_quoted

      // access object.a.b.c, where the object is named 'o' in the command
      Eval.me('o', object, command)
   }
}
