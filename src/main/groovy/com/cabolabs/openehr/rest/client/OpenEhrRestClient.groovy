package com.cabolabs.openehr.rest.client

import com.cabolabs.openehr.rm_1_0_2.ehr.*

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.cabolabs.openehr.formats.OpenEhrJsonParser
import com.cabolabs.openehr.opt.parser.OperationalTemplateParser
import com.cabolabs.openehr.opt.model.OperationalTemplate
import com.cabolabs.openehr.opt.instance_generator.JsonInstanceCanonicalGenerator2
import java.util.Properties
import java.io.InputStream
import net.pempek.unicode.UnicodeBOMInputStream
import org.apache.log4j.*
import groovy.util.logging.*
import java.nio.charset.StandardCharsets

@Log4j
class OpenEhrRestClient {

   String baseUrl // = 'http://192.168.1.110:8080/ehrbase/rest/openehr/v1'
   String baseAuthUrl
   String token

   OpenEhrRestClient (String baseUrl, String baseAuthUrl)
   {
      this.baseUrl = baseUrl
      this.baseAuthUrl = baseAuthUrl
   }

   String auth(String user, String pass)
   {
      def post = new URL(this.baseAuthUrl +"/auth").openConnection()
      def body = '' // '{"message":"this is a message"}' // add to send a status

      post.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      //post.setRequestProperty("Content-Type", "application/json") // add to send a status
      //post.setRequestProperty("Prefer", "return=representation")
      post.setRequestProperty("Accept", "application/json")

      post.setRequestMethod("POST")
      post.setDoOutput(true)

      String params = "email=${user}&password=${pass}&format=json"
      byte[] postData = params.getBytes(StandardCharsets.UTF_8)
      post.setRequestProperty("Content-Length", Integer.toString(postData.length))

      DataOutputStream wr = new DataOutputStream(post.getOutputStream())
      wr.write(postData)


      //post.getOutputStream().write(body.getBytes("UTF-8"));
      def status = post.getResponseCode()

      if (status.equals(200))
      {
         def response_body = post.getInputStream().getText()

         //println response_body

         def json_parser = new JsonSlurper()
         def json = json_parser.parseText(response_body)
         this.token = json.token

         //println this.token
      }
      else
      {
         println post.getInputStream().getText()
      }
   }

   /**
    * Creates a default EHR, no payload was provided and returns the full representation of the EHR created.
    * @return EHR created.
    */
   Ehr createEhr()
   {
      if (!this.token)
      {
         throw new Exception("Not authenticated")
      }
      /*
      try
      {
         */
         def post = new URL(this.baseUrl +"/ehr").openConnection()
         def body = '' // '{"message":"this is a message"}' // add to send a status

         post.setRequestMethod("POST")
         post.setDoOutput(true)

         //post.setRequestProperty("Content-Type", "application/json") // add to send a status
         post.setRequestProperty("Prefer", "return=representation")
         post.setRequestProperty("Accept", "application/json")
         post.setRequestProperty("Authorization", "Bearer "+ this.token)

         //post.getOutputStream().write(body.getBytes("UTF-8"));
         def status = post.getResponseCode()

         if (status.equals(201))
         {
            def response_body = post.getInputStream().getText()
            //def json_parser = new JsonSlurper()
            //def ehr = json_parser.parseText(response_body)
            def parser = new OpenEhrJsonParser()
            def ehr = parser.parseEhr(response_body)
            return ehr

            /*
            // register the ehr_id for later commits
            ehr_ids << ehr.ehr_id.value

            return true
            */
         }
         else
         {
            //println post.getInputStream().getText()
         }
      /*
      }
      catch (Exception e) // connection issues or parsing the ehr, etc
      {
         println e.message
         return false
      }

       */
   }

    def ehr_ids = []


    boolean createEHR()
    {
        try
        {
            def post = new URL(base_url +"/ehr").openConnection()
            def body = '' // '{"message":"this is a message"}' // add to send a status

            post.setRequestMethod("POST")
            post.setDoOutput(true)
            
            //post.setRequestProperty("Content-Type", "application/json") // add to send a status
            post.setRequestProperty("Prefer", "return=representation")
            post.setRequestProperty("Accept", "application/json")

            //post.getOutputStream().write(body.getBytes("UTF-8"));
            def status = post.getResponseCode()

            if (status.equals(201))
            {
                def response_body = post.getInputStream().getText()
                def json_parser = new JsonSlurper()
                def ehr = json_parser.parseText(response_body)

                // register the ehr_id for later commits
                ehr_ids << ehr.ehr_id.value

                return true
            }
            else
            {
                //println post.getInputStream().getText()
            }

            return false
        }
        catch (Exception e) // connection issues
        {
            println e.message
            return false
        }
    }

