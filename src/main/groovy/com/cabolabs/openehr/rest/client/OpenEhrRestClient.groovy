package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rm_1_0_2.ehr.*
import com.cabolabs.openehr.rm_1_0_2.composition.Composition
import com.cabolabs.openehr.rm_1_0_2.demographic.*

import com.cabolabs.openehr.dto_1_0_2.ehr.EhrDto
import com.cabolabs.openehr.dto_1_0_2.demographic.*

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.util.logging.*

import com.cabolabs.openehr.formats.OpenEhrJsonParserQuick
import com.cabolabs.openehr.formats.OpenEhrJsonSerializer
import com.cabolabs.openehr.opt.model.OperationalTemplate
import com.cabolabs.openehr.opt.model.OperationalTemplateSummary
import com.cabolabs.openehr.opt.parser.OperationalTemplateParser
import com.cabolabs.openehr.opt.instance_generator.JsonInstanceCanonicalGenerator2
import java.util.Properties
import java.io.InputStream
import java.nio.charset.StandardCharsets
import net.pempek.unicode.UnicodeBOMInputStream

import com.cabolabs.openehr.rest.client.auth.Authentication

@groovy.util.logging.Slf4j
class OpenEhrRestClient {

   Authentication auth

   String  baseUrl // = 'http://192.168.1.110:8080/ehrbase/rest/openehr/v1'

   Map lastError = [:] // parsed JSON that contains an error response
   Map headers = [:] // extra request headers to use in the POST endpoints like committer

   Map lastResponseHeaders = [:]
   int lastResponseCode
   boolean lastConnectionProblem = false // true if the last request threw an exception instead of getting a result, in that case, the error returned will be JSON and can be parsed

   ContentTypeEnum accept
   PreferEnum      prefer

   // TODO: refactor to share common code

   OpenEhrRestClient (
      String baseUrl,
      Authentication auth,
      ContentTypeEnum accept = ContentTypeEnum.JSON,
      PreferEnum prefer      = PreferEnum.REPRESENTATION
   ) {
      this.baseUrl = baseUrl
      this.auth   = auth
      this.accept = accept
      this.prefer = prefer
   }

   int getLastResponseCode()
   {
      this.lastResponseCode
   }

   // value example: 'name="John Doe", external_ref.id="BC8132EA-8F4A-11E7-BB31-BE2E44B06B34", external_ref.namespace="demographic", external_ref.type="PERSON"'
   // see https://specifications.openehr.org/releases/ITS-REST/Release-1.0.2/overview.html#design-considerations-http-headers
   def setCommitterHeader(String value)
   {
      // TODO: add a header object so we can validate it's required contents,
      // with a string the user can set anything...
      this.headers["openEHR-AUDIT_DETAILS.committer"] = value
   }

   // value example: code_string="532"
   // see https://specifications.openehr.org/releases/ITS-REST/Release-1.0.2/overview.html#design-considerations-http-headers
   def setLifecycleStateHeader(String value)
   {
      this.headers["openEHR-VERSION.lifecycle_state"] = value
   }

   // value example: code_string="251"
   // see https://specifications.openehr.org/releases/ITS-REST/Release-1.0.2/overview.html#design-considerations-http-headers
   def setChangeTypeHeader(String value)
   {
      this.headers["openEHR-AUDIT_DETAILS.change_type"] = value
   }

   // value example: value="An updated composition contribution description"
   // see https://specifications.openehr.org/releases/ITS-REST/Release-1.0.2/overview.html#design-considerations-http-headers
   def setDescriptionHeader(String value)
   {
      this.headers["openEHR-AUDIT_DETAILS.description"] = value
   }

   HttpURLConnection createConnection(String path)
   {
      return new URL(this.baseUrl + path).openConnection()
   }


   // EHR