    // parses the opt from the file, the caller already checked the file exists
    OperationalTemplate readTemplate(File opt_file)
    {
        def parser = new OperationalTemplateParser()
        try
        {
           def opt = parser.parse(opt_file.text)
           return opt
        }
        catch (Exception e)
        {
            println e.message
            return
        }
    }

    // true if the template exists, false if not
    boolean templateExists(String template_id)
    {
        try
        {
            def post = new URL(base_url +"/definition/template/adl1.4/"+ template_id.replaceAll(" ", "%20")).openConnection()

            post.setRequestMethod("GET")
            post.setRequestProperty("Accept", "application/xml")
            
            def status = post.getResponseCode()

            if (status.equals(200))
            {
                return true
            }

            return false
        }
        catch (Exception e)
        {
            println e.message
            return false
        }
    }

    // tries to upload an OPT, returns true if it was uploaded correctly, false if not
    boolean uploadTemplate(String template_location)
    {
        try
        {
            def post = new URL(base_url +"/definition/template/adl1.4").openConnection()

            def opt_file = new File(template_location)

            def body = opt_file.text

            post.setRequestMethod("POST")
            post.setDoOutput(true)
            post.setRequestProperty("Content-Type", "application/xml")
            //post.setRequestProperty("Prefer", "return=representation")
            post.setRequestProperty("Accept", "application/xml")
            post.getOutputStream().write(body.getBytes("UTF-8"))

            def status = post.getResponseCode()

            if ([201, 204].contains(status))
            {
                return true
            }

            return false
        }
        catch (Exception e) // connection issues
        {
            println e.message
            return false
        }
    }

    boolean commitComposition(String ehr_id, String json_compo)
    {
        try
        {
            def post = new URL(base_url +"/ehr/${ehr_id}/composition").openConnection()

            def body = json_compo

            post.setRequestMethod("POST")
            post.setDoOutput(true)
            post.setRequestProperty("Content-Type", "application/json")
            //post.setRequestProperty("Prefer", "return=representation")
            //post.setRequestProperty("Accept", "application/xml")
            post.getOutputStream().write(body.getBytes("UTF-8"))

            def status = post.getResponseCode()

            if ([201, 204].contains(status))
            {
                return true
            }

            return false
        }
        catch (Exception e) // connection issues
        {
            println e.message
            return false
        }
    }

    // executes a query to check for it's execution time
    Map witnessQuery(String aql_body)
    {
        def post, status
        try
        {
            post = new URL(base_url +"/query/aql").openConnection()

            post.setRequestMethod("POST")
            post.setDoOutput(true)
            post.setRequestProperty("Content-Type", "application/json")
            //post.setRequestProperty("Prefer", "return=representation")
            post.setRequestProperty("Accept", "application/json")

            post.getOutputStream().write(aql_body.getBytes("UTF-8"))

            status = post.getResponseCode()

            if ([200].contains(status))
            {
                return [result: 'ok']
            }
            else
            {
                /*
                def response_body = post.getInputStream().getText()
                def json_parser = new JsonSlurper()
                def response = json_parser.parseText(response_body)
                response.error
                // {"error":"Could not retrieve stored query, reason:java.lang.NullPointerException","status":"Bad Request"}
                */

                // if !2xx, the body should be read using getErrorStream(), getInputStream() only works for 2xx
                return [result: 'error', error: post.getErrorStream().getText()]
            }
        }
        catch (Exception e) // connection issues
        {
            return [result: 'error', error: e.message]
        }
    }

    /*
    static void main(String[] args)
    {
        log.info 'Running script..'

        //println args
        def f = new java.text.SimpleDateFormat("yyyyMMddhhmmss")
        def before, after, ehr_time // for time checking

        // --------------------------------------------------------------------------
        // load config
        
        try
        {
            //println getClass().getResource('/config.properties') // works!
            InputStream is = getClass().getResourceAsStream("/config.properties")    
            Properties properties = new Properties()
            properties.load(is)

            //println properties.base_url

            base_url = properties.base_url
        }
        catch (Exception e)
        {
            println "Can't load config.properties"
            System.exit(0)
        }


        // --------------------------------------------------------------------------
        // Util
        
        java.util.ArrayList.metaClass.pick {
            delegate.get( new Random().nextInt( delegate.size() ) )
        }

        cli = new CliBuilder(usage:'testehr [options]', header:'Options:')
        //cli.ehrs(args:1, argName:'ehrs', 'number of EHRs to create')
        //cli.template(args:1, argName:'template', 'operational template file location')


        // --------------------------------------------------------------------------
        // Argument processing

        def options = cli.parseFromSpec(AppArgs, args)

        if (args.size() < 8)
        {
           cli.usage()
           System.exit(0)
        }

        //println options
        //println options.ehrs()

        def ehr_count = options.ehrs()
        def template_location = options.template()
        def composition_count = options.compositions()
        def aql_location = options.aql()
        def scale_templates = options.scaleTemplates()
        def repeat_aql = options.repeatAql()

        validateArguments(ehr_count, template_location, composition_count, aql_location, scale_templates)

        def template_file = new File(template_location)
        def aql_file = new File(aql_location)

        // If aql_file is a folder, read the JSON files in that folder and execute all of them as queries, then report back each individual result
        def aql_files = []
        if (aql_file.isDirectory())
        {
            aql_file.eachFileMatch(~/.*\.json/) { aql ->
                aql_files << aql
            }
        }
        else
        {
            aql_files << aql_file
        }


        //println ehr_count
        //println template_location


        // --------------------------------------------------------------------------
        // Test execution

        def testehr = new App()
        def xml_temp, orig_template_id, xml_string, file_temp, temp_template_location


        // variables for the CSV report
        def csv = "template_id,template_accum,ehrs_created,ehrs_accum,compositions_committed,compositions_accum,compositions_commit_time,query,query_execution_time_avg,query_execution_time_min,query_execution_time_max,csv_query_status\n"
        def csv_template_id,
            csv_template_accum = 0,
            csv_ehrs_created = 0,
            csv_ehrs_accum = 0,
            csv_compositions_committed = 0,
            csv_compositions_accum = 0,
            csv_compositions_commit_time = 0,
            csv_query,
            csv_query_execution_time_avg = 0,
            csv_query_execution_time_min = 0,
            csv_query_execution_time_max = 0,
            csv_query_status


        // this parsing is done once for generating new templates
        xml_string = template_file.text
        xml_string = removeBOM(xml_string.getBytes())
        xml_temp = new XmlParser(false, false).parseText(xml_string)
        orig_template_id = xml_temp.template_id.value.text()

        scale_templates.times { scale_templates_i ->

            csv_template_id = orig_template_id
            csv_template_accum++

            // modify the template ID for the 2nd to N template
            if (scale_templates_i > 0)
            {
                temp_template_location = template_file.getParent() + File.separator + "${orig_template_id}_${scale_templates_i}.opt"

                file_temp = new File(temp_template_location)

                // create the new template only if it doesnt exists, saves time for future runs
                if (!file_temp.exists())
                {
                    // setting new template ID
                    xml_temp.template_id.replaceNode {
                        template_id {
                            value(orig_template_id +"_"+ scale_templates_i)
                        }
                    }

                    // setting also the uid because EHRBASE fails with same uids
                    xml_temp.uid.replaceNode {
                        uid {
                            value(UUID.randomUUID().toString())
                        }
                    }

                    // save opt file with new template ID
                    xml_string = groovy.xml.XmlUtil.serialize(xml_temp)

                    file_temp << xml_string
                }

                // now the rest of the tests will run with the new template/template_id
                template_file = file_temp
                template_location = temp_template_location
                csv_template_id = orig_template_id +"_"+ scale_templates_i
            }


            //println ">>>>> ${scale_templates_i}"

            // 1. parse template, check if invalid
            def opt = testehr.readTemplate(template_file)

            if (!opt)
            {
                println "There was a problem parsing the OPT, please verify it's valid"
                System.exit(0)
            }

            // ---------------------------------------------------------------------


            // 2. create EHRs
            println "Creating EHRs..."
            before = System.currentTimeMillis()
            ehr_count.times {
                testehr.createEHR()
            }
            after = System.currentTimeMillis()

            ehr_time = after - before

            csv_ehrs_created = testehr.ehr_ids.size()
            csv_ehrs_accum += csv_ehrs_created


            // 3. check template exists
            println "Checking template exists in the server..."
            if (!testehr.templateExists(opt.templateId))
            {
                println "Template ${opt.templateId} is not in the server"

                // 4. upload template
                testehr.uploadTemplate(template_location)
            }
            else
            {
                println "Template ${opt.templateId} exists in the server"
            }


            // 5. generate compositions
            println "Committing auto-generated compositions..."
            def generator, json_compo
            composition_count.times {

                generator = new JsonInstanceCanonicalGenerator2()
                json_compo = generator.generateJSONCompositionStringFromOPT(opt)

                // 5.1. commit compositions
                before = System.currentTimeMillis()
                if (testehr.commitComposition(testehr.ehr_ids.pick(), json_compo))
                {
                    csv_compositions_committed ++
                }
                after = System.currentTimeMillis()
                csv_compositions_commit_time += (after - before) // only counts the time to commit, avoiding the JSON generation time
            }

            csv_compositions_accum += csv_compositions_committed



            // 6. witness query
            println "Testing ${aql_files.size()} queries..."

            // 6.1. set the ehr_id in the query
            def aql_json, aql_body, query_result, aql_time, aql_times = []
            def out_log = new File("out_${f.format(new Date())}.log")

            aql_files.sort{ it.name }.eachWithIndex { aql, i ->

                aql_json = new JsonSlurper().parseText(aql.text)

                // set the ehr_id only if there it is in the query json
                if (aql_json.query_parameters && aql_json.query_parameters.ehr_id)
                {
                    aql_json.query_parameters.ehr_id = testehr.ehr_ids.pick()
                    aql_body = JsonOutput.toJson(aql_json)
                }

                // execute the same AQL many times to calculate the AVG, MIN and MAX
                repeat_aql.times { at ->

                    // 6.2. execute the query
                    print "Executing query ${(i+1).toString().padLeft(2)}.${at}) ${aql.name}".padRight(75)
                    
                    before = System.currentTimeMillis()
                    query_result = testehr.witnessQuery(aql_body)
                    if (query_result.result == 'error')
                    {
                        print "ERROR".padRight(8)
                        out_log << aql.name.padRight(75) + query_result.error + "\n"
                    }
                    else
                    {
                        print "OK".padRight(8)
                    }
                    after = System.currentTimeMillis()

                    //csv_query_execution_time = after - before
                    aql_time = after - before

                    if (csv_query_execution_time_min > aql_time || csv_query_execution_time_min == 0) csv_query_execution_time_min = aql_time
                    if (csv_query_execution_time_max < aql_time) csv_query_execution_time_max = aql_time

                    aql_times << aql_time

                    csv_query_status = query_result.result

                    println "\t${aql_time} ms"
                }

                csv_query_execution_time_avg = (aql_times.sum() / repeat_aql).round(1)

                // reset for next loop
                aql_times = []
                csv_query_execution_time_min = 0
                csv_query_execution_time_max = 0

                // line for csv report
                csv += "${csv_template_id},${csv_template_accum},${csv_ehrs_created},${csv_ehrs_accum},${csv_compositions_committed},${csv_compositions_accum},${csv_compositions_commit_time},${aql.getName()},${csv_query_execution_time_avg},${csv_query_execution_time_min},${csv_query_execution_time_max},${csv_query_status}\n"
            }



            // 7. report
            println ""
            println "EHRs Requested: ${ehr_count} / EHRs Created: ${csv_ehrs_created} / took ${ehr_time} ms, (${(ehr_time / ehr_count).round(1)} ms AVG per EHR)"
            println "Compositions Requested: ${composition_count} / Compositions Committed: ${csv_compositions_committed} / took ${csv_compositions_commit_time} ms, (${(csv_compositions_commit_time / composition_count).round(1)} ms AVG per Composition)"


            // reset the ehr ids for the next template run
            testehr.ehr_ids = []

            csv_compositions_committed = 0
            csv_compositions_commit_time = 0
        }

        def report = new File("report_${f.format(new Date())}.csv")
        report << csv
    }
    */