   /**
    * Creates a default EHR, no payload was provided and returns the full representation of the EHR created.
    * @return EHR created.
    */
   EhrDto createEhr()
   {
      //def req = new URL(this.baseUrl +"/ehr").openConnection()
      def req = createConnection('/ehr')

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])

      req.setRequestProperty("Prefer", this.prefer.toString())
      req.setRequestProperty("Accept", this.accept.toString())

      // Response will always be a json string
      String response_body = doRequest(req)

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (this.lastResponseCode.equals(201)) // return=representation
      {
         def parser = new OpenEhrJsonParserQuick()
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }
      else if (this.lastResponseCode.equals(204)) // return=minimal
      {
         return null
      }


      // FIXME: the parsing should rely on the response Content-Type, not assuming it will be JSON.
      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }

      return null // no ehr is returned if there is an error
   }

   EhrDto createEhr(String ehr_id)
   {
      def req = new URL(this.baseUrl +"/ehr/"+ ehr_id).openConnection()

      req.setRequestMethod("PUT")
      req.setDoOutput(true)

      req.setRequestProperty("Prefer", this.prefer.toString())
      req.setRequestProperty("Accept", this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])

      // Response will always be a json string
      String response_body = doRequest(req)

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (this.lastResponseCode.equals(201)) // return=representation
      {
         def parser = new OpenEhrJsonParserQuick()
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }
      else if (this.lastResponseCode.equals(204)) // return=minimal
      {
         return null
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }

      return null // no ehr is returned if there is an error
   }

   /**
    * Creates an EHR ith the given EHR_STATUS
    */
   EhrDto createEhr(EhrStatus ehr_status)
   {
      def req = new URL(this.baseUrl +"/ehr").openConnection()

      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(ehr_status)

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      req.setRequestProperty("Content-Type", "application/json") // add to send a status
      req.setRequestProperty("Prefer",       this.prefer.toString())
      req.setRequestProperty("Accept",       this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])

      // req.getOutputStream().write(body.getBytes("UTF-8"))


      // Response will always be a json string
      String response_body = doRequest(req, body)

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (this.lastResponseCode.equals(201))  // return=representation
      {
         def parser = new OpenEhrJsonParserQuick()
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }
      else if (this.lastResponseCode.equals(204)) // return=minimal
      {
         return null
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }

      return null // no ehr is returned if there is an error
   }

   /**
    * Creates an EHR ith the given EHR_STATUS and ehr_id
    */
   EhrDto createEhr(EhrStatus ehr_status, String ehr_id)
   {
      def req = new URL(this.baseUrl +"/ehr/"+ ehr_id).openConnection()

      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(ehr_status)

      req.setRequestMethod("PUT")
      req.setDoOutput(true)

      req.setRequestProperty("Content-Type", "application/json") // add to send a status
      req.setRequestProperty("Prefer",       this.prefer.toString())
      req.setRequestProperty("Accept",       this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])

      // req.getOutputStream().write(body.getBytes("UTF-8"))

      // Response will always be a json string
      String response_body = doRequest(req, body)

      if (this.lastResponseCode.equals(201)) // return=representation
      {
         def parser = new OpenEhrJsonParserQuick()
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }
      else if (this.lastResponseCode.equals(204)) // return=minimal
      {
         return null
      }

      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }

      return null // no ehr is returned if there is an error
   }

   EhrDto getEhr(String ehr_id)
   {
      def req = new URL(this.baseUrl +"/ehr/"+ ehr_id).openConnection()

      req.setRequestMethod("GET")
      req.setDoOutput(true)
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // Response will always be a json string
      String response_body = doRequest(req)

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (this.lastResponseCode.equals(200))
      {
         def parser = new OpenEhrJsonParserQuick()
         def ehr = parser.parseEhrDto(response_body)
         return ehr
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }

      return null // no ehr is returned if there is an error
   }


   // COMPOSITION

   Composition createComposition(String ehr_id, Composition compo)
   {
      def req = new URL("${this.baseUrl}/ehr/${ehr_id}/composition").openConnection()

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // NOTE: JSON only requests for now
      req.setRequestProperty("Content-Type",  "application/json")
      req.setRequestProperty("Prefer",        this.prefer.toString())
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])


      // NOTE: JSON only requests for now
      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(compo)

      // req.getOutputStream().write(body.getBytes("UTF-8"))


      // Response will always be a json string
      String response_body = doRequest(req, body)

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (this.lastResponseCode.equals(201)) // return=representation
      {
         def parser = new OpenEhrJsonParserQuick()
         def compo_out = parser.parseJson(response_body)
         return compo_out
      }
      else if (this.lastResponseCode.equals(204)) // return=minimal
      {
         return null
      }


      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }


      return null // no compo is returned if there is an error
   }

   Composition getComposition(String ehr_id, String version_uid)
   {
      def req = new URL("${this.baseUrl}/ehr/${ehr_id}/composition/${version_uid}").openConnection()

      req.setRequestMethod("GET")
      req.setDoOutput(true)
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // Response will always be a json string
      String response_body = doRequest(req)

      if (this.lastResponseCode.equals(200))
      {
         def parser = new OpenEhrJsonParserQuick()
         def compo_out = parser.parseJson(response_body)
         return compo_out
      }


      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }


      return null // no compo is returned if there is an error
   }

   /**
    * version_uid is the VERSION.uid of the object to be updated, in the format versioned_object_id::system_id::version
    */
   Composition updateComposition(String ehr_id, Composition compo, String version_uid)
   {
      def parts = version_uid.split('::')
      if (parts.size() < 3)
      {
         throw new Exception("Wrong format for version_uid")
      }
      def versioned_object_id = parts[0]

      def req = new URL("${this.baseUrl}/ehr/${ehr_id}/composition/${versioned_object_id}").openConnection()

      req.setRequestMethod("PUT")
      req.setDoOutput(true)

      // NOTE: JSON only requests for now
      req.setRequestProperty("Content-Type",  "application/json")
      req.setRequestProperty("Prefer",        this.prefer.toString())
      req.setRequestProperty("Accept",        this.accept.toString())
      req.setRequestProperty("If-Match",      version_uid)

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])


      // TODO: this is the same code as in POST composition
      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(compo)

      // req.getOutputStream().write(body.getBytes("UTF-8"))


      // Response will always be a json string
      String response_body = doRequest(req. body)

      // NOTE: the openEHR API responds 200 for updates
      // TODO: need to check for other 2xx codes and report a warning since it's not strictly compliant
      if (this.lastResponseCode.equals(200)) // return=representation
      {
         def parser = new OpenEhrJsonParserQuick()
         def compo_out = parser.parseJson(response_body)
         return compo_out
      }
      else if (this.lastResponseCode.equals(204)) // return=minimal
      {
         return null
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }


      return null // no compo is returned if there is an error
   }


   // TEMPLATE

   // TODO: we need a way to serialize OperationalTemplate to XML in openEHR-OPT
   def uploadTemplate(String opt)
   {
      def req = new URL("${this.baseUrl}/definition/template/adl1.4").openConnection()

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // NOTE: the upload template request will always be XML until we have a JSON schema for OPT
      req.setRequestProperty("Content-Type", "application/xml")
      req.setRequestProperty("Accept",       "application/xml") //this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // TODO: Need some AUDIT for uploading OPTs..

      // NOTE: getOutputStream() actually sends the request
      // https://github.com/frohoff/jdk8u-dev-jdk/blob/master/src/share/classes/sun/net/www/protocol/http/HttpURLConnection.java#L1281-L1292
      // req.getOutputStream().write(opt.getBytes("UTF-8"))


      // Response will always be a json string
      String response_body = doRequest(req, opt)

      // TODO: make configurable if it accepts a 409 Conflict as successful for this service
      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (this.lastResponseCode.equals(201)) // Created
      {
         return response_body
      }
      else if (this.lastResponseCode.equals(204)) // No Content
      {
         return opt // returns the same opt as the input
      }
      else if (this.lastResponseCode.equals(409)) // Conflict
      {
         // NOTE: this is more a warning than an error
         this.lastError = [status: 'error', message: "Conflict: template already exists"]
      }
      else
      {
         // NOTE: we could parse the error but different CDRs might return whatever they want,
         //       though we could check try parsing JSON then XML then treat it as string.
         if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
         {
            def json_parser = new JsonSlurper()
            this.lastError = json_parser.parseText(response_body)
         }
         else
         {
            println "No json errors "+ response_body
         }
         //this.lastError = [error: req.getErrorStream()?.getText()]
      }

      return null // no object is returned if there is an error
   }

   List<OperationalTemplateSummary> getTemplates()
   {
      def req = new URL("${this.baseUrl}/definition/template/adl1.4").openConnection()

      req.setRequestMethod("GET")
      req.setDoOutput(true)
      req.setRequestProperty("Accept", this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      // Response will be a jsono or xml string
      String response_body = doRequest(req)

      List result = []

      if (this.lastResponseCode.equals(200))
      {
         // NOTE: we don't have parsers for Template Summary yet
         if (this.accept == ContentTypeEnum.JSON)
         {
            def slurper = new JsonSlurper()
            def list = slurper.parseText(response_body)

            for (summary in list)
            {
               result << new OperationalTemplateSummary(
                  templateId:       summary.template_id,
                  concept:          summary.concept,
                  archetypeId:      summary.archetype_id,
                  createdTimestamp: summary.created_timestamp // TODO: fix cammel case on attribute
               )
            }
         }
         else
         {
            def list = new XmlSlurper().parseText(response_body)

            for (summary in list.map)
            {
               result << new OperationalTemplateSummary(
                  templateId:       summary.entry.find{it.@key='template_id'}.text(),
                  concept:          summary.entry.find{it.@key='concept'}.text(),
                  archetypeId:      summary.entry.find{it.@key='archetype_id'}.text(),
                  createdTimestamp: summary.entry.find{it.@key='created_timestamp'}.text() // TODO: fix cammel case on attribute
               )
            }
         }

         return result
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }

      return null // no ehr is returned if there is an error
   }

   OperationalTemplate getTemplate(String templateId)
   {
      def req = new URL("${this.baseUrl}/definition/template/adl1.4/${templateId}").openConnection()

      def old_accept = this.accept
      this.accept = ContentTypeEnum.XML

      req.setRequestMethod("GET")
      req.setDoOutput(true)
      req.setRequestProperty("Accept", this.accept.toString()) // TODO: XML only for now

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      // Response will be a jsono or xml string
      String response_body = doRequest(req)

      this.accept = old_accept

      OperationalTemplate opt

      if (this.lastResponseCode.equals(200))
      {
         // NOTE: we don't have parsers for Template Summary yet
         // if (this.accept == ContentTypeEnum.JSON)
         // {
         //    //def slurper = new JsonSlurper()
         //    //def list = slurper.parseText(response_body)

         //    // TODO: parse JSON template
         // }
         // else
         // {
            def parser = new OperationalTemplateParser()
            opt = parser.parse(response_body)
         // }

         return opt
      }

      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }

      return null // no ehr is returned if there is an error
   }


   ActorDto createActor(ActorDto actor)
   {
      def req = new URL("${this.baseUrl}/demographic/actor").openConnection()

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // NOTE: JSON only requests for now
      req.setRequestProperty("Content-Type",  "application/json")
      req.setRequestProperty("Prefer",        this.prefer.toString())
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])


      // NOTE: JSON only requests for now
      def serializer = new OpenEhrJsonSerializer()

      // FIXME: serialize DTO!
      def body = serializer.serialize(actor)

      // req.getOutputStream().write(body.getBytes("UTF-8"))


      // Response will always be a json string
      String response_body = doRequest(req, body)

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (this.lastResponseCode.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         parser.setSchemaFlavorAPI()

         def out = parser.parseActorDto(response_body) // NOTE: parseJson doesn't work with the ActorDto
         return out
      }
      else if (this.lastResponseCode.equals(204)) // return=minimal
      {
         return null
      }


      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }


      return null // no compo is returned if there is an error
   }

   PartyRelationship createRelationship(PartyRelationship relationship)
   {
      def req = new URL("${this.baseUrl}/demographic/relationship").openConnection()

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // NOTE: JSON only requests for now
      req.setRequestProperty("Content-Type",  "application/json")
      req.setRequestProperty("Prefer",        this.prefer.toString())
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])


      // NOTE: JSON only requests for now
      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(relationship)

      // req.getOutputStream().write(body.getBytes("UTF-8"));


      // Response will always be a json string
      String response_body = doRequest(req, body)

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (this.lastResponseCode.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         parser.setSchemaFlavorAPI()

         def out = parser.parseJson(response_body)
         return out
      }
      else if (this.lastResponseCode.equals(204)) // return=minimal
      {
         return null
      }


      // Expects a JSON error
      // NOTE: if other 2xx code is returned, this will try to parse it as an error and is not, see note above
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }


      return null // no compo is returned if there is an error
   }

   Role createRole(Role role, String performerObjectId)
   {
      def req = new URL("${this.baseUrl}/demographic/actor/${performerObjectId}/role").openConnection()

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // NOTE: JSON only requests for now
      req.setRequestProperty("Content-Type", "application/json")
      req.setRequestProperty("Prefer",       this.prefer.toString())
      req.setRequestProperty("Accept",       this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])


      // NOTE: JSON only requests for now
      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(role)

      // req.getOutputStream().write(body.getBytes("UTF-8"))

       // Response will always be a json string
      String response_body = doRequest(req, body)

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (this.lastResponseCode.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         parser.setSchemaFlavorAPI()

         def out = parser.parseJson(response_body)
         return out
      }
      else if (this.lastResponseCode.equals(204)) // return=minimal
      {
         return null
      }


      // Expects a JSON error
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }


      return null // no compo is returned if there is an error
   }


   RoleDto createRole(RoleDto role, String performerObjectId)
   {
      def req = new URL("${this.baseUrl}/demographic/actor/${performerObjectId}/role").openConnection()

      req.setRequestMethod("POST")
      req.setDoOutput(true)

      // NOTE: JSON only requests for now
      req.setRequestProperty("Content-Type", "application/json")
      req.setRequestProperty("Prefer",       this.prefer.toString())
      req.setRequestProperty("Accept",       this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      // required commiter header
      if (!this.headers["openEHR-AUDIT_DETAILS.committer"])
      {
         throw new Exception("Header openEHR-AUDIT_DETAILS.committer is required")
      }

      req.setRequestProperty("openEHR-AUDIT_DETAILS.committer", this.headers["openEHR-AUDIT_DETAILS.committer"])


      // NOTE: JSON only requests for now
      def serializer = new OpenEhrJsonSerializer()
      def body = serializer.serialize(role)

      // req.getOutputStream().write(body.getBytes("UTF-8"))

       // Response will always be a json string
      String response_body = doRequest(req, body)

      // NOTE: add support to detect other 2xx statuses with a warning that the spec requires 201, but it's not wrong to return 200
      if (this.lastResponseCode.equals(201))
      {
         def parser = new OpenEhrJsonParserQuick()
         parser.setSchemaFlavorAPI()

         def out = parser.parseJson(response_body)
         return out
      }
      else if (this.lastResponseCode.equals(204)) // return=minimal
      {
         return null
      }


      // Expects a JSON error
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }


      return null // no compo is returned if there is an error
   }


   /**
   updateEhrStatus
   getComposition at time

   updateActor

   updateRelationship
   getRelationship
   createDirectory
   updateDirectory
   getDirectory
   createContribution
   getContribution
   getVersionedComposition
   ...
   executeStoredQuery
   */

   ActorDto getActor(String version_uid)
   {
      def req = new URL("${this.baseUrl}/demographic/actor/${version_uid}").openConnection()

      req.setRequestMethod("GET")
      req.setDoOutput(true)
      req.setRequestProperty("Accept", this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)

      // Response will always be a json string
      String response_body = doRequest(req)

      if (this.lastResponseCode.equals(200))
      {
         def parser = new OpenEhrJsonParserQuick()
         parser.setSchemaFlavorAPI()

         def actor_out = parser.parseJson(response_body) //parser.parseActorDto(response_body) // NOTE: parseJson doesn't work with the ActorDto
         return actor_out
      }

      // Expects a JSON error
      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }


      return null // no compo is returned if there is an error
   }


   // QUERY (tests)

   def storeQuery(String qualified_name, String version, String type, String query)
   {

   }

   List listQueries()
   {
      def req = new URL("${this.baseUrl}/definition/query").openConnection()

      req.setRequestMethod("GET")
      req.setDoOutput(true)

      // NOTE: JSON only for now
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      String response_body = doRequest(req)

      if (this.lastResponseCode.equals(200))
      {
         // def parser = new OpenEhrJsonParserQuick()
         // def compo_out = parser.parseJson(response_body)
         // return compo_out

         // TODO: we don't have a standard class for the query structure yet, so we return the JSON object directly
         def json_parser = new JsonSlurper()
         return json_parser.parseText(response_body)
      }


      if (this.lastConnectionProblem || this.lastResponseHeaders['Content-Type']?.startsWith('application/json'))
      {
         def json_parser = new JsonSlurper()
         this.lastError = json_parser.parseText(response_body)
      }
      else
      {
         println "No json errors "+ response_body
      }


      return null // no compo is returned if there is an error
   }

   // Query result types: query_result_count, query_result_list and query_result_grouped (grouped by ehr_id)
   // if count key is in the result, it's a count query
   // if the resultType is composition, ehr_status or folder, it can be grouped by ehr_id or not, depending on the configuration of the server, if it's grouped by ehr_id, the result is a map, if not, the result is a list
   QueryResult executeQuery(String query_id, Map<String, String> parameters = [:])
   {
      // Build query string
      def queryString = parameters.collect { k, v ->
         "${URLEncoder.encode(k.toString(), 'UTF-8')}=${URLEncoder.encode(v.toString(), 'UTF-8')}"
      }.join('&')

      def req = new URL("${this.baseUrl}/query/${query_id}/execute?${queryString}").openConnection()

      req.setRequestMethod("GET")
      req.setDoOutput(true)

      // NOTE: JSON only for now
      req.setRequestProperty("Content-Type",  "application/json")
      // req.setRequestProperty("Prefer",        this.prefer.toString())
      req.setRequestProperty("Accept",        this.accept.toString())

      // makes the authenticaiton magic over the current request
      this.auth.apply(req)


      // Response will always be a json string
      String response_body = doRequest(req)

      if (this.lastResponseCode.equals(200))
      {
         def json_parser = new JsonSlurper()
         def response_json  = json_parser.parseText(response_body)


         if (response_json._type == 'query_result_count')
         {
            return new QueryResult(
               resultType: response_json._type,
               count: response_json.count,
               retrieveData: false
            )
         }
         else if (response_json._type == 'query_result_list')
         {
            boolean retrieveData = parameters.retrieveData ?: false

            def parsed_items = []

            if (retrieveData)
            {
               def parser = new OpenEhrJsonParserQuick()
               parser.setSchemaFlavorAPI()

               response_json.result.each { item ->
                  parsed_items << parser.parseJson(item)
               }
            }
            else
            {
               response_json.result.each { item ->
                  if (item.type == 'COMPOSITION')
                  {
                     item.startTime = parseDate(item.startTime)
                  }

                  item.timeCommitted = parseDate(item.timeCommitted)
                  item.timeCreated   = parseDate(item.timeCreated)

                  parsed_items << new QueryResultItem(item)
               }
            }

            return new QueryResult(
               resultType:   response_json._type,
               result:       parsed_items,    //  FIXME: if retrieveData => This should be parsed to the locatable or summary object
               retrieveData: retrieveData,
               offset:       response_json.pagination?.offset ?: 0,
               max:          response_json.pagination?.max ?: 0
            )
         }
         else if (response_json._type == 'query_result_grouped')
         {
            boolean retrieveData = parameters.retrieveData ?: false

            def parsed_items = [:] // ehr_id -> list of items

            if (retrieveData)
            {
               response_json.result.each { ehr_id, items ->

                  def parsed_item_list = []
                  def parser = new OpenEhrJsonParserQuick()
                  parser.setSchemaFlavorAPI()

                  items.each { item ->
                     parsed_item_list << parser.parseJson(item)
                  }

                  parsed_items[ehr_id] = parsed_item_list
               }
            }
            else
            {
               response_json.result.each { ehr_id, items ->

                  def parsed_item_list = []

                  items.each { item ->
                     if (item.type == 'COMPOSITION')
                     {
                        item.startTime = parseDate(item.startTime)
                     }

                     item.timeCommitted = parseDate(item.timeCommitted)
                     item.timeCreated   = parseDate(item.timeCreated)

                     parsed_item_list << new QueryResultItem(item)
                  }

                  parsed_items[ehr_id] = parsed_item_list
               }
            }

            return new QueryResult(
               resultType:   response_json._type,
               result:       parsed_items,
               retrieveData: retrieveData,
               offset:       response_json.pagination?.offset ?: 0,
               max:          response_json.pagination?.max ?: 0
            )
         }
         else
         {
            throw new Exception("Unknown query result type: ${response_json._type}")
         }

         // def parser = new OpenEhrJsonParserQuick()
         // def locatable_out = parser.parseJson(response_body)
         // return locatable_out
      }

   }

   // TODO: execute ad-hoc query

   static String removeBOM(byte[] bytes)
   {
      def inputStream = new ByteArrayInputStream(bytes)
      def bomInputStream = new UnicodeBOMInputStream(inputStream)
      bomInputStream.skipBOM() // NOP if no BOM is detected
      def br = new BufferedReader(new InputStreamReader(bomInputStream))
      return br.text // http://docs.groovy-lang.org/latest/html/groovy-jdk/java/io/BufferedReader.html#getText()
   }


   // connection https://github.com/openjdk/jdk11u/blob/master/src/java.base/share/classes/sun/net/www/protocol/http/HttpURLConnection.java
   String doRequest(HttpURLConnection connection, String body = "")
   {
      String response_body
      def stream

      try
      {
         // reset last XXX
         this.lastResponseHeaders = [:]
         this.lastResponseCode = -1
         this.lastError = [:]
         this.lastConnectionProblem = false


         connection.setRequestProperty("Content-Length", body.size().toString())

         if (body)
         {
            connection.getOutputStream().write(body.getBytes("UTF-8"))
         }

         connection.connect()


         // NOTE: If we do getResponseCode() here and the response is 4xx or 5xx, then the code below won't
         // throw an IOException! Though by looking at the OpenJDK code, it should always throw the exception
         // https://github.com/openjdk/jdk11u/blob/master/src/java.base/share/classes/sun/net/www/protocol/http/HttpURLConnection.java#L1944

         this.lastResponseCode = connection.getResponseCode()

         // NOTE: internally getErrorStream() can return the input stream
         // https://github.com/openjdk/jdk11u/blob/master/src/java.base/share/classes/sun/net/www/protocol/http/HttpURLConnection.java#L2019
         stream = connection.getErrorStream() // 4xx, 5xx
         if (stream)
         {
            response_body = stream.getText()
         }
         else
         {
            // this throws an exception if the response status code is not 2xx
            response_body = connection.getInputStream().getText()
         }

         // Response Headers
         // REF: https://specifications.openehr.org/releases/ITS-REST/latest/overview.html#tag/Requests_and_responses/HTTP-headers/ETag-and-Last-Modified

         // [Keep-Alive:[timeout=60], Transfer-Encoding:[chunked], null:[HTTP/1.1 201], ETag:[fabc2805-46ce-4bc0-9c2f-5659095a1046], Connection:[keep-alive], Set-Cookie:[JSESSIONID=55FD0A496C1EBAB5824DC62A19679F60; Path=/; HttpOnly], Vary:[Access-Control-Request-Headers, Access-Control-Request-Method, Origin], Date:[Sat, 24 Aug 2024 20:20:29 GMT]]

         // Map<String, List<String>>
         Map responseHeaders = connection.getHeaderFields() // alternative .getHeaderField(name): String

         lastResponseHeaders['ETag'] = responseHeaders['ETag'] ? responseHeaders['ETag'][0] : null // NOTE: depending on the server, this chould be null
         lastResponseHeaders['Last-Modified'] = responseHeaders['Last-Modified'] ?: null // NOTE: this could also be null

         if (responseHeaders['Content-Type'])
         {
            lastResponseHeaders['Content-Type'] = responseHeaders['Content-Type'][0] ?: null // application/json;charset=UTF-8
         }
      }
      catch (java.net.UnknownHostException e)
      {
         // Checked here because the message of the exception is just the URL and we need
         // a more explicit message.

         def message = JsonOutput.toJson(e.getMessage())[1..-2] // Remove first and last quote // escape

         response_body = """{
            \"status\": \"error\",
            \"context\": \"This is an 'unknown host' error, check the host is correct\",
            \"message\": \"The host is unreachable: ${message}\"
         }"""

         this.lastConnectionProblem = true

         return response_body
      }
      // Parent exception for UnknownHostException, ConnectException and other network related exceptions
      // also when the response code is 5xx it throws IOException.
      // We shouldn't expect a response body when there is an exception, se we create one ourselves.
      catch (java.io.IOException e)
      {
         def message = JsonOutput.toJson(e.getMessage()) // escape

         response_body = """{
            \"status\": \"error\",
            \"context\": \"This is a network related error, it's probable the connection couldn't be established\",
            \"message\": ${message}
         }"""

         this.lastConnectionProblem = true

         return response_body
      }
      catch (Exception e)
      {
         //println "Exception ------------------"+ e.getClass()
         //println e.getMessage()
         //e.printStackTrace()

         def message = JsonOutput.toJson(e.getMessage()) // escape

         e.printStackTrace()

         response_body = """{
            \"status\": \"error\",
            \"context\": \"This is a generic error handler\",
            \"message\": ${message}
         }"""

         this.lastConnectionProblem = true

         return response_body
      }


      /*
      // Will have response code only if the connection was established
      if (this.lastResponseCode)
      {
         // Check the response body only if the server returned contents
         switch (this.lastResponseCode)
         {
            case 200..299:
               println this.lastResponseCode
            break
            case 400..499:
               println this.lastResponseCode
            break
            case 500..599:
               println this.lastResponseCode
               println response_body
            break
         }
      }
      */

      // Check if response body is a json , if not, create the json string
      // THIS IS AN EXTRA CHECK NOT REALLY NEEDED!
      if (response_body)
      {
         // println ">>> response body "+ response_body
         if (this.accept == ContentTypeEnum.JSON)
         {
            try
            {
               def json_parser = new JsonSlurper()
               json_parser.parseText(response_body)
            }
            catch(Exception e) // IllegalArgumentException or groovy.json.JsonException
            {
               def message = JsonOutput.toJson(response_body) // escape

               response_body = """{
                  \"status\": \"error\",
                  \"context\": \"We got a response from the server but it's not in the expected format\",
                  \"message\": ${message}
               }"""

               this.lastConnectionProblem = true
               // This is not a connection problem, we got a result but it's not what's expected, but lets the client code to process the JSON as a response
            }
         }
         // TODO: XML
      }

      return response_body
   }

   /* FIXME: trucation should be an extension of the REST Client
   def truncateServer()
   {
      if (performDbTruncation) {
         def req = new URL(this.baseAdminUrl + "/truncate_all").openConnection()
         req.setRequestProperty("Authorization", "Bearer " + this.token)
         int code = req.getResponseCode()
         //req.getInputStream().getText()
      }
   }
   */

   private Date parseDate(String dateStr)
   {
      java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      sdf.timeZone = TimeZone.getTimeZone("UTC")
      return sdf.parse(dateStr)
   }
}