   /*
    static def validateArguments(Integer ehr_count, String template_location, Integer composition_count, String aql_location, Integer scale_templates)
    {
        if (ehr_count < 1)
        {
           println "ehrs should be a positive integer ${ehr_count}"
           cli.usage()
           System.exit(0)
        }
        if (composition_count < 1)
        {
           println "compositions should be a positive integer ${composition_count}"
           cli.usage()
           System.exit(0)
        }
        if (scale_templates < 1)
        {
           println "scaleTemplates should be a positive integer ${scale_templates}"
           cli.usage()
           System.exit(0)
        }

        if (!template_location)
        {
           println "template parameter wasn't provided"
           cli.usage()
           System.exit(0)
        }

        def template_file = new File(template_location)

        if (!template_file.exists())
        {
           println "template file doesn't exists at ${template_location}"
           cli.usage()
           System.exit(0)
        }

        if (!aql_location)
        {
           println "aql parameter wasn't provided"
           cli.usage()
           System.exit(0)
        }
        
        def aql_file = new File(aql_location)

        if (!aql_file.exists())
        {
           println "aql file doesn't exists at ${aql_location}"
           cli.usage()
           System.exit(0)
        }
    }
    */

    static String removeBOM(byte[] bytes)
    {
        def inputStream = new ByteArrayInputStream(bytes)
        def bomInputStream = new UnicodeBOMInputStream(inputStream)
        bomInputStream.skipBOM() // NOP if no BOM is detected
        def br = new BufferedReader(new InputStreamReader(bomInputStream))
        return br.text // http://docs.groovy-lang.org/latest/html/groovy-jdk/java/io/BufferedReader.html#getText()
    }
}
